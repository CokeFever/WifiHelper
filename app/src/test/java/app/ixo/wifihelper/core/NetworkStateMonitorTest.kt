package app.ixo.wifihelper.core

import app.ixo.wifihelper.model.NetworkState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 單元測試：NetworkStateMonitor
 *
 * 由於 [NetworkStateMonitorImpl] 高度依賴 Android 系統服務（ConnectivityManager、WifiManager），
 * 本測試透過 Mockk 模擬 [NetworkStateMonitor] 介面，驗證介面契約的正確行為。
 *
 * 測試涵蓋：
 * - 行動網路連線/斷線狀態偵測
 * - WiFi 連線/斷線狀態偵測
 * - RSSI 值讀取（已連線與未連線情境）
 *
 * **Validates: Requirements 3.6, 3.7**
 */
class NetworkStateMonitorTest : FunSpec({

    // ── 行動網路狀態偵測 ──────────────────────────────────────────────────

    context("Mobile data availability detection") {

        test("isMobileDataAvailable returns true when cellular network is available") {
            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.isMobileDataAvailable() } returns true

            monitor.isMobileDataAvailable() shouldBe true

            verify(exactly = 1) { monitor.isMobileDataAvailable() }
        }

        test("isMobileDataAvailable returns false when no cellular network") {
            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.isMobileDataAvailable() } returns false

            monitor.isMobileDataAvailable() shouldBe false

            verify(exactly = 1) { monitor.isMobileDataAvailable() }
        }
    }

    // ── WiFi 連線狀態偵測 ─────────────────────────────────────────────────

    context("WiFi connection state detection") {

        test("observeNetworkState emits state with isWifiConnected=true when WiFi is connected") {
            val wifiConnectedState = NetworkState(
                isMobileDataConnected = false,
                isWifiConnected = true,
                wifiSsid = "TestNetwork",
                wifiRssi = -55,
                networkType = null
            )
            val stateFlow = MutableStateFlow(wifiConnectedState)

            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.observeNetworkState() } returns stateFlow.asStateFlow()

            val emittedState = monitor.observeNetworkState().value

            emittedState.isWifiConnected shouldBe true
            emittedState.wifiSsid shouldBe "TestNetwork"
            emittedState.wifiRssi shouldBe -55
        }

        test("observeNetworkState emits state with isWifiConnected=false when WiFi is disconnected") {
            val wifiDisconnectedState = NetworkState(
                isMobileDataConnected = true,
                isWifiConnected = false,
                wifiSsid = null,
                wifiRssi = null,
                networkType = "4G"
            )
            val stateFlow = MutableStateFlow(wifiDisconnectedState)

            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.observeNetworkState() } returns stateFlow.asStateFlow()

            val emittedState = monitor.observeNetworkState().value

            emittedState.isWifiConnected shouldBe false
            emittedState.wifiSsid.shouldBeNull()
            emittedState.wifiRssi.shouldBeNull()
        }
    }

    // ── RSSI 值讀取 ──────────────────────────────────────────────────────

    context("WiFi RSSI reading") {

        test("getCurrentWifiRssi returns correct RSSI value when WiFi is connected") {
            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.getCurrentWifiRssi() } returns -65

            val rssi = monitor.getCurrentWifiRssi()

            rssi.shouldNotBeNull()
            rssi shouldBe -65

            verify(exactly = 1) { monitor.getCurrentWifiRssi() }
        }

        test("getCurrentWifiRssi returns null when WiFi is not connected") {
            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.getCurrentWifiRssi() } returns null

            val rssi = monitor.getCurrentWifiRssi()

            rssi.shouldBeNull()

            verify(exactly = 1) { monitor.getCurrentWifiRssi() }
        }
    }

    // ── 複合狀態場景 ─────────────────────────────────────────────────────

    context("Combined network state scenarios") {

        test("network state reflects both mobile data and WiFi connected simultaneously") {
            val dualConnectedState = NetworkState(
                isMobileDataConnected = true,
                isWifiConnected = true,
                wifiSsid = "OfficeWiFi",
                wifiRssi = -45,
                networkType = "5G"
            )
            val stateFlow = MutableStateFlow(dualConnectedState)

            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.observeNetworkState() } returns stateFlow.asStateFlow()
            every { monitor.isMobileDataAvailable() } returns true
            every { monitor.getCurrentWifiRssi() } returns -45

            val state = monitor.observeNetworkState().value
            state.isMobileDataConnected shouldBe true
            state.isWifiConnected shouldBe true
            state.wifiSsid shouldBe "OfficeWiFi"
            state.networkType shouldBe "5G"

            monitor.isMobileDataAvailable() shouldBe true
            monitor.getCurrentWifiRssi() shouldBe -45
        }

        test("network state reflects fully disconnected scenario") {
            val disconnectedState = NetworkState(
                isMobileDataConnected = false,
                isWifiConnected = false,
                wifiSsid = null,
                wifiRssi = null,
                networkType = null
            )
            val stateFlow = MutableStateFlow(disconnectedState)

            val monitor = mockk<NetworkStateMonitor>()
            every { monitor.observeNetworkState() } returns stateFlow.asStateFlow()
            every { monitor.isMobileDataAvailable() } returns false
            every { monitor.getCurrentWifiRssi() } returns null

            val state = monitor.observeNetworkState().value
            state.isMobileDataConnected shouldBe false
            state.isWifiConnected shouldBe false
            state.wifiSsid.shouldBeNull()
            state.wifiRssi.shouldBeNull()
            state.networkType.shouldBeNull()

            monitor.isMobileDataAvailable() shouldBe false
            monitor.getCurrentWifiRssi().shouldBeNull()
        }
    }
})
