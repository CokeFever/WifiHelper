package app.ixo.wifihelper.adapter

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
            CrashReporter.logInfo("enableHotspot: starting, current state=${try { getWifiApStateViaReflection() } catch (_: Exception) { "unknown" }}")

            // AOSP Android 9 要求：開啟 Hotspot 前必須先關閉 WiFi（共用射頻）
            // API 29+ 的 setWifiEnabled 已無效，但 startTethering 會自動處理
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P && wifiManager.isWifiEnabled) {
                CrashReporter.logInfo("enableHotspot: API 28 - WiFi is enabled, disabling first...")
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = false
                kotlinx.coroutines.delay(1000) // 等待 WiFi 完全關閉
            }

            // 呼叫 startTethering 反射
            startTetheringViaReflection()
            CrashReporter.logInfo("enableHotspot: reflection call completed, polling for state change...")

            // 等待 Hotspot 啟動（輪詢狀態）
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < TETHERING_TIMEOUT_MS) {
                kotlinx.coroutines.delay(500)
                try {
                    val apState = getWifiApStateViaReflection()
                    CrashReporter.logInfo("enableHotspot: polling apState=$apState")
                    if (apState == WIFI_AP_STATE_ENABLED) {
                        CrashReporter.logInfo("enableHotspot: SUCCESS - Hotspot enabled")
                        return@withContext HotspotResult.Success
                    }
                } catch (e: Exception) {
                    CrashReporter.logError("enableHotspot: polling getWifiApState failed", e)
                }
            }
            CrashReporter.logError("enableHotspot: TIMEOUT - state never became ENABLED")
            HotspotResult.Failure("操作未成功，請重試")
        } catch (e: Exception) {
            CrashReporter.logError("enableHotspot: exception, falling back to guided mode", e)
            // 反射方法都失敗了（某些客製化 ROM 移除了 tethering API）
            // Fallback 到引導模式：跳轉系統設定讓使用者手動開啟
            HotspotResult.NeedUserAction(createTetheringSettingsIntent())
        }
    }

    /**
     * 建立跳轉至系統 Tethering 設定頁面的 Intent（用於反射失敗時的 fallback）。
     */
    private fun createTetheringSettingsIntent(): android.content.Intent {
        val actions = listOf(
            "android.settings.TETHERING_SETTINGS",
            "com.android.settings.TetherSettings",
            Settings.ACTION_WIRELESS_SETTINGS
        )
        for (action in actions) {
            val intent = android.content.Intent(action).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (action == "android.settings.TETHERING_SETTINGS") {
                return intent
            }
        }
        return android.content.Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override suspend fun disableHotspot(): HotspotResult = withContext(Dispatchers.IO) {
        try {
            stopTetheringViaReflection()

            // API 28：enableHotspot 時關閉了 WiFi，需要重新開啟
            // API 29+：setWifiEnabled 無效，但系統會在 Hotspot 關閉後自動恢復 WiFi
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P && !wifiManager.isWifiEnabled) {
                CrashReporter.logInfo("disableHotspot: API 28 - re-enabling WiFi after hotspot stop")
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
            }

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

            val callbackInstance = try {
                callbackClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            } catch (e: Exception) {
                CrashReporter.logInfo("startTethering: callback instantiation failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }

            if (callbackInstance != null) {
                CrashReporter.logInfo("startTethering: using ConnectivityManager.startTethering with callback")
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
                CrashReporter.logInfo("startTethering: ConnectivityManager.startTethering invoked successfully")
                return
            }
        } catch (e: Exception) {
            CrashReporter.logError("startTethering: ConnectivityManager strategy failed", e)
        }

        // 策略 2：Fallback 到 WifiManager.setWifiApEnabled
        try {
            CrashReporter.logInfo("startTethering: using fallback WifiManager.setWifiApEnabled")
            @Suppress("DEPRECATION")
            val configClass = Class.forName("android.net.wifi.WifiConfiguration")

            // 嘗試取得現有的 AP 設定（某些 ROM 需要有效的 config 才能啟動）
            val apConfig = try {
                val getConfigMethod = wifiManager.javaClass.getDeclaredMethod("getWifiApConfiguration")
                getConfigMethod.invoke(wifiManager)
            } catch (_: Exception) {
                CrashReporter.logInfo("startTethering: getWifiApConfiguration failed, using null config")
                null
            }

            val method = wifiManager.javaClass.getDeclaredMethod(
                "setWifiApEnabled",
                configClass,
                Boolean::class.javaPrimitiveType
            )
            val result = method.invoke(wifiManager, apConfig, true)
            CrashReporter.logInfo("startTethering: setWifiApEnabled(config=${apConfig != null}, true) returned: $result")
        } catch (e: Exception) {
            CrashReporter.logError("startTethering: setWifiApEnabled fallback also failed", e)
            throw e
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
