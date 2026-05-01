package app.ixo.wifihelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.service.ServiceRestartWorker
import app.ixo.wifihelper.service.WifiManagerForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 開機廣播接收器，接收 [Intent.ACTION_BOOT_COMPLETED] 廣播。
 *
 * 當裝置開機完成後，根據 [PreferenceRepository.isAutoStartEnabled] 的設定
 * 決定是否啟動 Foreground Service。
 *
 * 版本適配：
 * - API 28-30：可直接從 BroadcastReceiver 呼叫 startForegroundService()
 * - API 31+（Android 12+）：不允許從背景 BroadcastReceiver 啟動 Foreground Service，
 *   改用 WorkManager scheduleImmediate() 透過合法途徑啟動
 *
 * 需求：4.6
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Received unexpected action: ${intent.action}")
            return
        }

        Log.i(TAG, "Received BOOT_COMPLETED broadcast")

        val autoStartEnabled = preferenceRepository.isAutoStartEnabled()
        Log.i(TAG, "autoStartEnabled=$autoStartEnabled")

        if (!autoStartEnabled) {
            Log.i(TAG, "Auto start is disabled, not starting service")
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            // API 28-30: Can start FGS directly from BroadcastReceiver
            Log.i(TAG, "API ${Build.VERSION.SDK_INT} <= 30: Starting service directly")
            try {
                val serviceIntent = Intent(context, WifiManagerForegroundService::class.java)
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Direct service start failed, falling back to WorkManager", e)
                ServiceRestartWorker.scheduleImmediate(context)
            }
        } else {
            // API 31+: Must use WorkManager to start FGS from background
            Log.i(TAG, "API ${Build.VERSION.SDK_INT} >= 31: Scheduling via WorkManager")
            ServiceRestartWorker.scheduleImmediate(context)
        }

        // Always schedule periodic restart as a safety net
        ServiceRestartWorker.schedule(context)
    }
}
