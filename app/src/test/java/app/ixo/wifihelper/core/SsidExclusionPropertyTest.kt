// Feature: auto-wifi-manager, Property 5: 手動排除阻止自動連線
package app.ixo.wifihelper.core

import app.ixo.wifihelper.model.KnownWifiNetwork
import app.ixo.wifihelper.model.SecurityType
import app.ixo.wifihelper.util.NetworkSelector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * 屬性測試：手動排除阻止自動連線
 *
 * 對任意已排除的 SSID，即使該 SSID 的訊號強度為所有已知網路中最高且超過門檻，
 * [NetworkSelector.selectBestNetwork] 也不應選擇該 SSID 作為連線目標。
 *
 * 此測試直接使用 [NetworkSelector.selectBestNetwork]，因為它是純函式且接受
 * excludedSsids 參數，正是手動排除邏輯的核心實作。
 *
 * **Validates: Requirements 3.10**
 */
class SsidExclusionPropertyTest : FunSpec({

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    /** 產生任意非空 SSID 字串（1~20 字元） */
    val arbSsid = Arb.string(minSize = 1, maxSize = 20)

    /** 產生任意訊號強度門檻（dBm） */
    val arbSignalThreshold = Arb.int(-100..-30)

    /** 產生任意 RSSI 值（dBm） */
    val arbRssi = Arb.int(-100..0)

    /** 產生任意安全類型 */
    val arbSecurityType = Arb.element(SecurityType.entries)

    /** 產生一個 KnownWifiNetwork */
    fun arbNetwork(ssid: String? = null, rssi: Int? = null) = Arb.int(-100..0).map { generatedRssi ->
        KnownWifiNetwork(
            ssid = ssid ?: "Network_${generatedRssi.hashCode()}",
            bssid = "00:11:22:33:44:55",
            rssi = rssi ?: generatedRssi,
            frequency = 2437,
            securityType = SecurityType.WPA2_PSK,
            isCurrentlyConnected = false,
            lastSeen = System.currentTimeMillis()
        )
    }

    // ── 屬性 5a：被排除的 SSID 不會被選為連線目標 ──────────────────────────

    test("Property 5a: An excluded SSID is never selected as the best network, even with the strongest signal") {
        // **Validates: Requirements 3.10**
        forAll(arbSsid, arbSignalThreshold) { excludedSsid, threshold ->
            // 建立一個被排除的網路，其 RSSI 為 0（最強可能值），遠超門檻
            val excludedNetwork = KnownWifiNetwork(
                ssid = excludedSsid,
                bssid = "AA:BB:CC:DD:EE:FF",
                rssi = 0, // 最強訊號
                frequency = 2437,
                securityType = SecurityType.WPA2_PSK,
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )

            // 建立一個未被排除的較弱網路
            val otherNetwork = KnownWifiNetwork(
                ssid = "OtherNetwork_${excludedSsid.hashCode()}",
                bssid = "11:22:33:44:55:66",
                rssi = threshold + 1, // 剛好超過門檻
                frequency = 5180,
                securityType = SecurityType.WPA2_PSK,
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )

            val networks = listOf(excludedNetwork, otherNetwork)
            val excludedSsids = setOf(excludedSsid)

            val selected = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )

            // 選擇結果不應為被排除的 SSID
            selected?.ssid != excludedSsid
        }
    }

    // ── 屬性 5b：排除最強網路後，應選擇次強的未排除網路 ────────────────────

    test("Property 5b: When the strongest network is excluded, the next strongest non-excluded network is selected") {
        // **Validates: Requirements 3.10**
        forAll(arbSsid, arbSignalThreshold) { excludedSsid, threshold ->
            val excludedNetwork = KnownWifiNetwork(
                ssid = excludedSsid,
                bssid = "AA:BB:CC:DD:EE:FF",
                rssi = 0, // 最強訊號
                frequency = 2437,
                securityType = SecurityType.WPA2_PSK,
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )

            val secondBestSsid = "SecondBest_${excludedSsid.hashCode()}"
            val secondBestNetwork = KnownWifiNetwork(
                ssid = secondBestSsid,
                bssid = "11:22:33:44:55:66",
                rssi = threshold + 1, // 超過門檻
                frequency = 5180,
                securityType = SecurityType.WPA2_PSK,
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )

            val networks = listOf(excludedNetwork, secondBestNetwork)
            val excludedSsids = setOf(excludedSsid)

            val selected = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = threshold,
                excludedSsids = excludedSsids
            )

            // 應選擇次強的未排除網路
            selected?.ssid == secondBestSsid
        }
    }

    // ── 屬性 5c：所有網路都被排除時，不選擇任何網路 ────────────────────────

    test("Property 5c: When all networks are excluded, no network is selected") {
        // **Validates: Requirements 3.10**
        forAll(arbSsid, arbSignalThreshold) { ssid, threshold ->
            val network = KnownWifiNetwork(
                ssid = ssid,
                bssid = "AA:BB:CC:DD:EE:FF",
                rssi = 0, // 最強訊號
                frequency = 2437,
                securityType = SecurityType.WPA2_PSK,
                isCurrentlyConnected = false,
                lastSeen = System.currentTimeMillis()
            )

            val selected = NetworkSelector.selectBestNetwork(
                networks = listOf(network),
                signalThreshold = threshold,
                excludedSsids = setOf(ssid)
            )

            selected == null
        }
    }

    // ── 屬性 5d：未被排除的網路不受排除清單影響 ────────────────────────────

    test("Property 5d: Non-excluded networks are unaffected by the exclusion list") {
        // **Validates: Requirements 3.10**
        forAll(arbSsid, arbSsid, arbSignalThreshold) { excludedSsid, otherSsid, threshold ->
            if (excludedSsid == otherSsid) {
                true // 相同 SSID 跳過
            } else {
                val otherRssi = threshold + 1 // 超過門檻

                val excludedNetwork = KnownWifiNetwork(
                    ssid = excludedSsid,
                    bssid = "AA:BB:CC:DD:EE:FF",
                    rssi = otherRssi + 10, // 更強但被排除
                    frequency = 2437,
                    securityType = SecurityType.WPA2_PSK,
                    isCurrentlyConnected = false,
                    lastSeen = System.currentTimeMillis()
                )

                val otherNetwork = KnownWifiNetwork(
                    ssid = otherSsid,
                    bssid = "11:22:33:44:55:66",
                    rssi = otherRssi,
                    frequency = 5180,
                    securityType = SecurityType.WPA2_PSK,
                    isCurrentlyConnected = false,
                    lastSeen = System.currentTimeMillis()
                )

                // 有排除清單時
                val selectedWithExclusion = NetworkSelector.selectBestNetwork(
                    networks = listOf(excludedNetwork, otherNetwork),
                    signalThreshold = threshold,
                    excludedSsids = setOf(excludedSsid)
                )

                // 無排除清單時（僅 otherNetwork）
                val selectedWithoutExclusion = NetworkSelector.selectBestNetwork(
                    networks = listOf(otherNetwork),
                    signalThreshold = threshold,
                    excludedSsids = emptySet()
                )

                // 未被排除的網路在兩種情況下都應被選中
                selectedWithExclusion?.ssid == otherSsid &&
                    selectedWithoutExclusion?.ssid == otherSsid
            }
        }
    }
})
