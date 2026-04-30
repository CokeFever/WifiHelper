package com.example.autowifimanager.adapter

import android.annotation.SuppressLint
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.example.autowifimanager.model.ConnectionFailureReason
import com.example.autowifimanager.model.ConnectionResult
import com.example.autowifimanager.model.KnownWifiNetwork
import com.example.autowifimanager.model.ScanResultInfo
import com.example.autowifimanager.model.SecurityType
import com.example.autowifimanager.model.WifiConnectionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi API 適配器的舊版實作（API 28-29）。
 *
 * 使用 [WifiManager.getConfiguredNetworks] 取得系統已記憶的 WiFi 清單，
 * 並透過 [WifiManager.enableNetwork] 進行連線。
 *
 * 此實作僅適用於 API 28-29，因為 API 30+ 的 getConfiguredNetworks()
 * 因隱私限制會回傳空清單。
 */
@Singleton
class WifiApiAdapterLegacy @Inject constructor(
    private val wifiManager: WifiManager
) : WifiApiAdapter {

    companion object {
        /** 連線後等待確認的最大時間（毫秒） */
        private const val CONNECTION_TIMEOUT_MS = 5000L

        /** 連線確認輪詢間隔（毫秒） */
        private const val CONNECTION_POLL_INTERVAL_MS = 500L
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override suspend fun getKnownNetworks(): List<KnownWifiNetwork> =
        withContext(Dispatchers.IO) {
            val configuredNetworks = try {
                wifiManager.configuredNetworks.orEmpty()
            } catch (e: SecurityException) {
                emptyList()
            }

            val scanResults = try {
                wifiManager.scanResults.orEmpty()
            } catch (e: SecurityException) {
                emptyList()
            }

            val currentConnection = getCurrentConnectionInternal()
            val currentSsid = currentConnection?.ssid?.removeSurrounding("\"")

            configuredNetworks.mapNotNull { config ->
                val ssid = config.SSID?.removeSurrounding("\"") ?: return@mapNotNull null

                // 交叉比對掃描結果以取得 RSSI 與頻率
                val matchingScan = scanResults
                    .filter { it.SSID == ssid }
                    .maxByOrNull { it.level }

                KnownWifiNetwork(
                    ssid = ssid,
                    bssid = matchingScan?.BSSID ?: config.BSSID,
                    rssi = matchingScan?.level ?: -100,
                    frequency = matchingScan?.frequency ?: 0,
                    securityType = parseSecurityType(matchingScan?.capabilities),
                    isCurrentlyConnected = ssid == currentSsid,
                    lastSeen = matchingScan?.timestamp ?: 0L
                )
            }
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override suspend fun connectToNetwork(network: KnownWifiNetwork): ConnectionResult =
        withContext(Dispatchers.IO) {
            try {
                // 從已設定網路中找到對應的 networkId
                val configuredNetworks = wifiManager.configuredNetworks.orEmpty()
                val targetConfig = configuredNetworks.find {
                    it.SSID?.removeSurrounding("\"") == network.ssid
                } ?: return@withContext ConnectionResult.Failure(
                    ConnectionFailureReason.NETWORK_NOT_FOUND
                )

                // 先中斷當前連線
                wifiManager.disconnect()

                // 啟用目標網路
                val enabled = wifiManager.enableNetwork(targetConfig.networkId, true)
                if (!enabled) {
                    return@withContext ConnectionResult.Failure(
                        ConnectionFailureReason.UNKNOWN
                    )
                }

                wifiManager.reconnect()

                // 等待連線完成
                val connected = waitForConnection(network.ssid)
                if (connected) {
                    ConnectionResult.Success
                } else {
                    ConnectionResult.Failure(ConnectionFailureReason.TIMEOUT)
                }
            } catch (e: SecurityException) {
                ConnectionResult.Failure(ConnectionFailureReason.UNKNOWN)
            }
        }

    @Suppress("DEPRECATION")
    override suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            wifiManager.disconnect()
        } catch (e: SecurityException) {
            false
        }
    }

    override fun getCurrentConnection(): WifiConnectionInfo? {
        return getCurrentConnectionInternal()
    }

    @SuppressLint("MissingPermission")
    override suspend fun startScan(): List<ScanResultInfo> = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()

            // 短暫等待掃描結果更新
            delay(CONNECTION_POLL_INTERVAL_MS)

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
     * 取得當前 WiFi 連線資訊（內部使用）。
     */
    @Suppress("DEPRECATION")
    private fun getCurrentConnectionInternal(): WifiConnectionInfo? {
        val info: WifiInfo = try {
            wifiManager.connectionInfo ?: return null
        } catch (e: SecurityException) {
            return null
        }

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
     * 等待 WiFi 連線至指定 SSID，超時回傳 false。
     */
    @Suppress("DEPRECATION")
    private suspend fun waitForConnection(targetSsid: String): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < CONNECTION_TIMEOUT_MS) {
            val currentInfo = wifiManager.connectionInfo
            val currentSsid = currentInfo?.ssid?.removeSurrounding("\"")
            if (currentSsid == targetSsid && currentInfo.networkId != -1) {
                return true
            }
            delay(CONNECTION_POLL_INTERVAL_MS)
        }
        return false
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
