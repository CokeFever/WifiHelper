package app.ixo.wifihelper.core

import app.ixo.wifihelper.adapter.HotspotApiAdapter
import app.ixo.wifihelper.adapter.WifiApiAdapter
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.model.NetworkMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.orNull
import io.kotest.property.forAll
import io.mockk.mockk

// Feature: auto-wifi-manager, Property 2: 智慧切換決策邏輯

/**
 * 屬性測試：智慧切換決策邏輯
 *
 * 對任意 (smartSwitchEnabled, mobileDataAvailable, bestKnownWifiRssi, signalThreshold, currentMode)
 * 組合，驗證 [SmartSwitchEngineImpl.makeDecision] 的決策結果符合設計規格中的規則。
 *
 * 決策規則：
 * 1. smartSwitchEnabled = false → [SwitchDecision.NO_ACTION]
 * 2. smartSwitchEnabled = true 且 mobileDataAvailable = false 且 bestKnownWifiRssi > signalThreshold → [SwitchDecision.CONNECT_WIFI]
 * 3. smartSwitchEnabled = true 且 mobileDataAvailable = true 且 currentMode = WIFI_CONNECTED → [SwitchDecision.RESTORE_HOTSPOT]
 * 4. smartSwitchEnabled = true 且 mobileDataAvailable = true 且 (bestKnownWifiRssi == null 或 bestKnownWifiRssi ≤ signalThreshold) → [SwitchDecision.RESTORE_HOTSPOT]
 * 5. 其他情況 → [SwitchDecision.MAINTAIN_CURRENT]
 *
 * **Validates: Requirements 3.6, 3.7, 4.3, 4.4, 4.5**
 */
class SmartSwitchDecisionPropertyTest : FunSpec({

    // 使用 mockk 建立 SmartSwitchEngineImpl 所需的依賴（makeDecision 為純函式，不使用這些依賴）
    val engine = SmartSwitchEngineImpl(
        wifiApiAdapter = mockk<WifiApiAdapter>(relaxed = true),
        hotspotApiAdapter = mockk<HotspotApiAdapter>(relaxed = true),
        networkStateMonitor = mockk<NetworkStateMonitor>(relaxed = true),
        preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    )

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    val arbSmartSwitchEnabled = Arb.boolean()
    val arbMobileDataAvailable = Arb.boolean()
    val arbBestKnownWifiRssi = Arb.int(-100..0).orNull()
    val arbSignalThreshold = Arb.int(-100..-30)
    val arbCurrentMode = Arb.element(NetworkMode.entries)

    // ── 屬性 2a：smartSwitchEnabled = false → NO_ACTION ────────────────────

    test("Property 2a: When smartSwitchEnabled is false, decision is always NO_ACTION") {
        // **Validates: Requirements 4.5**
        forAll(
            arbMobileDataAvailable,
            arbBestKnownWifiRssi,
            arbSignalThreshold,
            arbCurrentMode
        ) { mobileDataAvailable, bestKnownWifiRssi, signalThreshold, currentMode ->
            val decision = engine.makeDecision(
                smartSwitchEnabled = false,
                mobileDataAvailable = mobileDataAvailable,
                bestKnownWifiRssi = bestKnownWifiRssi,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )
            decision == SwitchDecision.NO_ACTION
        }
    }

    // ── 屬性 2b：行動數據不可用且有良好 WiFi → CONNECT_WIFI ────────────────

    test("Property 2b: When enabled, no mobile data, and good WiFi signal, decision is CONNECT_WIFI") {
        // **Validates: Requirements 3.6, 4.3**
        forAll(
            arbSignalThreshold,
            arbCurrentMode
        ) { signalThreshold, currentMode ->
            // 產生一個嚴格大於 signalThreshold 的 RSSI 值
            val bestRssi = signalThreshold + 1

            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = bestRssi,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            // Rule 2a: 若已連上 WiFi 且訊號良好 → MAINTAIN_CURRENT（不需重複連線）
            // Rule 2b: 尚未連線 WiFi 且有良好訊號 → CONNECT_WIFI
            if (currentMode == NetworkMode.WIFI_CONNECTED) {
                decision == SwitchDecision.MAINTAIN_CURRENT
            } else {
                decision == SwitchDecision.CONNECT_WIFI
            }
        }
    }

    // ── 屬性 2c：行動數據可用且當前為 WiFi 連線 → RESTORE_HOTSPOT ──────────

    test("Property 2c: When enabled, mobile data available, and currently on WiFi, decision is RESTORE_HOTSPOT") {
        // **Validates: Requirements 3.7, 4.4**
        forAll(
            arbBestKnownWifiRssi,
            arbSignalThreshold
        ) { bestKnownWifiRssi, signalThreshold ->
            val decision = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = bestKnownWifiRssi,
                signalThreshold = signalThreshold,
                currentMode = NetworkMode.WIFI_CONNECTED
            )
            decision == SwitchDecision.RESTORE_HOTSPOT
        }
    }

    // ── 屬性 2d：行動數據可用且 WiFi 訊號不足（或無 WiFi）→ RESTORE_HOTSPOT ─

    test("Property 2d: When enabled, mobile data available, and WiFi signal weak or absent, decision is RESTORE_HOTSPOT") {
        // **Validates: Requirements 3.7, 4.4, 4.5**
        forAll(
            arbSignalThreshold,
            Arb.element(NetworkMode.entries.filter { it != NetworkMode.WIFI_CONNECTED })
        ) { signalThreshold, currentMode ->
            // 測試 bestKnownWifiRssi == null（無可用 WiFi）
            val decisionNoWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = null,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            // 測試 bestKnownWifiRssi <= signalThreshold（WiFi 訊號不足）
            val weakRssi = signalThreshold // 等於門檻，應視為不足
            val decisionWeakWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = true,
                bestKnownWifiRssi = weakRssi,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            decisionNoWifi == SwitchDecision.RESTORE_HOTSPOT &&
                decisionWeakWifi == SwitchDecision.RESTORE_HOTSPOT
        }
    }

    // ── 屬性 2e：行動數據不可用且無良好 WiFi → MAINTAIN_CURRENT ────────────

    test("Property 2e: When enabled, no mobile data, and no good WiFi, decision is MAINTAIN_CURRENT") {
        // **Validates: Requirements 3.6, 4.3**
        forAll(
            arbSignalThreshold,
            arbCurrentMode
        ) { signalThreshold, currentMode ->
            // 測試 bestKnownWifiRssi == null（無可用 WiFi）
            val decisionNoWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = null,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            // 測試 bestKnownWifiRssi <= signalThreshold（WiFi 訊號不足）
            val weakRssi = signalThreshold
            val decisionWeakWifi = engine.makeDecision(
                smartSwitchEnabled = true,
                mobileDataAvailable = false,
                bestKnownWifiRssi = weakRssi,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            decisionNoWifi == SwitchDecision.MAINTAIN_CURRENT &&
                decisionWeakWifi == SwitchDecision.MAINTAIN_CURRENT
        }
    }

    // ── 屬性 2f：完整決策規則綜合驗證 ──────────────────────────────────────

    test("Property 2f: For all input combinations, decision matches the expected rule") {
        // **Validates: Requirements 3.6, 3.7, 4.3, 4.4, 4.5**
        forAll(
            arbSmartSwitchEnabled,
            arbMobileDataAvailable,
            arbBestKnownWifiRssi,
            arbSignalThreshold,
            arbCurrentMode
        ) { smartSwitchEnabled, mobileDataAvailable, bestKnownWifiRssi, signalThreshold, currentMode ->
            val decision = engine.makeDecision(
                smartSwitchEnabled = smartSwitchEnabled,
                mobileDataAvailable = mobileDataAvailable,
                bestKnownWifiRssi = bestKnownWifiRssi,
                signalThreshold = signalThreshold,
                currentMode = currentMode
            )

            val expected = when {
                // Rule 1: 智慧切換停用 → 不動作
                !smartSwitchEnabled -> SwitchDecision.NO_ACTION

                // Rule 3: 行動數據可用且當前為 WiFi 連線 → 恢復 Hotspot
                mobileDataAvailable && currentMode == NetworkMode.WIFI_CONNECTED ->
                    SwitchDecision.RESTORE_HOTSPOT

                // Rule 2a: 已連上 WiFi 且訊號良好 → 維持當前狀態
                currentMode == NetworkMode.WIFI_CONNECTED && bestKnownWifiRssi != null && bestKnownWifiRssi > signalThreshold ->
                    SwitchDecision.MAINTAIN_CURRENT

                // Rule 2b: 行動數據不可用且有良好 WiFi 且尚未連線 → 連線 WiFi
                !mobileDataAvailable && bestKnownWifiRssi != null && bestKnownWifiRssi > signalThreshold ->
                    SwitchDecision.CONNECT_WIFI

                // Rule 4: 行動數據可用且 WiFi 訊號不足 → 恢復 Hotspot
                mobileDataAvailable && (bestKnownWifiRssi == null || bestKnownWifiRssi <= signalThreshold) ->
                    SwitchDecision.RESTORE_HOTSPOT

                // 其他情況：維持當前狀態
                else -> SwitchDecision.MAINTAIN_CURRENT
            }

            decision == expected
        }
    }
})
