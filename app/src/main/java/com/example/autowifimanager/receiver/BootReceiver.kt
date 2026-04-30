package com.example.autowifimanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.autowifimanager.data.PreferenceRepository
import com.example.autowifimanager.service.WifiManagerForegroundService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 開機廣播接收器，接收 [Intent.ACTION_BOOT_COMPLETED] 廣播。
 *
 * 當裝置開機完成後，根據 [PreferenceRepository.isAutoStartEnabled] 的設定
 * 決定是否自動啟動 [WifiManagerForegroundService]。
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

        Log.i(TAG, "Starting WifiManagerForegroundService on boot")
        try {
            val serviceIntent = Intent(context, WifiManagerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service on boot", e)
        }
    }
}
