package app.ixo.wifihelper.adapter

import android.content.Intent
import android.net.wifi.WifiManager
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hotspot API 適配器的引導控制實作（API 33+）。
 *
 * API 33+ 的非特權 App 無法透過反射呼叫 Tethering API，因此改為透過
 * Intent 跳轉至系統設定的 Tethering 頁面，引導使用者手動操作。
 *
 * - [enableHotspot] / [disableHotspot]：回傳 [HotspotResult.NeedUserAction]，
 *   包含跳轉至系統 Tethering 設定的 Intent
 * - [getHotspotState]：嘗試透過反射讀取 WifiManager.getWifiApState()，
 *   若失敗則回傳 [HotspotState.UNKNOWN]
 */
@Singleton
class HotspotApiAdapterGuided @Inject constructor(
    private val wifiManager: WifiManager
) : HotspotApiAdapter {

    companion object {
        /**
         * 系統 Tethering 設定頁面的 Intent Action。
         * 此為非公開 API，但在大多數 Android 裝置上可用。
         */
        private const val ACTION_TETHERING_SETTINGS = "android.settings.TETHERING_SETTINGS"

        /** WifiManager.WIFI_AP_STATE_ENABLED 的常數值 */
        private const val WIFI_AP_STATE_ENABLED = 13

        /** WifiManager.WIFI_AP_STATE_DISABLED 的常數值 */
        private const val WIFI_AP_STATE_DISABLED = 11
    }

    override suspend fun enableHotspot(): HotspotResult {
        return HotspotResult.NeedUserAction(createTetheringSettingsIntent())
    }

    override suspend fun disableHotspot(): HotspotResult {
        return HotspotResult.NeedUserAction(createTetheringSettingsIntent())
    }

    override suspend fun getHotspotState(): HotspotState = withContext(Dispatchers.IO) {
        try {
            // 嘗試透過反射讀取 Hotspot 狀態
            // 部分 API 33+ 裝置仍可讀取（僅控制被限制，讀取可能仍可用）
            val apState = getWifiApStateViaReflection()
            when (apState) {
                WIFI_AP_STATE_ENABLED -> HotspotState.ENABLED
                WIFI_AP_STATE_DISABLED -> HotspotState.DISABLED
                else -> HotspotState.UNKNOWN
            }
        } catch (e: Exception) {
            // 反射失敗，無法確定狀態
            HotspotState.UNKNOWN
        }
    }

    override fun getControlMode(): HotspotControlMode = HotspotControlMode.GUIDED

    /**
     * 建立跳轉至系統 Tethering 設定頁面的 Intent。
     *
     * 使用 `android.settings.TETHERING_SETTINGS` Action，若裝置不支援
     * 則 fallback 至 `android.settings.WIRELESS_SETTINGS`。
     */
    private fun createTetheringSettingsIntent(): Intent {
        return Intent(ACTION_TETHERING_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 嘗試透過反射呼叫 WifiManager.getWifiApState()。
     *
     * 在 API 33+ 上，此方法可能因安全限制而拋出例外。
     */
    private fun getWifiApStateViaReflection(): Int {
        val method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
        return method.invoke(wifiManager) as Int
    }
}
