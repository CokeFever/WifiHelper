package app.ixo.wifihelper.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ixo.wifihelper.data.PreferenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker，負責在 Foreground Service 被系統終止後重新啟動服務。
 *
 * 使用 [PeriodicWorkRequestBuilder] 以 15 分鐘間隔（WorkManager 最小週期）排程，
 * 確保服務在被殺後能在合理時間內恢復運作。
 *
 * 啟動前會檢查 [PreferenceRepository] 中的 smartSwitchEnabled 或 autoStartEnabled，
 * 僅在至少一項啟用時才重新啟動服務。
 *
 * 需求：5.2
 */
@HiltWorker
class ServiceRestartWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferenceRepository: PreferenceRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ServiceRestartWorker"

        /** WorkManager 唯一工作名稱 */
        const val WORK_NAME = "service_restart_work"

        /** 排程週期（分鐘），WorkManager 最小週期為 15 分鐘 */
        const val REPEAT_INTERVAL_MINUTES = 15L

        /**
         * 排程服務重啟的週期性工作。
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ServiceRestartWorker>(
                REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.i(TAG, "Scheduled periodic service restart work (interval: ${REPEAT_INTERVAL_MINUTES}min)")
        }

        /**
         * 立即排程一次性工作以啟動服務（用於開機自動啟動）。
         */
        fun scheduleImmediate(context: Context) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ServiceRestartWorker>()
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Scheduled immediate service start work")
        }

        /**
         * 取消服務重啟的週期性工作。
         *
         * @param context 應用程式 Context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i(TAG, "Cancelled periodic service restart work")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork: Checking if service should be restarted")

        val smartSwitchEnabled = preferenceRepository.isSmartSwitchEnabled()
        val autoStartEnabled = preferenceRepository.isAutoStartEnabled()

        if (!smartSwitchEnabled && !autoStartEnabled) {
            Log.i(TAG, "doWork: Both smartSwitch and autoStart are disabled, skipping restart")
            return Result.success()
        }

        Log.i(TAG, "doWork: Restarting WifiManagerForegroundService " +
            "(smartSwitch=$smartSwitchEnabled, autoStart=$autoStartEnabled)")

        try {
            // 使用 setForeground() 提升為前景工作，避免 Android 12+ 的背景啟動限制
            val notification = app.ixo.wifihelper.service.NotificationHelper.buildNotification(
                applicationContext,
                app.ixo.wifihelper.model.SmartSwitchState(
                    isRunning = false,
                    currentMode = app.ixo.wifihelper.model.NetworkMode.DISCONNECTED,
                    lastScanTime = 0L,
                    connectedSsid = null,
                    hotspotState = app.ixo.wifihelper.model.HotspotState.UNKNOWN,
                    mobileDataAvailable = false,
                    knownNetworksCount = 0,
                    failedAttempts = emptyMap()
                )
            )
            app.ixo.wifihelper.service.NotificationHelper.createNotificationChannel(applicationContext)

            val notificationId = app.ixo.wifihelper.service.NotificationHelper.NOTIFICATION_ID + 1
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: Must specify foregroundServiceType (required on API 34+)
                ForegroundInfo(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                // API 28: No service type needed
                ForegroundInfo(notificationId, notification)
            }
            setForeground(foregroundInfo)

            val serviceIntent = Intent(applicationContext, WifiManagerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(serviceIntent)
            } else {
                applicationContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork: Failed to restart service", e)
            return Result.retry()
        }

        return Result.success()
    }
}
