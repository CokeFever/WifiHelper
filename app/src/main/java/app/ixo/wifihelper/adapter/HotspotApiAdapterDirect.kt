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
     * 透過反射呼叫 ConnectivityManager.startTethering() 或
     * WifiManager.setWifiApEnabled() 啟動 Hotspot。
     *
     * 策略：
     * 1. 先嘗試 ConnectivityManager.startTethering()（需要建立 callback 實例）
     * 2. 若失敗，fallback 到 WifiManager.setWifiApEnabled()（API 28 上更可靠）
     */
    private fun startTetheringViaReflection() {
        // 策略 1：嘗試 ConnectivityManager.startTethering
        try {
            val callbackClass = Class.forName(
                "android.net.ConnectivityManager\$OnStartTetheringCallback"
            )

            // OnStartTetheringCallback 是 abstract class，嘗試用 getDeclaredConstructor
            // 某些 ROM 允許實例化（方法有預設空實作）
            val callbackInstance = try {
                callbackClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            } catch (_: Exception) {
                null
            }

            if (callbackInstance != null) {
                val method = try {
                    connectivityManager.javaClass.getDeclaredMethod(
                        "startTethering",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        callbackClass,
                        Handler::class.java
                    )
                } catch (e: NoSuchMethodException) {
                    connectivityManager.javaClass.getDeclaredMethod(
                        "startTethering",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        callbackClass
                    )
                }

                if (method.parameterCount == 4) {
                    method.invoke(connectivityManager, TETHERING_WIFI, false, callbackInstance, Handler(Looper.getMainLooper()))
                } else {
                    method.invoke(connectivityManager, TETHERING_WIFI, false, callbackInstance)
                }
                return // 成功呼叫，由 enableHotspot 輪詢結果
            }
        } catch (e: Exception) {
            CrashReporter.logError("startTethering via ConnectivityManager failed, trying fallback", e)
        }

        // 策略 2：Fallback 到 WifiManager.setWifiApEnabled (deprecated but works on API 28-29)
        try {
            @Suppress("DEPRECATION")
            val configClass = Class.forName("android.net.wifi.WifiConfiguration")
            val method = wifiManager.javaClass.getDeclaredMethod(
                "setWifiApEnabled",
                configClass,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, null, true)
        } catch (e: Exception) {
            CrashReporter.logError("setWifiApEnabled fallback also failed", e)
            throw e // 讓上層 catch 處理
        }
    }

    /**
     * 透過反射呼叫 ConnectivityManager.stopTethering() 或
     * WifiManager.setWifiApEnabled(null, false) 關閉 Hotspot。
     */
    private fun stopTetheringViaReflection() {
        // 策略 1：ConnectivityManager.stopTethering
        try {
            val method = connectivityManager.javaClass.getDeclaredMethod(
                "stopTethering",
                Int::class.javaPrimitiveType
            )
            method.invoke(connectivityManager, TETHERING_WIFI)
            return
        } catch (e: Exception) {
            CrashReporter.logError("stopTethering via ConnectivityManager failed, trying fallback", e)
        }

        // 策略 2：WifiManager.setWifiApEnabled(null, false)
        try {
            @Suppress("DEPRECATION")
            val configClass = Class.forName("android.net.wifi.WifiConfiguration")
            val method = wifiManager.javaClass.getDeclaredMethod(
                "setWifiApEnabled",
                configClass,
                Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, null, false)
        } catch (e: Exception) {
            CrashReporter.logError("setWifiApEnabled(false) fallback also failed", e)
            throw e
        }
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
