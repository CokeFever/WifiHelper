package app.ixo.wifihelper.adapter

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.util.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hotspot API 適配器的直接控制實作（API 28-32）。
 *
 * 透過反射呼叫 [ConnectivityManager] 的隱藏 Tethering API：
 * - `startTethering(int, boolean, OnStartTetheringCallback, Handler)` 啟動 Hotspot
 * - `stopTethering(int)` 關閉 Hotspot
 *
 * 透過反射呼叫 [WifiManager] 的隱藏 API：
 * - `getWifiApState()` 查詢 Hotspot 狀態
 *
 * 此實作僅適用於 API 28-32。API 33+ 因 Google 進一步限制 Tethering API 存取，
 * 非特權 App 無法透過反射呼叫，需改用引導控制模式。
 */
@Singleton
class HotspotApiAdapterDirect @Inject constructor(
    private val connectivityManager: ConnectivityManager,
    private val wifiManager: WifiManager
) : HotspotApiAdapter {

    companion object {
        /** Tethering 類型：WiFi */
        private const val TETHERING_WIFI = 0

        /** WifiManager.WIFI_AP_STATE_ENABLED 的常數值 */
        private const val WIFI_AP_STATE_ENABLED = 13

        /** WifiManager.WIFI_AP_STATE_DISABLED 的常數值 */
        private const val WIFI_AP_STATE_DISABLED = 11

        /** startTethering 回呼等待超時時間（毫秒） */
        private const val TETHERING_TIMEOUT_MS = 5000L
    }

    override suspend fun enableHotspot(): HotspotResult = withContext(Dispatchers.IO) {
        try {
            // 呼叫 startTethering 反射
            startTetheringViaReflection()

            // 等待 Hotspot 啟動（輪詢狀態）
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < TETHERING_TIMEOUT_MS) {
                kotlinx.coroutines.delay(500)
                try {
                    val apState = getWifiApStateViaReflection()
                    if (apState == WIFI_AP_STATE_ENABLED) {
                        return@withContext HotspotResult.Success
                    }
                } catch (_: Exception) { }
            }
            HotspotResult.Failure("操作未成功，請重試")
        } catch (e: Exception) {
            CrashReporter.logError("Hotspot enableHotspot reflection failed", e)
            HotspotResult.Failure("操作未成功，請重試")
        }
    }

    override suspend fun disableHotspot(): HotspotResult = withContext(Dispatchers.IO) {
        try {
            stopTetheringViaReflection()
            HotspotResult.Success
        } catch (e: Exception) {
            CrashReporter.logError("Hotspot disableHotspot reflection failed", e)
            HotspotResult.Failure("操作未成功，請重試")
        }
    }

    override suspend fun getHotspotState(): HotspotState = withContext(Dispatchers.IO) {
        try {
            val apState = getWifiApStateViaReflection()
            when (apState) {
                WIFI_AP_STATE_ENABLED -> HotspotState.ENABLED
                WIFI_AP_STATE_DISABLED -> HotspotState.DISABLED
                else -> HotspotState.UNKNOWN
            }
        } catch (e: Exception) {
            CrashReporter.logError("Hotspot getHotspotState reflection failed", e)
            HotspotState.UNKNOWN
        }
    }

    override fun getControlMode(): HotspotControlMode = HotspotControlMode.DIRECT

    /**
     * 透過反射呼叫 ConnectivityManager.startTethering()。
     *
     * OnStartTetheringCallback 是 abstract class（非 interface），
     * 使用反射建立預設實例並傳入。結果透過輪詢 getWifiApState() 判斷。
     */
    private fun startTetheringViaReflection() {
        val callbackClass = Class.forName(
            "android.net.ConnectivityManager\$OnStartTetheringCallback"
        )

        // 建立 callback 實例（預設空實作）
        val callbackInstance = callbackClass.getDeclaredConstructor().newInstance()

        // 嘗試 4 參數版本（帶 Handler）
        val method = try {
            connectivityManager.javaClass.getDeclaredMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                callbackClass,
                Handler::class.java
            )
        } catch (e: NoSuchMethodException) {
            // 嘗試 3 參數版本
            connectivityManager.javaClass.getDeclaredMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                callbackClass
            )
        }

        if (method.parameterCount == 4) {
            method.invoke(
                connectivityManager,
                TETHERING_WIFI,
                false,
                callbackInstance,
                Handler(Looper.getMainLooper())
            )
        } else {
            method.invoke(
                connectivityManager,
                TETHERING_WIFI,
                false,
                callbackInstance
            )
        }
    }

    /**
     * 透過反射呼叫 ConnectivityManager.stopTethering()。
     *
     * 方法簽名：`void stopTethering(int type)`
     */
    private fun stopTetheringViaReflection() {
        val method = connectivityManager.javaClass.getDeclaredMethod(
            "stopTethering",
            Int::class.javaPrimitiveType
        )
        method.invoke(connectivityManager, TETHERING_WIFI)
    }

    /**
     * 透過反射呼叫 WifiManager.getWifiApState()。
     *
     * 方法簽名：`int getWifiApState()`
     *
     * 回傳值對應：
     * - 10: WIFI_AP_STATE_DISABLING
     * - 11: WIFI_AP_STATE_DISABLED
     * - 12: WIFI_AP_STATE_ENABLING
     * - 13: WIFI_AP_STATE_ENABLED
     * - 14: WIFI_AP_STATE_FAILED
     */
    private fun getWifiApStateViaReflection(): Int {
        val method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
        return method.invoke(wifiManager) as Int
    }
}
