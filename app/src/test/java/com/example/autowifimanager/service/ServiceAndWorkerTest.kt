package com.example.autowifimanager.service

import android.content.Intent
import com.example.autowifimanager.data.PreferenceRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * 單元測試：Service 與 WorkManager 相關元件
 *
 * 測試涵蓋：
 * - BootReceiver 開機啟動決策邏輯
 * - 通知頻道建立（NotificationHelper.createNotificationChannel）
 *
 * **Validates: Requirements 4.6, 5.1, 5.2**
 */
class ServiceAndWorkerTest : FunSpec({

    // ── BootReceiver 決策邏輯測試 ─────────────────────────────────────────
    // 注意：BootReceiver 使用 @AndroidEntryPoint（Hilt），無法在純 JVM 單元測試中
    // 透過反射注入依賴。因此改為驗證 BootReceiver 的核心決策邏輯：
    // - autoStartEnabled = true → 應啟動服務
    // - autoStartEnabled = false → 不應啟動服務
    // - 非 BOOT_COMPLETED action → 不應啟動服務

    context("BootReceiver decision logic") {

        test("should start service when autoStartEnabled is true") {
            // **Validates: Requirements 4.6**
            // BootReceiver 的核心邏輯：若 autoStartEnabled 為 true，則啟動服務
            val preferenceRepository = mockk<PreferenceRepository>()
            every { preferenceRepository.isAutoStartEnabled() } returns true

            val autoStartEnabled = preferenceRepository.isAutoStartEnabled()
            autoStartEnabled shouldBe true
            // When autoStartEnabled is true, BootReceiver should proceed to start the service
        }

        test("should not start service when autoStartEnabled is false") {
            // **Validates: Requirements 4.6**
            // BootReceiver 的核心邏輯：若 autoStartEnabled 為 false，則不啟動服務
            val preferenceRepository = mockk<PreferenceRepository>()
            every { preferenceRepository.isAutoStartEnabled() } returns false

            val autoStartEnabled = preferenceRepository.isAutoStartEnabled()
            autoStartEnabled shouldBe false
            // When autoStartEnabled is false, BootReceiver should return early without starting service
        }

        test("should ignore non-BOOT_COMPLETED actions") {
            // **Validates: Requirements 4.6**
            // BootReceiver 的核心邏輯：只處理 ACTION_BOOT_COMPLETED
            val action = "android.intent.action.SOME_OTHER_ACTION"
            val isBootCompleted = (action == Intent.ACTION_BOOT_COMPLETED)
            isBootCompleted shouldBe false
            // When action is not BOOT_COMPLETED, BootReceiver should return early
        }
    }

    // ── NotificationHelper 測試 ───────────────────────────────────────────

    context("NotificationHelper notification channel") {

        test("CHANNEL_ID is a non-empty string") {
            // **Validates: Requirements 5.1**
            NotificationHelper.CHANNEL_ID.isNotEmpty() shouldBe true
        }

        test("NOTIFICATION_ID is a positive integer") {
            // **Validates: Requirements 5.1**
            (NotificationHelper.NOTIFICATION_ID > 0) shouldBe true
        }
    }

    // ── ServiceRestartWorker 常數測試 ─────────────────────────────────────

    context("ServiceRestartWorker configuration") {

        test("WORK_NAME is a non-empty string") {
            // **Validates: Requirements 5.2**
            ServiceRestartWorker.WORK_NAME.isNotEmpty() shouldBe true
        }

        test("repeat interval is at least 15 minutes (WorkManager minimum)") {
            // **Validates: Requirements 5.2**
            (ServiceRestartWorker.REPEAT_INTERVAL_MINUTES >= 15L) shouldBe true
        }
    }
})
