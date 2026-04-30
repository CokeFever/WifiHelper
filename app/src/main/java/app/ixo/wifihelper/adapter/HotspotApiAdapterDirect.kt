package app.ixo.wifihelper.adapter

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

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
            val result = withTimeoutOrNull(TETHERING_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    startTetheringViaReflection(
                        onSuccess = {
                            if (continuation.isActive) {
                                continuation.resume(HotspotResult.Success)
                            }
                        },
                        onFailure = { errorCode ->
                            if (continuation.isActive) {
                                continuation.resume(
                                    HotspotResult.Failure("Tethering 啟動失敗，錯誤碼：$errorCode")
                                )
                            }
                        }
                    )
                }
            }
            result ?: HotspotResult.Failure("Hotspot 啟動逾時")
        } catch (e: Exception) {
            HotspotResult.Failure("Hotspot 啟動失敗：${e.message}")
        }
    }

    override suspend fun disableHotspot(): HotspotResult = withContext(Dispatchers.IO) {
        try {
            stopTetheringViaReflection()
            HotspotResult.Success
        } catch (e: Exception) {
            HotspotResult.Failure("Hotspot 關閉失敗：${e.message}")
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
            HotspotState.UNKNOWN
        }
    }

    override fun getControlMode(): HotspotControlMode = HotspotControlMode.DIRECT

    /**
     * 透過反射呼叫 ConnectivityManager.startTethering()。
     *
     * 方法簽名：
     * ```
     * void startTethering(int type, boolean showProvisioningUi,
     *     OnStartTetheringCallback callback, Handler handler)
     * ```
     */
    private fun startTetheringViaReflection(
        onSuccess: () -> Unit,
        onFailure: (Int) -> Unit
    ) {
        try {
            // 取得 OnStartTetheringCallback 內部類別
            val callbackClass = Class.forName(
                "android.net.ConnectivityManager\$OnStartTetheringCallback"
            )

            // 建立回呼的動態代理
            val callbackProxy = object : Any() {
                // 透過反射被呼叫
                @Suppress("unused")
                fun onTetheringStarted() {
                    onSuccess()
                }

                // 透過反射被呼叫
                @Suppress("unused")
                fun onTetheringFailed(error: Int) {
                    onFailure(error)
                }
            }

            // 使用 Proxy 建立 callback 實例
            val callbackInstance = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onTetheringStarted" -> callbackProxy.onTetheringStarted()
                    "onTetheringFailed" -> {
                        val errorCode = (args?.firstOrNull() as? Int) ?: -1
                        callbackProxy.onTetheringFailed(errorCode)
                    }
                }
                null
            }

            // 呼叫 startTethering
            val method = connectivityManager.javaClass.getDeclaredMethod(
                "startTethering",
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                callbackClass,
                Handler::class.java
            )
            method.invoke(
                connectivityManager,
                TETHERING_WIFI,
                false,
                callbackInstance,
                Handler(Looper.getMainLooper())
            )
        } catch (e: NoSuchMethodException) {
            onFailure(-1)
        } catch (e: Exception) {
            onFailure(-1)
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
