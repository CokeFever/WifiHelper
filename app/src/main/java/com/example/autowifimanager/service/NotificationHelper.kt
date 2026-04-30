package com.example.autowifimanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.autowifimanager.R
import com.example.autowifimanager.model.NetworkMode
import com.example.autowifimanager.model.SmartSwitchState

/**
 * 通知建構輔助物件，負責建立通知頻道與根據 [SmartSwitchState] 產生通知。
 *
 * 將通知邏輯抽離 Service，方便單元測試驗證通知內容是否正確反映運作狀態。
 *
 * 需求：5.1, 5.5
 */
object NotificationHelper {

    /** 通知頻道 ID */
    const val CHANNEL_ID = "wifi_manager_channel"

    /** 通知頻道名稱 */
    private const val CHANNEL_NAME = "WiFi 智慧管理"

    /** 通知頻道描述 */
    private const val CHANNEL_DESCRIPTION = "顯示 WiFi 智慧切換的運作狀態"

    /** Foreground Service 通知 ID */
    const val NOTIFICATION_ID = 1001

    /**
     * 建立通知頻道。
     *
     * 在 Android O（API 26）以上必須先建立頻道才能發送通知。
     * 由於本 App 的 minSdk 為 28，此方法一定會執行頻道建立。
     *
     * @param context 應用程式 Context
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * 根據 [SmartSwitchState] 建構持續性通知。
     *
     * 通知內容根據 [SmartSwitchState.currentMode] 顯示對應的運作狀態描述：
     * - [NetworkMode.WIFI_CONNECTED] → "WiFi 連線中：{ssid}"
     * - [NetworkMode.HOTSPOT_ACTIVE] → "WiFi 熱點啟用中"
     * - [NetworkMode.SWITCHING] → "掃描中..."
     * - [NetworkMode.DISCONNECTED] → "已停用"
     * - [NetworkMode.MOBILE_DATA] → "使用行動數據"
     *
     * @param context 應用程式 Context
     * @param state 當前智慧切換引擎狀態
     * @return 建構完成的 [Notification]
     */
    fun buildNotification(context: Context, state: SmartSwitchState): Notification {
        val contentText = getNotificationText(state)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 更新已顯示的前景服務通知。
     *
     * @param context 應用程式 Context
     * @param notification 新的通知內容
     */
    fun updateNotification(context: Context, notification: Notification) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 根據 [SmartSwitchState] 產生通知文字。
     *
     * 此方法為純函式，方便屬性測試驗證通知內容正確性（屬性 10）。
     *
     * @param state 當前智慧切換引擎狀態
     * @return 通知內容文字
     */
    fun getNotificationText(state: SmartSwitchState): String {
        return when (state.currentMode) {
            NetworkMode.WIFI_CONNECTED -> {
                val ssid = state.connectedSsid ?: "未知網路"
                "WiFi 連線中：$ssid"
            }
            NetworkMode.HOTSPOT_ACTIVE -> "WiFi 熱點啟用中"
            NetworkMode.SWITCHING -> "掃描中..."
            NetworkMode.DISCONNECTED -> "已停用"
            NetworkMode.MOBILE_DATA -> "使用行動數據"
        }
    }
}
