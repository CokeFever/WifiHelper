package app.ixo.wifihelper.ui

import app.cash.turbine.test
import app.ixo.wifihelper.adapter.HotspotApiAdapter
import app.ixo.wifihelper.core.SmartSwitchEngine
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.NetworkMode
import app.ixo.wifihelper.model.SmartSwitchState
import app.ixo.wifihelper.ui.dashboard.DashboardUiState
import app.ixo.wifihelper.ui.dashboard.DashboardViewModel
import app.ixo.wifihelper.ui.settings.SettingsViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * 單元測試：ViewModel 狀態管理
 *
 * 使用 Turbine 測試 StateFlow 發射，驗證：
 * - 智慧切換開關切換後的狀態變化
 * - Hotspot 操作在不同控制模式下的行為
 * - SettingsViewModel 的設定變更
 *
 * **Validates: Requirements 2.1, 2.5, 4.1**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelStateTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // ── 共用工廠方法 ──────────────────────────────────────────────────────

    fun defaultEngineState() = SmartSwitchState(
        isRunning = false,
        currentMode = NetworkMode.DISCONNECTED,
        lastScanTime = 0L,
        connectedSsid = null,
        hotspotState = HotspotState.UNKNOWN,
        mobileDataAvailable = false,
        knownNetworksCount = 0,
        failedAttempts = emptyMap()
    )

    fun createDashboardViewModel(
        engineStateFlow: MutableStateFlow<SmartSwitchState> = MutableStateFlow(defaultEngineState()),
        smartSwitchEnabled: Boolean = false,
        hotspotApiAdapter: HotspotApiAdapter = mockk(relaxed = true),
        preferenceRepository: PreferenceRepository = mockk(relaxed = true)
    ): DashboardViewModel {
        val engine = mockk<SmartSwitchEngine>(relaxed = true) {
            every { getState() } returns engineStateFlow
        }
        every { preferenceRepository.isSmartSwitchEnabled() } returns smartSwitchEnabled

        return DashboardViewModel(
            smartSwitchEngine = engine,
            hotspotApiAdapter = hotspotApiAdapter,
            preferenceRepository = preferenceRepository
        )
    }

    // ── DashboardViewModel：智慧切換開關切換 ─────────────────────────────

    context("DashboardViewModel - Smart Switch Toggle") {

        test("Initial UI state reflects engine state") {
            // **Validates: Requirements 4.1**
            val engineState = defaultEngineState()
            val engineStateFlow = MutableStateFlow(engineState)

            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                smartSwitchEnabled = false
            )

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.smartSwitchEnabled shouldBe false
            viewModel.uiState.value.isRunning shouldBe false
            viewModel.uiState.value.networkMode shouldBe NetworkMode.DISCONNECTED
        }

        test("toggleSmartSwitch enables smart switch and starts engine") {
            // **Validates: Requirements 4.1**
            val engineStateFlow = MutableStateFlow(defaultEngineState())
            val engine = mockk<SmartSwitchEngine>(relaxed = true) {
                every { getState() } returns engineStateFlow
            }
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isSmartSwitchEnabled() } returns false
            }

            val viewModel = DashboardViewModel(
                smartSwitchEngine = engine,
                hotspotApiAdapter = mockk(relaxed = true),
                preferenceRepository = preferenceRepository
            )

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.toggleSmartSwitch()

            verify { preferenceRepository.setSmartSwitchEnabled(true) }
            verify { engine.start() }
            viewModel.uiState.value.smartSwitchEnabled shouldBe true
        }

        test("toggleSmartSwitch disables smart switch and stops engine") {
            // **Validates: Requirements 4.1**
            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(isRunning = true)
            )
            val engine = mockk<SmartSwitchEngine>(relaxed = true) {
                every { getState() } returns engineStateFlow
            }
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isSmartSwitchEnabled() } returns true
            }

            val viewModel = DashboardViewModel(
                smartSwitchEngine = engine,
                hotspotApiAdapter = mockk(relaxed = true),
                preferenceRepository = preferenceRepository
            )

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.toggleSmartSwitch()

            verify { preferenceRepository.setSmartSwitchEnabled(false) }
            verify { engine.stop() }
            viewModel.uiState.value.smartSwitchEnabled shouldBe false
        }

        test("UI state updates when engine state changes") {
            // **Validates: Requirements 2.5, 4.1**
            val engineStateFlow = MutableStateFlow(defaultEngineState())
            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                smartSwitchEnabled = true
            )

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val initial = awaitItem()
                initial.networkMode shouldBe NetworkMode.DISCONNECTED

                // 模擬引擎狀態變更：WiFi 連線成功
                engineStateFlow.value = defaultEngineState().copy(
                    isRunning = true,
                    currentMode = NetworkMode.WIFI_CONNECTED,
                    connectedSsid = "TestNetwork",
                    knownNetworksCount = 3
                )

                testDispatcher.scheduler.advanceUntilIdle()

                val updated = awaitItem()
                updated.networkMode shouldBe NetworkMode.WIFI_CONNECTED
                updated.connectedSsid shouldBe "TestNetwork"
                updated.knownNetworksCount shouldBe 3

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ── DashboardViewModel：Hotspot 操作 ────────────────────────────────

    context("DashboardViewModel - Hotspot Toggle") {

        test("toggleHotspot enables hotspot in direct mode and updates UI state") {
            // **Validates: Requirements 2.1, 2.5**
            val hotspotApiAdapter = mockk<HotspotApiAdapter>(relaxed = true) {
                every { getControlMode() } returns HotspotControlMode.DIRECT
                coEvery { enableHotspot() } returns HotspotResult.Success
            }

            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(hotspotState = HotspotState.DISABLED)
            )

            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                hotspotApiAdapter = hotspotApiAdapter
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.toggleHotspot()

            result shouldBe HotspotResult.Success
            viewModel.uiState.value.hotspotState shouldBe HotspotState.ENABLED
        }

        test("toggleHotspot disables hotspot when currently enabled") {
            // **Validates: Requirements 2.1, 2.5**
            val hotspotApiAdapter = mockk<HotspotApiAdapter>(relaxed = true) {
                every { getControlMode() } returns HotspotControlMode.DIRECT
                coEvery { disableHotspot() } returns HotspotResult.Success
            }

            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(hotspotState = HotspotState.ENABLED)
            )

            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                hotspotApiAdapter = hotspotApiAdapter
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.toggleHotspot()

            result shouldBe HotspotResult.Success
            viewModel.uiState.value.hotspotState shouldBe HotspotState.DISABLED
        }

        test("toggleHotspot returns NeedUserAction in guided mode") {
            // **Validates: Requirements 2.1**
            val mockIntent = mockk<android.content.Intent>(relaxed = true)
            val hotspotApiAdapter = mockk<HotspotApiAdapter>(relaxed = true) {
                every { getControlMode() } returns HotspotControlMode.GUIDED
                coEvery { enableHotspot() } returns HotspotResult.NeedUserAction(mockIntent)
            }

            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(hotspotState = HotspotState.DISABLED)
            )

            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                hotspotApiAdapter = hotspotApiAdapter
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.toggleHotspot()

            // 引導模式回傳 NeedUserAction，UI 狀態不應改變（等使用者從系統設定返回後偵測）
            result shouldBe HotspotResult.NeedUserAction(mockIntent)
            // hotspotState 不應更新為 ENABLED（因為操作尚未完成）
            viewModel.uiState.value.hotspotState shouldBe HotspotState.DISABLED
        }

        test("toggleHotspot returns Failure and does not update UI state on failure") {
            // **Validates: Requirements 2.1, 2.5**
            val hotspotApiAdapter = mockk<HotspotApiAdapter>(relaxed = true) {
                every { getControlMode() } returns HotspotControlMode.DIRECT
                coEvery { enableHotspot() } returns HotspotResult.Failure("Reflection failed")
            }

            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(hotspotState = HotspotState.DISABLED)
            )

            val viewModel = createDashboardViewModel(
                engineStateFlow = engineStateFlow,
                hotspotApiAdapter = hotspotApiAdapter
            )

            testDispatcher.scheduler.advanceUntilIdle()

            val result = viewModel.toggleHotspot()

            result shouldBe HotspotResult.Failure("Reflection failed")
            // 失敗時 UI 狀態不應改變
            viewModel.uiState.value.hotspotState shouldBe HotspotState.DISABLED
        }

        test("Hotspot state in UI updates when engine reports state change") {
            // **Validates: Requirements 2.5**
            val engineStateFlow = MutableStateFlow(
                defaultEngineState().copy(hotspotState = HotspotState.DISABLED)
            )

            val viewModel = createDashboardViewModel(engineStateFlow = engineStateFlow)

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.test {
                val initial = awaitItem()
                initial.hotspotState shouldBe HotspotState.DISABLED

                // 引擎報告 Hotspot 狀態變更
                engineStateFlow.value = engineStateFlow.value.copy(
                    hotspotState = HotspotState.ENABLED
                )

                testDispatcher.scheduler.advanceUntilIdle()

                val updated = awaitItem()
                updated.hotspotState shouldBe HotspotState.ENABLED

                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ── SettingsViewModel ───────────────────────────────────────────────

    context("SettingsViewModel - Settings Management") {

        test("Initial state reflects current preferences") {
            // **Validates: Requirements 4.2, 4.7**
            val autoStartFlow = MutableStateFlow(true)
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isAutoStartEnabled() } returns true
                every { getSignalThreshold() } returns -65
                every { observeAutoStartEnabled() } returns autoStartFlow
            }

            val viewModel = SettingsViewModel(preferenceRepository)

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.autoStartEnabled shouldBe true
            viewModel.uiState.value.signalThreshold shouldBe -65
        }

        test("toggleAutoStart toggles the auto start setting") {
            // **Validates: Requirements 4.2**
            val autoStartFlow = MutableStateFlow(false)
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isAutoStartEnabled() } returns false
                every { getSignalThreshold() } returns -70
                every { observeAutoStartEnabled() } returns autoStartFlow
            }

            val viewModel = SettingsViewModel(preferenceRepository)

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.autoStartEnabled shouldBe false

            viewModel.toggleAutoStart()

            verify { preferenceRepository.setAutoStartEnabled(true) }
            viewModel.uiState.value.autoStartEnabled shouldBe true
        }

        test("setSignalThreshold updates the threshold value") {
            // **Validates: Requirements 4.7**
            val autoStartFlow = MutableStateFlow(false)
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isAutoStartEnabled() } returns false
                every { getSignalThreshold() } returns -70
                every { observeAutoStartEnabled() } returns autoStartFlow
            }

            val viewModel = SettingsViewModel(preferenceRepository)

            testDispatcher.scheduler.advanceUntilIdle()

            // 設定新門檻
            every { preferenceRepository.getSignalThreshold() } returns -55
            viewModel.setSignalThreshold(-55)

            verify { preferenceRepository.setSignalThreshold(-55) }
            viewModel.uiState.value.signalThreshold shouldBe -55
        }

        test("setSignalThreshold reads back clamped value from repository") {
            // **Validates: Requirements 4.7**
            val autoStartFlow = MutableStateFlow(false)
            val preferenceRepository = mockk<PreferenceRepository>(relaxed = true) {
                every { isAutoStartEnabled() } returns false
                every { getSignalThreshold() } returns -70
                every { observeAutoStartEnabled() } returns autoStartFlow
            }

            val viewModel = SettingsViewModel(preferenceRepository)

            testDispatcher.scheduler.advanceUntilIdle()

            // 設定超出範圍的值，repository 會 clamp 到 -30
            every { preferenceRepository.getSignalThreshold() } returns -30
            viewModel.setSignalThreshold(-10)

            verify { preferenceRepository.setSignalThreshold(-10) }
            // UI 應反映 repository 回傳的 clamped 值
            viewModel.uiState.value.signalThreshold shouldBe -30
        }
    }
})
