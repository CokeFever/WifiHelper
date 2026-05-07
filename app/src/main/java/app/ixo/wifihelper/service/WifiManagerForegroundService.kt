package app.ixo.wifihelper.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import app.ixo.wifihelper.core.SmartSwitchEngine
import app.ixo.wifihelper.util.CrashReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WiFi 智慧管理前景服務。
 *
 * 使用 Foreground Service 維持背景自動化任務的運作，透過持續性通知
 * 向使用者顯示當前的運作狀態（WiFi 連線中、Hotspot 啟用中、掃描中、已停用）。
 *
 * 生命週期：
 * - [onCreate]：建立通知頻道
 * - [onStartCommand]：啟動為前景服務、啟動 SmartSwitchEngine、開始收集狀態更新通知
 * - [onDestroy]：停止 SmartSwitchEngine、取消協程
 *
 * 需求：5.1, 5.5
 */
@AndroidEntryPoint
class WifiManagerForegroundService : Service() {

    companion object {
        private const val TAG = "WifiManagerFGS"
    }

    @Inject
    lateinit var smartSwitchEngine: SmartSwitchEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateCollectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")

        // MUST call startForeground() first to satisfy Android's contract.
        // On Android 12+, after startForegroundService() is called, the service
        // MUST call startForeground() within 5 seconds or the app crashes with
        // ForegroundServiceDidNotStartInTimeException.
        val initialState = smartSwitchEngine.getState().value
        val notification = NotificationHelper.buildNotification(this, initialState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }

        // Now check permissions - if not granted, stop gracefully
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Required permissions not granted, stopping service")
            CrashReporter.logError("Service stopped: required permissions not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        // 啟動智慧切換引擎
        smartSwitchEngine.start()

        // 收集引擎狀態變化並更新通知內容
        startStateCollection()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy")
        stateCollectionJob?.cancel()
        smartSwitchEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 開始收集 [SmartSwitchEngine] 的狀態變化，並據此更新前景通知內容。
     */
    private fun startStateCollection() {
        stateCollectionJob?.cancel()
        stateCollectionJob = serviceScope.launch {
            smartSwitchEngine.getState().collectLatest { state ->
                val notification = NotificationHelper.buildNotification(
                    this@WifiManagerForegroundService,
                    state
                )
                NotificationHelper.updateNotification(
                    this@WifiManagerForegroundService,
                    notification
                )
            }
        }
    }

    /**
     * 檢查服務運作所需的最低必要權限。
     *
     * @return true 表示所有必要權限已授予
     */
    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
