package app.ixo.wifihelper.util

import app.ixo.wifihelper.model.KnownWifiNetwork
import app.ixo.wifihelper.model.SecurityType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.kotest.property.forAll

// Feature: auto-wifi-manager, Property 3: 最佳網路選擇

/**
 * 屬性測試：最佳網路選擇
 *
 * 對任意已知 WiFi 網路清單與任意門檻值，驗證 [NetworkSelector.selectBestNetwork] 的選擇結果
 * 為超過門檻的最強訊號網路；若無超過門檻者則不連線。
 *
 * 使用 Kotest Arb 產生器產生隨機的 [KnownWifiNetwork] 清單與訊號門檻值，
 * 驗證以下屬性：
 * 1. 若結果非 null，其 RSSI 嚴格大於門檻
 * 2. 若結果非 null，無其他非排除網路超過門檻且 RSSI 更高
 * 3. 若結果為 null，無任何非排除網路的 RSSI 嚴格超過門檻
 *
 * **Validates: Requirements 3.5**
 */
class NetworkSelectorPropertyTest : FunSpec({

    // ── Arb 產生器 ────────────────────────────────────────────────────────

    /** 產生隨機訊號門檻值，範圍 [-100, -30] dBm */
    val arbThreshold: Arb<Int> = Arb.int(-100..-30)

    /**
     * 產生隨機 KnownWifiNetwork 清單（0-20 個元素）。
     *
     * 每個網路具有唯一的 SSID（"Network_0", "Network_1", ...）、
     * 隨機 RSSI 值（[-100, 0] dBm）、隨機頻率與安全類型。
     */
    fun arbNetworkList(): Arb<List<KnownWifiNetwork>> = Arb.list(
        Arb.int(-100..0), // RSSI values
        range = 0..20
    ).map { rssiValues ->
        rssiValues.mapIndexed { index, rssi ->
            KnownWifiNetwork(
                ssid = "Network_$index",
                bssid = "00:11:22:33:44:${index.toString(16).padStart(2, '0')}",
                rssi = rssi,
                frequency = if (index % 2 == 0) 2437 else 5180,
                securityType = SecurityType.entries[index % SecurityType.entries.size],
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )
        }
    }

    // ── 屬性 3a：結果非 null 時，RSSI 嚴格大於門檻 ──────────────────────────

    test("Property 3a: if result is non-null, its RSSI is strictly greater than threshold") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold
            )
            if (result != null) {
                result.rssi > threshold
            } else {
                true // null result trivially satisfies this property
            }
        }
    }

    // ── 屬性 3b：結果非 null 時，無其他非排除網路超過門檻且 RSSI 更高 ──────────

    test("Property 3b: if result is non-null, no other non-excluded network above threshold has higher RSSI") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val excludedSsids = emptySet<String>()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )
            if (result != null) {
                val eligibleNetworks = networks.filter {
                    it.ssid !in excludedSsids && it.rssi > threshold
                }
                // No eligible network should have a higher RSSI than the selected one
                eligibleNetworks.all { it.rssi <= result.rssi }
            } else {
                true
            }
        }
    }

    // ── 屬性 3c：結果為 null 時，無任何非排除網路的 RSSI 嚴格超過門檻 ──────────

    test("Property 3c: if result is null, no non-excluded network has RSSI strictly above threshold") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val excludedSsids = emptySet<String>()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )
            if (result == null) {
                // No non-excluded network should have RSSI strictly above threshold
                networks.filter { it.ssid !in excludedSsids }
                    .none { it.rssi > threshold }
            } else {
                true
            }
        }
    }

    // ── 屬性 3d：含排除 SSID 時，結果非 null 的 RSSI 嚴格大於門檻 ─────────────

    test("Property 3d: with excluded SSIDs, if result is non-null, its RSSI is strictly greater than threshold") {
        // **Validates: Requirements 3.5**
        checkAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            // Randomly exclude some SSIDs (first half of the list)
            val excludedSsids = networks.take(networks.size / 2).map { it.ssid }.toSet()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )
            if (result != null) {
                result.rssi shouldBe result.rssi // sanity
                assert(result.rssi > threshold) {
                    "Expected RSSI ${result.rssi} > threshold $threshold"
                }
            }
        }
    }

    // ── 屬性 3e：含排除 SSID 時，結果不包含被排除的 SSID ──────────────────────

    test("Property 3e: with excluded SSIDs, result should never be an excluded network") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val excludedSsids = networks.take(networks.size / 2).map { it.ssid }.toSet()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )
            if (result != null) {
                result.ssid !in excludedSsids
            } else {
                true
            }
        }
    }

    // ── 屬性 3f：含排除 SSID 時，結果為最佳非排除網路 ─────────────────────────

    test("Property 3f: with excluded SSIDs, result is the best among non-excluded networks above threshold") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val excludedSsids = networks.take(networks.size / 2).map { it.ssid }.toSet()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )

            val eligibleNetworks = networks.filter {
                it.ssid !in excludedSsids && it.rssi > threshold
            }

            if (result != null) {
                // Result should be the max RSSI among eligible networks
                eligibleNetworks.all { it.rssi <= result.rssi }
            } else {
                // No eligible networks exist
                eligibleNetworks.isEmpty()
            }
        }
    }

    // ── 屬性 3g：含排除 SSID 時，結果為 null 表示無合格非排除網路 ──────────────

    test("Property 3g: with excluded SSIDs, null result means no eligible non-excluded network above threshold") {
        // **Validates: Requirements 3.5**
        forAll(arbNetworkList(), arbThreshold) { networks, threshold ->
            val excludedSsids = networks.take(networks.size / 2).map { it.ssid }.toSet()
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )
            if (result == null) {
                networks.filter { it.ssid !in excludedSsids }
                    .none { it.rssi > threshold }
            } else {
                true
            }
        }
    }
})
