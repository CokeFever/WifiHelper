package app.ixo.wifihelper.core

import app.ixo.wifihelper.model.NetworkState
import kotlinx.coroutines.flow.StateFlow

/**
 * 網路狀態監控介面：監聽行動網路與 WiFi 狀態變化。
 *
 * 透過 [StateFlow] 發布 [NetworkState] 變更，讓上層元件（如 SmartSwitchEngine）
 * 能即時取得網路狀態並做出決策。
 *
 * 需求：3.6, 3.7, 4.3, 4.4
 */
interface NetworkStateMonitor {

    /**
     * 監聽網路狀態變化。
     *
     * 回傳一個 [StateFlow]，每當行動網路或 WiFi 狀態發生變化時，
     * 會發布新的 [NetworkState] 物件。
     */
    fun observeNetworkState(): StateFlow<NetworkState>

    /**
     * 查詢行動數據（4G/5G）是否可用。
     *
     * @return `true` 表示行動數據已連線且可用
     */
    fun isMobileDataAvailable(): Boolean

    /**
     * 查詢 WiFi 是否已啟用（不代表已連線）。
     *
     * @return `true` 表示 WiFi 硬體已啟用
     */
    fun isWifiEnabled(): Boolean

    /**
     * 取得當前 WiFi 訊號強度（RSSI）。
     *
     * @return 當前 WiFi 的 RSSI 值（dBm），若未連線至 WiFi 則回傳 `null`
     */
    fun getCurrentWifiRssi(): Int?

    /**
     * 釋放資源，取消註冊網路回呼。
     *
     * 應在不再需要監控時呼叫，以避免資源洩漏。
     */
    fun cleanup()
}
