package app.ixo.wifihelper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.service.ServiceRestartWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 開機廣播接收器，接收 [Intent.ACTION_BOOT_COMPLETED] 廣播。
 *
 * 當裝置開機完成後，根據 [PreferenceRepository.isAutoStartEnabled] 的設定
 * 決定是否透過 [ServiceRestartWorker] 啟動 Foreground Service。
 *
 * 使用 WorkManager 而非直接呼叫 startForegroundService()，
 * 因為 Android 12+ 不允許從背景 BroadcastReceiver 啟動 Foreground Service。
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

        Log.i(TAG, "Scheduling service start via WorkManager")
        ServiceRestartWorker.scheduleImmediate(context)
        ServiceRestartWorker.schedule(context)
    }
}
