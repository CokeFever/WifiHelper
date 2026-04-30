package app.ixo.wifihelper.core

import app.ixo.wifihelper.adapter.HotspotApiAdapter
import app.ixo.wifihelper.adapter.WifiApiAdapter
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.model.ConnectionResult
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.KnownWifiNetwork
import app.ixo.wifihelper.model.NetworkMode
import app.ixo.wifihelper.model.NetworkState
import app.ixo.wifihelper.model.SecurityType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 單元測試：SmartSwitchEngine 狀態機
 *
 * 使用 Mockk 模擬依賴，透過 [SmartSwitchEngineImpl.makeDecision] 驗證狀態轉換邏輯。
 *
 * 測試涵蓋：
 * - 狀態轉換：Idle → Scanning → WifiConnecting → WifiConnected
 * - 狀態轉換：WifiConnected → HotspotRestoring → HotspotActive
 * - 停用智慧切換時的狀態重置
 *
 * **Validates: Requirements 4.3, 4.4, 4.5**
 */
class SmartSwitchEngineStateMachineTest : FunSpec({

    // ── 共用 Mock 與引擎建立 ──────────────────────────────────────────────

    fun createEngine(
        wifiApiAdapter: WifiApiAdapter = mockk(relaxed = true),
        hotspotApiAdapter: HotspotApiAdapter = mockk(relaxed = true),
        networkStateMonitor: NetworkStateMonitor = mockk(relaxed = true),
        preferenceRepository: PreferenceRepository = mockk(relaxed = true)
    ): SmartSwitchEngineImpl {
        return SmartSwitchEngineImpl(
            wifiApiAdapter = wifiApiAdapter,
            hotspotApiAdapter = hotspotApiAdapter,
            networkStateMonitor = networkStateMonitor,
            preferenceRepository = preferenceRepository
        )
    }

    // ── 狀態轉換：Idle → Scanning → WifiConnecting → WifiConnected ───────

    context("State transition: Idle -> Scanning -> WifiConnecting -> WifiConnected") {

        test("Initial state is Idle (not running, DISCONNECTED mode)") {
            // **Validates: Requirements 4.3**
            val engine = createEngine()

            val state = engine.getState().value
            state.isRunning shouldBe false
            state.currentMode shouldBe NetworkMode.DISCONNECTED
        }

        test("makeDecision returns CONNECT_WIFI when enabled, no mobile data, and good WiFi signal") {
            // **Validates: Requirements 4.3**
            // 模擬：智慧切換啟用、行動數據不可用、有良好 WiFi 訊號
            val engine = createEngine()

            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -50,
                signalThreshold = -70,
                currentMode = NetworkMode.DISCONNECTED
            )

            decision shouldBe SwitchDecision.CONNECT_WIFI
        }

        test("makeDecision returns CONNECT_WIFI from Scanning state (SWITCHING mode)") {
            // **Validates: Requirements 4.3**
            val engine = createEngine()

            // 從 SWITCHING（掃描中）狀態，偵測到良好 WiFi
            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -55,
                signalThreshold = -70,
                currentMode = NetworkMode.SWITCHING
            )

            decision shouldBe SwitchDecision.CONNECT_WIFI
        }

        test("makeDecision returns CONNECT_WIFI transitioning through the full path") {
            // **Validates: Requirements 4.3**
            val engine = createEngine()

            // Step 1: Idle → 啟用智慧切換（引擎開始掃描）
            // 模擬掃描中狀態
            val scanningDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = null, // 尚未發現 WiFi
                signalThreshold = -70,
                currentMode = NetworkMode.DISCONNECTED
            )
            // 無良好 WiFi → 維持當前狀態
            scanningDecision shouldBe SwitchDecision.MAINTAIN_CURRENT

            // Step 2: Scanning → 發現良好 WiFi → WifiConnecting
            val connectDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -60,
                signalThreshold = -70,
                currentMode = NetworkMode.DISCONNECTED
            )
            connectDecision shouldBe SwitchDecision.CONNECT_WIFI

            // Step 3: WifiConnecting → 連線成功 → WifiConnected
            // 連線成功後，狀態為 WIFI_CONNECTED，行動數據仍不可用
            val connectedDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -60,
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            // 行動數據不可用且已連線 WiFi → 繼續連線 WiFi（CONNECT_WIFI）
            connectedDecision shouldBe SwitchDecision.CONNECT_WIFI
        }
    }

    // ── 狀態轉換：WifiConnected → HotspotRestoring → HotspotActive ───────

    context("State transition: WifiConnected -> HotspotRestoring -> HotspotActive") {

        test("makeDecision returns RESTORE_HOTSPOT when mobile data recovers while on WiFi") {
            // **Validates: Requirements 4.4**
            val engine = createEngine()

            // 當前為 WiFi 連線狀態，行動數據恢復
            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -50, // 即使 WiFi 訊號良好
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )

            decision shouldBe SwitchDecision.RESTORE_HOTSPOT
        }

        test("makeDecision returns RESTORE_HOTSPOT regardless of WiFi signal strength when mobile data is available and on WiFi") {
            // **Validates: Requirements 4.4**
            val engine = createEngine()

            // 即使 WiFi 訊號非常強，行動數據可用時仍應恢復 Hotspot
            val decisionStrongWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -20, // 極強訊號
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            decisionStrongWifi shouldBe SwitchDecision.RESTORE_HOTSPOT

            // WiFi 訊號弱也一樣
            val decisionWeakWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -90, // 弱訊號
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            decisionWeakWifi shouldBe SwitchDecision.RESTORE_HOTSPOT
        }

        test("After hotspot restore succeeds, decision maintains HOTSPOT_ACTIVE state") {
            // **Validates: Requirements 4.4**
            val engine = createEngine()

            // Hotspot 恢復成功後，狀態為 HOTSPOT_ACTIVE，行動數據可用
            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -50,
                signalThreshold = -70,
                currentMode = NetworkMode.HOTSPOT_ACTIVE
            )

            // 行動數據可用且 WiFi 超過門檻，但不在 WIFI_CONNECTED 狀態
            // Rule 4 不適用（WiFi > threshold），所以不是 RESTORE_HOTSPOT
            // 但 mobileDataAvailable = true 且 bestKnownWifiRssi > threshold
            // 且 currentMode != WIFI_CONNECTED → 不觸發 Rule 3
            // 且 mobileDataAvailable = true → 不觸發 Rule 2
            // 且 bestKnownWifiRssi > signalThreshold → 不觸發 Rule 4
            // → MAINTAIN_CURRENT
            decision shouldBe SwitchDecision.MAINTAIN_CURRENT
        }

        test("Full transition path: WifiConnected -> RESTORE_HOTSPOT -> HotspotActive") {
            // **Validates: Requirements 4.4**
            val engine = createEngine()

            // Step 1: 當前為 WiFi 連線，行動數據恢復 → 決策為恢復 Hotspot
            val restoreDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -55,
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            restoreDecision shouldBe SwitchDecision.RESTORE_HOTSPOT

            // Step 2: 恢復過程中（SWITCHING 狀態），行動數據可用，無良好 WiFi
            val switchingDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = null, // WiFi 已中斷
                signalThreshold = -70,
                currentMode = NetworkMode.SWITCHING
            )
            switchingDecision shouldBe SwitchDecision.RESTORE_HOTSPOT

            // Step 3: Hotspot 恢復成功，狀態為 HOTSPOT_ACTIVE
            val activeDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = null,
                signalThreshold = -70,
                currentMode = NetworkMode.HOTSPOT_ACTIVE
            )
            activeDecision shouldBe SwitchDecision.RESTORE_HOTSPOT
        }
    }

    // ── 停用智慧切換時的狀態重置 ─────────────────────────────────────────

    context("State reset when smart switch is disabled") {

        test("makeDecision returns NO_ACTION when smart switch is disabled from any state") {
            // **Validates: Requirements 4.5**
            val engine = createEngine()

            // 從各種狀態停用智慧切換，都應回傳 NO_ACTION
            val modes = NetworkMode.entries

            modes.forEach { mode ->
                val decision = engine.makeDecision(
                    smartSwitchEnabled = false,
                    mobileDataAvailable = true,
                    bestKnownWifiRssi = -50,
                    signalThreshold = -70,
                    currentMode = mode
                )
                decision shouldBe SwitchDecision.NO_ACTION
            }
        }

        test("stop() resets engine state to not running and DISCONNECTED") {
            // **Validates: Requirements 4.5**
            val engine = createEngine()

            // 驗證初始狀態
            engine.getState().value.isRunning shouldBe false

            // 停止引擎（即使未啟動，stop() 也應將狀態設為 not running + DISCONNECTED）
            engine.stop()

            val state = engine.getState().value
            state.isRunning shouldBe false
            state.currentMode shouldBe NetworkMode.DISCONNECTED
        }

        test("makeDecision returns NO_ACTION regardless of network conditions when disabled") {
            // **Validates: Requirements 4.5**
            val engine = createEngine()

            // 即使有良好 WiFi 且行動數據不可用，停用時也不動作
            val decision1 = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -30, // 極強訊號
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            decision1 shouldBe SwitchDecision.NO_ACTION

            // 即使行動數據可用且當前為 WiFi 連線，停用時也不動作
            val decision2 = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = true,
                bestKnownWifiRssi = -50,
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            decision2 shouldBe SwitchDecision.NO_ACTION

            // 即使完全無連線，停用時也不動作
            val decision3 = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = false,
                bestKnownWifiRssi = null,
                signalThreshold = -70,
                currentMode = NetworkMode.DISCONNECTED
            )
            decision3 shouldBe SwitchDecision.NO_ACTION
        }

        test("Transition from active WiFi connection to disabled resets decision to NO_ACTION") {
            // **Validates: Requirements 4.5**
            val engine = createEngine()

            // Step 1: 智慧切換啟用，已連線 WiFi
            val activeDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -50,
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            // 行動數據不可用且已連線 WiFi → 繼續連線
            activeDecision shouldBe SwitchDecision.CONNECT_WIFI

            // Step 2: 使用者停用智慧切換
            val disabledDecision = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = false,
                bestKnownWifiRssi = -50,
                signalThreshold = -70,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            disabledDecision shouldBe SwitchDecision.NO_ACTION
        }

        test("Transition from active Hotspot to disabled resets decision to NO_ACTION") {
            // **Validates: Requirements 4.5**
            val engine = createEngine()

            // Step 1: 智慧切換啟用，Hotspot 啟用中
            val activeDecision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = null,
                signalThreshold = -70,
                currentMode = NetworkMode.HOTSPOT_ACTIVE
            )
            activeDecision shouldBe SwitchDecision.RESTORE_HOTSPOT

            // Step 2: 使用者停用智慧切換
            val disabledDecision = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = true,
                bestKnownWifiRssi = null,
                signalThreshold = -70,
                currentMode = NetworkMode.HOTSPOT_ACTIVE
            )
            disabledDecision shouldBe SwitchDecision.NO_ACTION
        }
    }
})
