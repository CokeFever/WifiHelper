package app.ixo.wifihelper.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import app.ixo.wifihelper.model.NetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkStateMonitor] 的實作類別。
 *
 * 使用 [ConnectivityManager.NetworkCallback] 監聽網路狀態變化，
 * 透過 [StateFlow] 發布 [NetworkState] 變更。同時提供查詢方法
 * 以取得行動數據可用性、WiFi 啟用狀態與當前 WiFi RSSI。
 *
 * 需求：3.6, 3.7, 4.3, 4.4
 */
@Singleton
class NetworkStateMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkStateMonitor {

    companion object {
        private const val TAG = "NetworkStateMonitor"
    }

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _networkState = MutableStateFlow(buildCurrentNetworkState())

    /**
     * 追蹤目前已連線的網路與其能力，用於在回呼中判斷狀態。
     * key = Network, value = NetworkCapabilities
     */
    private val activeNetworks = mutableMapOf<Network, NetworkCapabilities>()

    /**
     * 預設網路回呼：監聽所有網路（WiFi + 行動數據）的連線與斷線事件。
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null) {
                synchronized(activeNetworks) {
                    activeNetworks[network] = capabilities
                }
            }
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Log.d(TAG, "Network capabilities changed: $network")
            synchronized(activeNetworks) {
                activeNetworks[network] = networkCapabilities
            }
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            synchronized(activeNetworks) {
                activeNetworks.remove(network)
            }
            updateNetworkState()
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        Log.d(TAG, "NetworkStateMonitor initialized, callback registered")
    }

    override fun observeNetworkState(): StateFlow<NetworkState> = _networkState.asStateFlow()

    override fun isMobileDataAvailable(): Boolean {
        return synchronized(activeNetworks) {
            activeNetworks.values.any { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
    }

    override fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    override fun getCurrentWifiRssi(): Int? {
        // On API 31+, prefer NetworkCapabilities-based RSSI retrieval
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rssi = getWifiRssiFromCapabilities()
            if (rssi != null) return rssi
        }

        // Fallback: use WifiManager.getConnectionInfo() for older APIs or if capabilities unavailable
        @Suppress("DEPRECATION")
        val connectionInfo = wifiManager.connectionInfo ?: return null
        val rssi = connectionInfo.rssi
        // RSSI of -127 typically means not connected
        return if (rssi == -127) null else rssi
    }

    override fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "NetworkStateMonitor cleanup, callback unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Callback was already unregistered", e)
        }
        synchronized(activeNetworks) {
            activeNetworks.clear()
        }
    }

    /**
     * 從 [NetworkCapabilities] 取得 WiFi RSSI（API 29+）。
     */
    private fun getWifiRssiFromCapabilities(): Int? {
        return synchronized(activeNetworks) {
            activeNetworks.entries
                .firstOrNull { (_, caps) ->
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
                ?.let { (_, caps) ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        caps.signalStrength.takeIf { it != Int.MIN_VALUE }
                    } else {
                        null
                    }
                }
        }
    }

    /**
     * 更新 [_networkState]，根據當前所有已連線網路的能力重新建構 [NetworkState]。
     */
    private fun updateNetworkState() {
        val newState = buildCurrentNetworkState()
        _networkState.value = newState
        Log.d(
            TAG,
            "NetworkState updated: mobile=${newState.isMobileDataConnected}, " +
                "wifi=${newState.isWifiConnected}, ssid=${newState.wifiSsid}, " +
                "rssi=${newState.wifiRssi}, type=${newState.networkType}"
        )
    }

    /**
     * 根據當前系統狀態建構 [NetworkState] 物件。
     */
    private fun buildCurrentNetworkState(): NetworkState {
        val isMobileConnected = isMobileDataAvailable()
        val isWifiConnected = isWifiCurrentlyConnected()
        val wifiSsid = if (isWifiConnected) getCurrentWifiSsid() else null
        val wifiRssi = if (isWifiConnected) getCurrentWifiRssi() else null
        val networkType = if (isMobileConnected) getMobileNetworkType() else null

        return NetworkState(
            isMobileDataConnected = isMobileConnected,
            isWifiConnected = isWifiConnected,
            wifiSsid = wifiSsid,
            wifiRssi = wifiRssi,
            networkType = networkType
        )
    }

    /**
     * 判斷 WiFi 是否已連線（不只是啟用）。
     */
    private fun isWifiCurrentlyConnected(): Boolean {
        return synchronized(activeNetworks) {
            activeNetworks.values.any { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
    }

    /**
     * 取得當前連線的 WiFi SSID。
     *
     * API 29+ 透過 [NetworkCapabilities.getTransportInfo] 取得 [WifiInfo]，
     * 較舊版本使用 [WifiManager.getConnectionInfo]。
     */
    private fun getCurrentWifiSsid(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            synchronized(activeNetworks) {
                for ((network, caps) in activeNetworks) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wifiInfo = caps.transportInfo as? WifiInfo
                        val ssid = wifiInfo?.ssid
                        if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
                            // SSID is returned with quotes, strip them
                            return ssid.removeSurrounding("\"")
                        }
                    }
                }
            }
        }

        // Fallback for older APIs
        @Suppress("DEPRECATION")
        val connectionInfo = wifiManager.connectionInfo
        val ssid = connectionInfo?.ssid
        if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
            return ssid.removeSurrounding("\"")
        }
        return null
    }

    /**
     * 取得行動網路類型字串（如 "4G", "5G"）。
     */
    private fun getMobileNetworkType(): String? {
        // Use NetworkCapabilities to detect 5G (NR) on API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            synchronized(activeNetworks) {
                for ((_, caps) in activeNetworks) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        // Check for 5G NR via TelephonyManager on API 30+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            return getMobileNetworkTypeFromTelephony()
                        }
                    }
                }
            }
        }

        return getMobileNetworkTypeFromTelephony()
    }

    /**
     * 透過 [TelephonyManager] 取得行動網路類型。
     */
    private fun getMobileNetworkTypeFromTelephony(): String? {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return null

        return try {
            @Suppress("DEPRECATION")
            when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
                else -> "Unknown"
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read network type", e)
            null
        }
    }
}
