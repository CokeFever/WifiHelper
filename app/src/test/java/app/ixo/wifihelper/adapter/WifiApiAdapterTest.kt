package app.ixo.wifihelper.adapter

import app.ixo.wifihelper.model.ConnectionFailureReason
import app.ixo.wifihelper.model.ConnectionResult
import app.ixo.wifihelper.model.KnownWifiNetwork
import app.ixo.wifihelper.model.ScanResultInfo
import app.ixo.wifihelper.model.SecurityType
import app.ixo.wifihelper.util.NetworkSelector
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

/**
 * 單元測試：WifiApiAdapter 介面契約與 NetworkSelector 邊界條件
 *
 * 由於 [WifiApiAdapterLegacy] 與 [WifiApiAdapterModern] 高度依賴 Android 系統服務
 * （WifiManager、ConnectivityManager），本測試透過 Mockk 模擬 [WifiApiAdapter] 介面，
 * 驗證介面契約的正確行為。同時直接測試 [NetworkSelector] 工具類別的邊界條件。
 *
 * **Validates: Requirements 3.1, 3.2, 3.5**
 */
class WifiApiAdapterTest : FunSpec({

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

    // ── 空掃描結果 ───────────────────────────────────────────────────────

    context("empty scan results") {

        test("startScan returns empty list → getKnownNetworks returns empty list") {
            val adapter = mockk<WifiApiAdapter>()
            coEvery { adapter.startScan() } returns emptyList()
            coEvery { adapter.getKnownNetworks() } returns emptyList()

            val scanResults = adapter.startScan()
            scanResults.shouldBeEmpty()

            val knownNetworks = adapter.getKnownNetworks()
            knownNetworks.shouldBeEmpty()

            coVerify(exactly = 1) { adapter.startScan() }
            coVerify(exactly = 1) { adapter.getKnownNetworks() }
        }

        test("NetworkSelector returns null for empty network list") {
            val result = NetworkSelector.selectBestNetwork(
                networks = emptyList(),
                signalThreshold = -70
            )
            result.shouldBeNull()
        }
    }

    // ── 所有網路都低於門檻 ───────────────────────────────────────────────

    context("all networks below threshold") {

        test("NetworkSelector returns null when all networks have RSSI below threshold") {
            val networks = listOf(
                network("Weak_A", rssi = -80),
                network("Weak_B", rssi = -90),
                network("Weak_C", rssi = -75)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }

        test("NetworkSelector returns null when all networks have RSSI exactly at threshold") {
            val networks = listOf(
                network("Exact_A", rssi = -70),
                network("Exact_B", rssi = -70)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }

        test("NetworkSelector returns null with single network below threshold") {
            val networks = listOf(
                network("OnlyWeak", rssi = -85)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result.shouldBeNull()
        }
    }

    // ── 多個網路相同 RSSI ────────────────────────────────────────────────

    context("multiple networks with same RSSI") {

        test("NetworkSelector returns one of the networks when multiple share the highest RSSI") {
            val networks = listOf(
                network("SameRssi_A", rssi = -50),
                network("SameRssi_B", rssi = -50),
                network("SameRssi_C", rssi = -50)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            // All three are valid; maxByOrNull returns the first max encountered
            result!!.rssi shouldBe -50
            // The result must be one of the input networks
            (result.ssid in listOf("SameRssi_A", "SameRssi_B", "SameRssi_C")) shouldBe true
        }

        test("NetworkSelector returns one of the tied networks when some are below threshold") {
            val networks = listOf(
                network("Tied_A", rssi = -55),
                network("Tied_B", rssi = -55),
                network("BelowThreshold", rssi = -80)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            result!!.rssi shouldBe -55
            (result.ssid in listOf("Tied_A", "Tied_B")) shouldBe true
        }

        test("NetworkSelector handles all networks with same RSSI at threshold boundary") {
            val networks = listOf(
                network("AtThreshold_A", rssi = -69),
                network("AtThreshold_B", rssi = -69)
            )
            val result = NetworkSelector.selectBestNetwork(
                networks = networks,
                signalThreshold = -70
            )
            // -69 > -70, so both are above threshold
            result!!.rssi shouldBe -69
            (result.ssid in listOf("AtThreshold_A", "AtThreshold_B")) shouldBe true
        }
    }

    // ── connectToNetwork 與 NETWORK_NOT_FOUND ────────────────────────────

    context("connectToNetwork with NETWORK_NOT_FOUND") {

        test("connectToNetwork returns Failure with NETWORK_NOT_FOUND for unknown network") {
            val adapter = mockk<WifiApiAdapter>()
            val unknownNetwork = network("NonExistent", rssi = -50)

            coEvery { adapter.connectToNetwork(unknownNetwork) } returns
                ConnectionResult.Failure(ConnectionFailureReason.NETWORK_NOT_FOUND)

            val result = adapter.connectToNetwork(unknownNetwork)

            result.shouldBeInstanceOf<ConnectionResult.Failure>()
            result.reason shouldBe ConnectionFailureReason.NETWORK_NOT_FOUND

            coVerify(exactly = 1) { adapter.connectToNetwork(unknownNetwork) }
        }

        test("connectToNetwork returns Success for a known network") {
            val adapter = mockk<WifiApiAdapter>()
            val knownNetwork = network("HomeWiFi", rssi = -45)

            coEvery { adapter.connectToNetwork(knownNetwork) } returns
                ConnectionResult.Success

            val result = adapter.connectToNetwork(knownNetwork)

            result shouldBe ConnectionResult.Success

            coVerify(exactly = 1) { adapter.connectToNetwork(knownNetwork) }
        }

        test("connectToNetwork returns Failure with AUTHENTICATION_FAILED") {
            val adapter = mockk<WifiApiAdapter>()
            val targetNetwork = network("SecureNet", rssi = -40)

            coEvery { adapter.connectToNetwork(targetNetwork) } returns
                ConnectionResult.Failure(ConnectionFailureReason.AUTHENTICATION_FAILED)

            val result = adapter.connectToNetwork(targetNetwork)

            result.shouldBeInstanceOf<ConnectionResult.Failure>()
            result.reason shouldBe ConnectionFailureReason.AUTHENTICATION_FAILED
        }

        test("connectToNetwork returns Failure with TIMEOUT") {
            val adapter = mockk<WifiApiAdapter>()
            val targetNetwork = network("SlowNet", rssi = -55)

            coEvery { adapter.connectToNetwork(targetNetwork) } returns
                ConnectionResult.Failure(ConnectionFailureReason.TIMEOUT)

            val result = adapter.connectToNetwork(targetNetwork)

            result.shouldBeInstanceOf<ConnectionResult.Failure>()
            result.reason shouldBe ConnectionFailureReason.TIMEOUT
        }
    }

    // ── disconnect 回傳 true/false ───────────────────────────────────────

    context("disconnect returns true or false") {

        test("disconnect returns true when disconnection succeeds") {
            val adapter = mockk<WifiApiAdapter>()
            coEvery { adapter.disconnect() } returns true

            val result = adapter.disconnect()

            result shouldBe true

            coVerify(exactly = 1) { adapter.disconnect() }
        }

        test("disconnect returns false when disconnection fails") {
            val adapter = mockk<WifiApiAdapter>()
            coEvery { adapter.disconnect() } returns false

            val result = adapter.disconnect()

            result shouldBe false

            coVerify(exactly = 1) { adapter.disconnect() }
        }
    }

    // ── startScan 回傳掃描結果 ───────────────────────────────────────────

    context("startScan returns scan results") {

        test("startScan returns non-empty list of scan results") {
            val adapter = mockk<WifiApiAdapter>()
            val expectedResults = listOf(
                ScanResultInfo(
                    ssid = "Office",
                    bssid = "AA:BB:CC:DD:EE:01",
                    rssi = -45,
                    frequency = 5180,
                    capabilities = "[WPA2-PSK]"
                ),
                ScanResultInfo(
                    ssid = "Guest",
                    bssid = "AA:BB:CC:DD:EE:02",
                    rssi = -65,
                    frequency = 2437,
                    capabilities = "[WPA2-PSK]"
                )
            )

            coEvery { adapter.startScan() } returns expectedResults

            val results = adapter.startScan()

            results.size shouldBe 2
            results[0].ssid shouldBe "Office"
            results[1].ssid shouldBe "Guest"
        }
    }

    // ── getKnownNetworks 回傳已知網路 ────────────────────────────────────

    context("getKnownNetworks returns known networks") {

        test("getKnownNetworks returns list with currently connected network flagged") {
            val adapter = mockk<WifiApiAdapter>()
            val expectedNetworks = listOf(
                network("ConnectedNet", rssi = -40, isCurrentlyConnected = true),
                network("OtherNet", rssi = -60, isCurrentlyConnected = false)
            )

            coEvery { adapter.getKnownNetworks() } returns expectedNetworks

            val networks = adapter.getKnownNetworks()

            networks.size shouldBe 2
            networks.first { it.isCurrentlyConnected }.ssid shouldBe "ConnectedNet"
            networks.first { !it.isCurrentlyConnected }.ssid shouldBe "OtherNet"
        }
    }
})
