package app.ixo.wifihelper.service

import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.NetworkMode
import app.ixo.wifihelper.model.SmartSwitchState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

// Feature: auto-wifi-manager, Property 10: 通知內容反映運作狀態

/**
 * 屬性測試：通知內容反映運作狀態
 *
 * 對任意 [SmartSwitchState]，驗證 [NotificationHelper.getNotificationText] 產生的通知文字
 * 包含能讓使用者辨識當前運作模式的描述文字：
 * - [NetworkMode.WIFI_CONNECTED] → 包含 "WiFi 連線中"
 * - [NetworkMode.HOTSPOT_ACTIVE] → 包含 "熱點啟用中"
 * - [NetworkMode.SWITCHING] → 包含 "掃描中"
 * - [NetworkMode.DISCONNECTED] → 包含 "已停用"
 * - [NetworkMode.MOBILE_DATA] → 包含 "行動數據"
 *
 * **Validates: Requirements 5.5**
 */
class NotificationPropertyTest : FunSpec({

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    val arbNetworkMode = Arb.element(NetworkMode.entries)
    val arbHotspotState = Arb.element(HotspotState.entries)
    val arbConnectedSsid = Arb.string(1..30).orNull()
    val arbFailedAttempts = Arb.string(1..10).map { mapOf(it to 1) }

    /** 產生任意 SmartSwitchState */
    fun arbSmartSwitchState(mode: NetworkMode? = null): Arb<SmartSwitchState> {
        val modeArb = if (mode != null) Arb.element(listOf(mode)) else arbNetworkMode
        return Arb.boolean().let { arbRunning ->
            io.kotest.property.arbitrary.arbitrary {
                SmartSwitchState(
                    isRunning = arbRunning.bind(),
                    currentMode = modeArb.bind(),
                    lastScanTime = Arb.long(0L..System.currentTimeMillis()).bind(),
                    connectedSsid = arbConnectedSsid.bind(),
                    hotspotState = arbHotspotState.bind(),
                    mobileDataAvailable = Arb.boolean().bind(),
                    knownNetworksCount = Arb.int(0..50).bind(),
                    failedAttempts = arbFailedAttempts.bind()
                )
            }
        }
    }

    // ── 屬性 10a：WIFI_CONNECTED 模式的通知包含 "WiFi 連線中" ──────────────

    test("Property 10a: WIFI_CONNECTED mode notification text contains WiFi connection keyword") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState(NetworkMode.WIFI_CONNECTED)) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.contains("WiFi 連線中")
        }
    }

    // ── 屬性 10b：HOTSPOT_ACTIVE 模式的通知包含 "熱點啟用中" ───────────────

    test("Property 10b: HOTSPOT_ACTIVE mode notification text contains hotspot keyword") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState(NetworkMode.HOTSPOT_ACTIVE)) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.contains("熱點啟用中")
        }
    }

    // ── 屬性 10c：SWITCHING 模式的通知包含 "掃描中" ─────────────────────────

    test("Property 10c: SWITCHING mode notification text contains scanning keyword") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState(NetworkMode.SWITCHING)) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.contains("掃描中")
        }
    }

    // ── 屬性 10d：DISCONNECTED 模式的通知包含 "已停用" ─────────────────────

    test("Property 10d: DISCONNECTED mode notification text contains disabled keyword") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState(NetworkMode.DISCONNECTED)) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.contains("已停用")
        }
    }

    // ── 屬性 10e：MOBILE_DATA 模式的通知包含 "行動數據" ────────────────────

    test("Property 10e: MOBILE_DATA mode notification text contains mobile data keyword") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState(NetworkMode.MOBILE_DATA)) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.contains("行動數據")
        }
    }

    // ── 屬性 10f：WIFI_CONNECTED 且有 connectedSsid 時，通知包含該 SSID ───

    test("Property 10f: WIFI_CONNECTED with non-null SSID includes the SSID in notification text") {
        // **Validates: Requirements 5.5**
        forAll(Arb.string(1..30)) { ssid ->
            val state = SmartSwitchState(
                isRunning = true,
                currentMode = NetworkMode.WIFI_CONNECTED,
                lastScanTime = System.currentTimeMillis(),
                connectedSsid = ssid,
                hotspotState = HotspotState.DISABLED,
                mobileDataAvailable = false,
                knownNetworksCount = 1,
                failedAttempts = emptyMap()
            )
            val text = NotificationHelper.getNotificationText(state)
            text.contains(ssid)
        }
    }

    // ── 屬性 10g：對任意 SmartSwitchState，通知文字非空 ────────────────────

    test("Property 10g: For any SmartSwitchState, notification text is never empty") {
        // **Validates: Requirements 5.5**
        forAll(arbSmartSwitchState()) { state ->
            val text = NotificationHelper.getNotificationText(state)
            text.isNotEmpty()
        }
    }

    // ── 屬性 10h：每種 NetworkMode 都有唯一可辨識的關鍵字 ──────────────────

    test("Property 10h: Each NetworkMode produces a distinguishable notification text") {
        // **Validates: Requirements 5.5**
        val baseState = SmartSwitchState(
            isRunning = true,
            currentMode = NetworkMode.WIFI_CONNECTED, // will be overridden
            lastScanTime = System.currentTimeMillis(),
            connectedSsid = "TestSSID",
            hotspotState = HotspotState.DISABLED,
            mobileDataAvailable = false,
            knownNetworksCount = 1,
            failedAttempts = emptyMap()
        )

        val modeKeywords = mapOf(
            NetworkMode.WIFI_CONNECTED to "WiFi 連線中",
            NetworkMode.HOTSPOT_ACTIVE to "熱點啟用中",
            NetworkMode.SWITCHING to "掃描中",
            NetworkMode.DISCONNECTED to "已停用",
            NetworkMode.MOBILE_DATA to "行動數據"
        )

        for ((mode, keyword) in modeKeywords) {
            val state = baseState.copy(currentMode = mode)
            val text = NotificationHelper.getNotificationText(state)
            text shouldContain keyword
        }

        // Verify all modes are covered
        modeKeywords.keys.size shouldBe NetworkMode.entries.size
    }
})
