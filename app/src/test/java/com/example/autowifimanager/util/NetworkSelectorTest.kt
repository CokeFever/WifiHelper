package com.example.autowifimanager.util

import com.example.autowifimanager.model.KnownWifiNetwork
import com.example.autowifimanager.model.SecurityType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * 單元測試：NetworkSelector 最佳網路選擇邏輯
 *
 * 測試網路選擇演算法的邊界條件與具體範例，確保過濾、排序、排除邏輯正確。
 *
 * **Validates: Requirements 3.5**
 */
class NetworkSelectorTest : FunSpec({

    // ── 輔助函式 ──────────────────────────────────────────────────────────

    fun network(
        ssid: String,
        rssi: Int,
        securityType: SecurityType = SecurityType.WPA2_PSK,
        isCurrentlyConnected: Boolean = false
    ): KnownWifiNetwork = KnownWifiNetwork(
        ssid = ssid,
        bssid = "00:11:22:33:44:55",
        rssi = rssi,
        frequency = 2437,
        securityType = securityType,
        isCurrentlyConnected = isCurrentlyConnected,
        lastSeen = System.currentTimeMillis()
    )

    // ── 空清單 ────────────────────────────────────────────────────────────

    context("empty network list") {

        test("should return null when network list is empty") {
            val result = NetworkSelector.selectBestNetwork(
                networks = emptyList(),
                signalThreshold = -70
            )
            result.shouldBeNull()
        }
    }

    // ── 所有網路低於門檻 ──────────────────────────────────────────────────

    context("all networks below threshold") {

        test("should return null when all networks have RSSI below threshold") {
            val networks = listOf(
                network("WeakNet1", rssi = -80),
                network("WeakNet2", rssi = -90),
                network("WeakNet3", rssi = -85)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }

        test("should return null when network RSSI equals threshold exactly") {
            val networks = listOf(
                network("ExactThreshold", rssi = -70)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }
    }

    // ── 正常選擇：選擇 RSSI 最高者 ───────────────────────────────────────

    context("selecting best network above threshold") {

        test("should select the network with highest RSSI above threshold") {
            val networks = listOf(
                network("GoodNet", rssi = -50),
                network("BetterNet", rssi = -40),
                network("WeakNet", rssi = -80)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result!!.ssid shouldBe "BetterNet"
            result.rssi shouldBe -40
        }

        test("should select the only network above threshold") {
            val networks = listOf(
                network("OnlyGood", rssi = -60),
                network("Weak1", rssi = -80),
                network("Weak2", rssi = -90)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result!!.ssid shouldBe "OnlyGood"
        }

        test("should handle single network above threshold") {
            val networks = listOf(
                network("SingleNet", rssi = -45)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result!!.ssid shouldBe "SingleNet"
        }
    }

    // ── 相同 RSSI ─────────────────────────────────────────────────────────

    context("networks with same RSSI") {

        test("should return one of the networks when multiple have same highest RSSI") {
            val networks = listOf(
                network("Net_A", rssi = -50),
                network("Net_B", rssi = -50),
                network("Net_C", rssi = -60)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            // Both Net_A and Net_B are valid; maxByOrNull returns the first max
            result!!.rssi shouldBe -50
        }
    }

    // ── SSID 排除 ─────────────────────────────────────────────────────────

    context("excluded SSIDs") {

        test("should skip excluded SSIDs even if they have the highest RSSI") {
            val networks = listOf(
                network("BestButExcluded", rssi = -30),
                network("SecondBest", rssi = -50),
                network("Weak", rssi = -80)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70,
                excludedSsids = setOf("BestButExcluded")
            )
            result!!.ssid shouldBe "SecondBest"
        }

        test("should return null when all above-threshold networks are excluded") {
            val networks = listOf(
                network("Excluded1", rssi = -40),
                network("Excluded2", rssi = -50),
                network("BelowThreshold", rssi = -80)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70,
                excludedSsids = setOf("Excluded1", "Excluded2")
            )
            result.shouldBeNull()
        }

        test("should handle empty excluded set normally") {
            val networks = listOf(
                network("Net1", rssi = -45)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70,
                excludedSsids = emptySet()
            )
            result!!.ssid shouldBe "Net1"
        }

        test("should exclude networks below threshold that are also in excluded set") {
            val networks = listOf(
                network("ExcludedWeak", rssi = -80),
                network("GoodNet", rssi = -50)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70,
                excludedSsids = setOf("ExcludedWeak")
            )
            result!!.ssid shouldBe "GoodNet"
        }
    }

    // ── 門檻邊界值 ───────────────────────────────────────────────────────

    context("threshold boundary values") {

        test("should select network with RSSI just above threshold (-69 > -70)") {
            val networks = listOf(
                network("JustAbove", rssi = -69)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result!!.ssid shouldBe "JustAbove"
        }

        test("should reject network with RSSI exactly at threshold (-70 == -70)") {
            val networks = listOf(
                network("AtThreshold", rssi = -70)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }

        test("should reject network with RSSI just below threshold (-71 < -70)") {
            val networks = listOf(
                network("JustBelow", rssi = -71)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }
    }
})
