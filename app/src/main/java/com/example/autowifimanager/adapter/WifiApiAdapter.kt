package com.example.autowifimanager.adapter

import com.example.autowifimanager.model.ConnectionResult
import com.example.autowifimanager.model.KnownWifiNetwork
import com.example.autowifimanager.model.ScanResultInfo
import com.example.autowifimanager.model.WifiConnectionInfo

/**
 * WiFi API 適配器介面：封裝不同 Android 版本的 WiFi 連線邏輯。
 *
 * - API 28-29：使用 [android.net.wifi.WifiManager.getConfiguredNetworks] + [android.net.wifi.WifiManager.enableNetwork]
 * - API 30+：使用 [android.net.wifi.WifiManager.getScanResults] + [android.net.wifi.WifiNetworkSuggestion]
 *
 * 上層邏輯透過此介面操作 WiFi，不需直接依賴 Android API 版本。
 */
interface WifiApiAdapter {

    /**
     * 取得系統已記憶的 WiFi 網路清單。
     *
     * 依版本使用不同策略：
     * - API 28-29：透過 getConfiguredNetworks() 取得已儲存網路，交叉比對掃描結果取得 RSSI
     * - API 30+：透過 getScanResults() 取得周圍網路，比對已知網路
     */
    suspend fun getKnownNetworks(): List<KnownWifiNetwork>

    /**
     * 連線至指定的 WiFi 網路。
     *
     * - API 28-29：使用 enableNetwork(networkId, true)
     * - API 30+：使用 WifiNetworkSuggestion API 或 ConnectivityManager.requestNetwork()
     */
    suspend fun connectToNetwork(network: KnownWifiNetwork): ConnectionResult

    /**
     * 中斷當前 WiFi 連線。
     *
     * @return true 表示成功中斷，false 表示操作失敗
     */
    suspend fun disconnect(): Boolean

    /**
     * 取得當前 WiFi 連線資訊。
     *
     * @return 當前連線資訊，若未連線則回傳 null
     */
    fun getCurrentConnection(): WifiConnectionInfo?

    /**
     * 啟動 WiFi 掃描並回傳掃描結果。
     *
     * 觸發 [android.net.wifi.WifiManager.startScan] 並取得 getScanResults()。
     */
    suspend fun startScan(): List<ScanResultInfo>
}
