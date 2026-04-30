package com.example.autowifimanager.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.autowifimanager.core.SmartSwitchEngine
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

        // 使用引擎的初始狀態建構通知，立即啟動為前景服務
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
}
