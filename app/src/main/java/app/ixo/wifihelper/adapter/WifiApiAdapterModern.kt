package app.ixo.wifihelper.adapter

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import app.ixo.wifihelper.model.ConnectionFailureReason
import app.ixo.wifihelper.model.ConnectionResult
import app.ixo.wifihelper.model.KnownWifiNetwork
import app.ixo.wifihelper.model.ScanResultInfo
import app.ixo.wifihelper.model.SecurityType
import app.ixo.wifihelper.model.WifiConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * WiFi API 適配器的現代版實作（API 30+）。
 *
 * 使用 [WifiManager.getScanResults] 取得周圍 WiFi 網路並比對已知網路，
 * 透過 [ConnectivityManager.requestNetwork] 搭配 [WifiNetworkSpecifier] 進行連線。
 *
 * 此實作適用於 API 30+，因為 getConfiguredNetworks() 在 API 30+ 因隱私限制
 * 會回傳空清單。
 */
@Singleton
class WifiApiAdapterModern @Inject constructor(
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager
) : WifiApiAdapter {

    companion object {
        /** 連線請求超時時間（毫秒） */
        private const val CONNECTION_TIMEOUT_MS = 10_000L

        /** 掃描後等待結果的時間（毫秒） */
        private const val SCAN_WAIT_MS = 500L
    }

    /** 當前透過 requestNetwork 建立的網路回呼，用於 disconnect 時取消 */
    @Volatile
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    @SuppressLint("MissingPermission")
    override suspend fun getKnownNetworks(): List<KnownWifiNetwork> =
        withContext(Dispatchers.IO) {
            val scanResults = try {
                wifiManager.scanResults.orEmpty()
            } catch (e: SecurityException) {
                emptyList()
            }

            val currentConnection = getCurrentConnection()
            val currentSsid = currentConnection?.ssid

            // API 30+ cannot get saved networks list directly.
            // getConfiguredNetworks() returns empty due to privacy restrictions.
            // Filter: exclude open networks (unsaved) and deduplicate by SSID
            // so that multiple access points with the same SSID count as one network.
            scanResults.mapNotNull { result ->
                val ssid = result.SSID
                if (ssid.isNullOrBlank()) return@mapNotNull null

                KnownWifiNetwork(
                    ssid = ssid,
                    bssid = result.BSSID ?: "",
                    rssi = result.level,
                    frequency = result.frequency,
                    securityType = parseSecurityType(result.capabilities),
                    isCurrentlyConnected = ssid == currentSsid,
                    lastSeen = result.timestamp
                )
            }
            .filter { it.securityType != SecurityType.OPEN }  // Exclude open networks
            .groupBy { it.ssid }  // Deduplicate by SSID
            .map { (_, networks) -> networks.maxByOrNull { it.rssi }!! }  // Keep strongest signal per SSID
        }

    override suspend fun connectToNetwork(network: KnownWifiNetwork): ConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                // 先取消之前的網路請求
                cancelActiveNetworkRequest()

                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(network.ssid)

                // 若有 BSSID 則指定，提高連線精確度
                if (!network.bssid.isNullOrBlank()) {
                    specifierBuilder.setBssid(
                        android.net.MacAddress.fromString(network.bssid)
                    )
                }

                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifierBuilder.build())
                    .build()

                requestNetwork(networkRequest)
            } catch (e: SecurityException) {
                ConnectionResult.Failure(ConnectionFailureReason.UNKNOWN)
            } catch (e: IllegalArgumentException) {
                // 無效的 BSSID 格式等
                ConnectionResult.Failure(ConnectionFailureReason.UNKNOWN)
            }
        }

    override suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            cancelActiveNetworkRequest()
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun getCurrentConnection(): WifiConnectionInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+：透過 NetworkCapabilities 取得連線資訊
                getCurrentConnectionViaNetworkCapabilities()
            } else {
                // API 30：仍可使用 getConnectionInfo()（已棄用但可用）
                getCurrentConnectionLegacy()
            }
        } catch (e: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan(): List<ScanResultInfo> = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()

            delay(SCAN_WAIT_MS)

            wifiManager.scanResults.orEmpty().mapNotNull { result ->
                val ssid = result.SSID
                if (ssid.isNullOrBlank()) return@mapNotNull null

                ScanResultInfo(
                    ssid = ssid,
                    bssid = result.BSSID ?: "",
                    rssi = result.level,
                    frequency = result.frequency,
                    capabilities = result.capabilities ?: ""
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * 透過 ConnectivityManager.requestNetwork 發起 WiFi 連線請求。
     */
    private suspend fun requestNetwork(
        networkRequest: NetworkRequest
    ): ConnectionResult {
        return withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        // 將此網路綁定為預設網路
                        connectivityManager.bindProcessToNetwork(network)
                        if (continuation.isActive) {
                            continuation.resume(ConnectionResult.Success)
                        }
                    }

                    override fun onUnavailable() {
                        if (continuation.isActive) {
                            continuation.resume(
                                ConnectionResult.Failure(
                                    ConnectionFailureReason.NETWORK_NOT_FOUND
                                )
                            )
                        }
                    }
                }

                activeNetworkCallback = callback

                try {
                    connectivityManager.requestNetwork(networkRequest, callback)
                } catch (e: SecurityException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            ConnectionResult.Failure(ConnectionFailureReason.UNKNOWN)
                        )
                    }
                }

                continuation.invokeOnCancellation {
                    cancelActiveNetworkRequest()
                }
            }
        } ?: ConnectionResult.Failure(ConnectionFailureReason.TIMEOUT)
    }

    /**
     * 取消當前的網路請求回呼。
     */
    private fun cancelActiveNetworkRequest() {
        activeNetworkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: IllegalArgumentException) {
                // 回呼可能已被取消註冊
            }
            activeNetworkCallback = null
        }
        // 解除程序的網路綁定
        connectivityManager.bindProcessToNetwork(null)
    }

    /**
     * API 31+：透過 NetworkCapabilities 取得當前 WiFi 連線資訊。
     */
    @SuppressLint("MissingPermission")
    private fun getCurrentConnectionViaNetworkCapabilities(): WifiConnectionInfo? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return null

        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? android.net.wifi.WifiInfo
        } else {
            null
        } ?: return null

        val ssid = wifiInfo.ssid?.removeSurrounding("\"")
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") return null

        return WifiConnectionInfo(
            ssid = ssid,
            bssid = wifiInfo.bssid ?: "",
            rssi = wifiInfo.rssi,
            linkSpeed = wifiInfo.linkSpeed,
            frequency = wifiInfo.frequency,
            networkId = wifiInfo.networkId
        )
    }

    /**
     * API 30：使用已棄用的 getConnectionInfo() 取得連線資訊。
     */
    @Suppress("DEPRECATION")
    private fun getCurrentConnectionLegacy(): WifiConnectionInfo? {
        val info = wifiManager.connectionInfo ?: return null

        val ssid = info.ssid?.removeSurrounding("\"")
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") return null

        return WifiConnectionInfo(
            ssid = ssid,
            bssid = info.bssid ?: "",
            rssi = info.rssi,
            linkSpeed = info.linkSpeed,
            frequency = info.frequency,
            networkId = info.networkId
        )
    }

    /**
     * 從掃描結果的 capabilities 字串解析安全類型。
     */
    private fun parseSecurityType(capabilities: String?): SecurityType {
        if (capabilities == null) return SecurityType.UNKNOWN
        return when {
            capabilities.contains("WPA3-SAE", ignoreCase = true) -> SecurityType.WPA3_SAE
            capabilities.contains("WPA2-PSK", ignoreCase = true) -> SecurityType.WPA2_PSK
            capabilities.contains("WPA-PSK", ignoreCase = true) -> SecurityType.WPA_PSK
            capabilities.contains("WEP", ignoreCase = true) -> SecurityType.WEP
            capabilities.contains("ESS") && !capabilities.contains("WPA") &&
                !capabilities.contains("WEP") -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }
    }
}
