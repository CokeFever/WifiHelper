package app.ixo.wifihelper.adapter

import android.content.Intent
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * 單元測試：HotspotApiAdapter 介面契約
 *
 * 由於 [HotspotApiAdapterDirect] 與 [HotspotApiAdapterGuided] 高度依賴 Android 系統服務
 * （ConnectivityManager、WifiManager），本測試透過 Mockk 模擬 [HotspotApiAdapter] 介面，
 * 驗證直接控制模式與引導控制模式的介面契約行為。
 *
 * **Validates: Requirements 2.2, 2.3, 2.4, 2.6**
 */
class HotspotApiAdapterTest : FunSpec({

    // ── 直接控制模式（API 28-32）──────────────────────────────────────────

    context("Direct mode (API 28-32)") {

        test("enableHotspot returns Success") {
            val adapter = mockk<HotspotApiAdapter>()
            coEvery { adapter.enableHotspot() } returns HotspotResult.Success

            val result = adapter.enableHotspot()

            result shouldBe HotspotResult.Success
            coVerify(exactly = 1) { adapter.enableHotspot() }
        }

        test("disableHotspot returns Success") {
            val adapter = mockk<HotspotApiAdapter>()
            coEvery { adapter.disableHotspot() } returns HotspotResult.Success

            val result = adapter.disableHotspot()

            result shouldBe HotspotResult.Success
            coVerify(exactly = 1) { adapter.disableHotspot() }
        }

        test("enableHotspot returns Failure with error message") {
            val adapter = mockk<HotspotApiAdapter>()
            val errorMessage = "Tethering 啟動失敗，錯誤碼：-1"
            coEvery { adapter.enableHotspot() } returns HotspotResult.Failure(errorMessage)

            val result = adapter.enableHotspot()

            result.shouldBeInstanceOf<HotspotResult.Failure>()
            result.reason shouldBe errorMessage
            coVerify(exactly = 1) { adapter.enableHotspot() }
        }

        test("getControlMode returns DIRECT") {
            val adapter = mockk<HotspotApiAdapter>()
            every { adapter.getControlMode() } returns HotspotControlMode.DIRECT

            val mode = adapter.getControlMode()

            mode shouldBe HotspotControlMode.DIRECT
            verify(exactly = 1) { adapter.getControlMode() }
        }

        test("getHotspotState returns ENABLED and DISABLED correctly") {
            val adapter = mockk<HotspotApiAdapter>()

            // 測試 ENABLED 狀態
            coEvery { adapter.getHotspotState() } returns HotspotState.ENABLED
            adapter.getHotspotState() shouldBe HotspotState.ENABLED

            // 測試 DISABLED 狀態
            coEvery { adapter.getHotspotState() } returns HotspotState.DISABLED
            adapter.getHotspotState() shouldBe HotspotState.DISABLED

            coVerify(exactly = 2) { adapter.getHotspotState() }
        }
    }

    // ── 引導控制模式（API 33+）────────────────────────────────────────────

    context("Guided mode (API 33+)") {

        test("enableHotspot returns NeedUserAction with Intent") {
            val adapter = mockk<HotspotApiAdapter>()
            val mockIntent = mockk<Intent>(relaxed = true)
            every { mockIntent.action } returns "android.settings.TETHERING_SETTINGS"
            every { mockIntent.flags } returns Intent.FLAG_ACTIVITY_NEW_TASK
            coEvery { adapter.enableHotspot() } returns HotspotResult.NeedUserAction(mockIntent)

            val result = adapter.enableHotspot()

            result.shouldBeInstanceOf<HotspotResult.NeedUserAction>()
            result.intent.action shouldBe "android.settings.TETHERING_SETTINGS"
            result.intent.flags shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
            coVerify(exactly = 1) { adapter.enableHotspot() }
        }

        test("disableHotspot returns NeedUserAction with Intent") {
            val adapter = mockk<HotspotApiAdapter>()
            val mockIntent = mockk<Intent>(relaxed = true)
            every { mockIntent.action } returns "android.settings.TETHERING_SETTINGS"
            every { mockIntent.flags } returns Intent.FLAG_ACTIVITY_NEW_TASK
            coEvery { adapter.disableHotspot() } returns HotspotResult.NeedUserAction(mockIntent)

            val result = adapter.disableHotspot()

            result.shouldBeInstanceOf<HotspotResult.NeedUserAction>()
            result.intent.action shouldBe "android.settings.TETHERING_SETTINGS"
            result.intent.flags shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
            coVerify(exactly = 1) { adapter.disableHotspot() }
        }

        test("getControlMode returns GUIDED") {
            val adapter = mockk<HotspotApiAdapter>()
            every { adapter.getControlMode() } returns HotspotControlMode.GUIDED

            val mode = adapter.getControlMode()

            mode shouldBe HotspotControlMode.GUIDED
            verify(exactly = 1) { adapter.getControlMode() }
        }

        test("getHotspotState returns UNKNOWN when state cannot be determined") {
            val adapter = mockk<HotspotApiAdapter>()
            coEvery { adapter.getHotspotState() } returns HotspotState.UNKNOWN

            val state = adapter.getHotspotState()

            state shouldBe HotspotState.UNKNOWN
            coVerify(exactly = 1) { adapter.getHotspotState() }
        }
    }

    // ── 失敗情境的錯誤處理 ────────────────────────────────────────────────

    context("Failure scenarios and error handling") {

        test("enableHotspot returns Failure on timeout") {
            val adapter = mockk<HotspotApiAdapter>()
            coEvery { adapter.enableHotspot() } returns HotspotResult.Failure("Hotspot 啟動逾時")

            val result = adapter.enableHotspot()

            result.shouldBeInstanceOf<HotspotResult.Failure>()
            result.reason shouldBe "Hotspot 啟動逾時"
        }

        test("disableHotspot returns Failure with error message") {
            val adapter = mockk<HotspotApiAdapter>()
            val errorMessage = "Hotspot 關閉失敗：SecurityException"
            coEvery { adapter.disableHotspot() } returns HotspotResult.Failure(errorMessage)

            val result = adapter.disableHotspot()

            result.shouldBeInstanceOf<HotspotResult.Failure>()
            result.reason shouldBe errorMessage
            coVerify(exactly = 1) { adapter.disableHotspot() }
        }

        test("enableHotspot returns Failure with reflection error") {
            val adapter = mockk<HotspotApiAdapter>()
            val errorMessage = "Hotspot 啟動失敗：NoSuchMethodException"
            coEvery { adapter.enableHotspot() } returns HotspotResult.Failure(errorMessage)

            val result = adapter.enableHotspot()

            result.shouldBeInstanceOf<HotspotResult.Failure>()
            result.reason shouldBe errorMessage
        }
    }
})
