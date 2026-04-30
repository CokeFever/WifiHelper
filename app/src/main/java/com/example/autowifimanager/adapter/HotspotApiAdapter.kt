package com.example.autowifimanager.adapter

import com.example.autowifimanager.model.HotspotControlMode
import com.example.autowifimanager.model.HotspotResult
import com.example.autowifimanager.model.HotspotState

/**
 * Hotspot API 適配器介面：封裝不同 Android 版本的 Hotspot 控制邏輯。
 *
 * - API 28-32（直接控制模式）：透過反射呼叫 [android.net.ConnectivityManager] 的隱藏
 *   Tethering API 直接啟動/關閉 WiFi Hotspot。
 * - API 33+（引導控制模式）：因 Google 進一步限制 Tethering API 存取，改為透過
 *   Intent 跳轉至系統設定頁面，引導使用者手動操作。
 *
 * 上層邏輯透過此介面操作 Hotspot，不需直接依賴 Android API 版本。
 */
interface HotspotApiAdapter {

    /**
     * 啟動 WiFi Hotspot。
     *
     * - 直接控制模式：透過反射呼叫 startTethering()，回傳 [HotspotResult.Success] 或 [HotspotResult.Failure]
     * - 引導控制模式：回傳 [HotspotResult.NeedUserAction]，包含跳轉系統設定的 Intent
     */
    suspend fun enableHotspot(): HotspotResult

    /**
     * 關閉 WiFi Hotspot。
     *
     * - 直接控制模式：透過反射呼叫 stopTethering()，回傳 [HotspotResult.Success] 或 [HotspotResult.Failure]
     * - 引導控制模式：回傳 [HotspotResult.NeedUserAction]，包含跳轉系統設定的 Intent
     */
    suspend fun disableHotspot(): HotspotResult

    /**
     * 查詢當前 Hotspot 狀態。
     *
     * - 直接控制模式：透過反射呼叫 getWifiApState() 取得精確狀態
     * - 引導控制模式：嘗試偵測狀態，若無法確定則回傳 [HotspotState.UNKNOWN]
     */
    suspend fun getHotspotState(): HotspotState

    /**
     * 取得此適配器的 Hotspot 控制模式。
     *
     * @return [HotspotControlMode.DIRECT] 或 [HotspotControlMode.GUIDED]
     */
    fun getControlMode(): HotspotControlMode
}
