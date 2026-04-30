package app.ixo.wifihelper.model

/**
 * 系統已記憶的 WiFi 網路資訊。
 */
data class KnownWifiNetwork(
    val ssid: String,
    val bssid: String?,
    val rssi: Int,
    val frequency: Int,
    val securityType: SecurityType,
    val isCurrentlyConnected: Boolean,
    val lastSeen: Long
)

/**
 * WiFi 掃描結果資訊。
 */
data class ScanResultInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String
)

/**
 * 當前 WiFi 連線資訊。
 */
data class WifiConnectionInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeed: Int,
    val frequency: Int,
    val networkId: Int
)

/**
 * 智慧切換引擎狀態。
 */
data class SmartSwitchState(
    val isRunning: Boolean,
    val currentMode: NetworkMode,
    val lastScanTime: Long,
    val connectedSsid: String?,
    val hotspotState: HotspotState,
    val mobileDataAvailable: Boolean,
    val knownNetworksCount: Int,
    val failedAttempts: Map<String, Int>
)

/**
 * 網路狀態資訊。
 */
data class NetworkState(
    val isMobileDataConnected: Boolean,
    val isWifiConnected: Boolean,
    val wifiSsid: String?,
    val wifiRssi: Int?,
    val networkType: String?
)

/**
 * 使用者偏好設定。
 */
data class UserPreferences(
    val smartSwitchEnabled: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val signalThreshold: Int = DEFAULT_SIGNAL_THRESHOLD
) {
    companion object {
        const val DEFAULT_SIGNAL_THRESHOLD = -70 // dBm
        const val KEY_SMART_SWITCH = "smart_switch_enabled"
        const val KEY_AUTO_START = "auto_start_enabled"
        const val KEY_SIGNAL_THRESHOLD = "signal_threshold"
    }
}
