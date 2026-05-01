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
import app.ixo.wifihelper.BuildConfig
import app.ixo.wifihelper.model.NetworkState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkStateMonitor] 的實作類別。
 *
 * 使用 [ConnectivityManager.NetworkCallback] 監聽網路狀態變化，
 * 透過 [StateFlow] 發布 [NetworkState] 變更。
 *
 * 使用 [ConcurrentHashMap] 追蹤已連線網路，確保執行緒安全（CWE-362 修復）。
 * Release build 中不記錄敏感網路資訊至 Logcat（CWE-532 修復）。
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

    /** 使用 ConcurrentHashMap 確保多執行緒存取安全 */
    private val activeNetworks = ConcurrentHashMap<Network, NetworkCapabilities>()

    private val _networkState = MutableStateFlow(buildCurrentNetworkState())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null) {
                activeNetworks[network] = capabilities
            }
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            activeNetworks[network] = networkCapabilities
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            activeNetworks.remove(network)
            updateNetworkState()
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun observeNetworkState(): StateFlow<NetworkState> = _networkState.asStateFlow()

    override fun isMobileDataAvailable(): Boolean {
        return activeNetworks.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    override fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    override fun getCurrentWifiRssi(): Int? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val rssi = getWifiRssiFromCapabilities()
                if (rssi != null) return rssi
            }
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo ?: return null
            val rssi = connectionInfo.rssi
            if (rssi == -127) null else rssi
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read WiFi RSSI", e)
            null
        }
    }

    override fun cleanup() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Callback was already unregistered", e)
        }
        activeNetworks.clear()
    }

    override fun refreshState() {
        updateNetworkState()
    }

    private fun getWifiRssiFromCapabilities(): Int? {
        return activeNetworks.entries
            .firstOrNull { (_, caps) -> caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?.let { (_, caps) ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    caps.signalStrength.takeIf { it != Int.MIN_VALUE }
                } else null
            }
    }

    private fun updateNetworkState() {
        val newState = buildCurrentNetworkState()
        _networkState.value = newState
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "NetworkState updated: mobile=${newState.isMobileDataConnected}, " +
                "wifi=${newState.isWifiConnected}, ssid=${newState.wifiSsid}, " +
                "rssi=${newState.wifiRssi}, type=${newState.networkType}")
        }
    }

    private fun buildCurrentNetworkState(): NetworkState {
        val isMobileConnected = isMobileDataAvailable()
        val isWifiConnected = isWifiCurrentlyConnected()
        return NetworkState(
            isMobileDataConnected = isMobileConnected,
            isWifiConnected = isWifiConnected,
            wifiSsid = if (isWifiConnected) getCurrentWifiSsid() else null,
            wifiRssi = if (isWifiConnected) getCurrentWifiRssi() else null,
            networkType = if (isMobileConnected) getMobileNetworkType() else null
        )
    }

    private fun isWifiCurrentlyConnected(): Boolean {
        return activeNetworks.values.any { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun getCurrentWifiSsid(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for ((_, caps) in activeNetworks) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        val wifiInfo = caps.transportInfo as? WifiInfo
                        val ssid = wifiInfo?.ssid
                        if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
                            return ssid.removeSurrounding("\"")
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager.connectionInfo
            val ssid = connectionInfo?.ssid
            if (ssid != null && ssid != WifiManager.UNKNOWN_SSID) {
                return ssid.removeSurrounding("\"")
            }
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read WiFi SSID", e)
            null
        }
    }

    private fun getMobileNetworkType(): String? {
        return getMobileNetworkTypeFromTelephony()
    }

    private fun getMobileNetworkTypeFromTelephony(): String? {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
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
