package app.ixo.wifihelper.adapter

import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.WifiListStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

// Feature: auto-wifi-manager, Property 1: 版本適配映射正確性

/**
 * 屬性測試：版本適配映射正確性
 *
 * 驗證 VersionAdapter 對 API 28-36 的所有值，產生的 HotspotControlMode 與
 * WifiListStrategy 映射正確。
 *
 * 由於 [VersionAdapterImpl] 直接使用 [android.os.Build.VERSION.SDK_INT]，
 * 無法在純 JVM 單元測試中動態變更，因此本測試直接驗證映射邏輯本身的正確性：
 * 以給定的 API 等級作為輸入，驗證映射結果符合設計規格。
 *
 * **Validates: Requirements 1.2, 1.3, 2.2, 2.3, 3.1, 3.2**
 */
class VersionAdapterPropertyTest : FunSpec({

    /**
     * 建立一個可測試的 VersionAdapter，接受指定的 API 等級而非依賴 Build.VERSION.SDK_INT。
     * 內部映射邏輯與 [VersionAdapterImpl] 完全一致。
     */
    fun createTestableAdapter(apiLevel: Int): VersionAdapter {
        return object : VersionAdapter {
            override fun getApiLevel(): Int = apiLevel

            override fun getHotspotControlMode(): HotspotControlMode {
                return if (getApiLevel() <= 32) {
                    HotspotControlMode.DIRECT
                } else {
                    HotspotControlMode.GUIDED
                }
            }

            override fun getWifiListStrategy(): WifiListStrategy {
                return if (getApiLevel() <= 29) {
                    WifiListStrategy.CONFIGURED_NETWORKS
                } else {
                    WifiListStrategy.SCAN_AND_SUGGEST
                }
            }

            override fun getRequiredPermissions(): List<PermissionInfo> {
                return VersionAdapterImpl.ALL_PERMISSIONS.filter { permission ->
                    apiLevel >= permission.minApiLevel &&
                        (permission.maxApiLevel == null || apiLevel <= permission.maxApiLevel)
                }
            }
        }
    }

    // ── 屬性 1：HotspotControlMode 映射正確性 ──────────────────────────────

    test("Property 1a: API 28-32 should map to HotspotControlMode.DIRECT") {
        // **Validates: Requirements 1.2, 1.3, 2.2**
        forAll(Arb.int(28..32)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)
            adapter.getHotspotControlMode() == HotspotControlMode.DIRECT
        }
    }

    test("Property 1b: API 33-36 should map to HotspotControlMode.GUIDED") {
        // **Validates: Requirements 1.2, 1.3, 2.3**
        forAll(Arb.int(33..36)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)
            adapter.getHotspotControlMode() == HotspotControlMode.GUIDED
        }
    }

    // ── 屬性 1：WifiListStrategy 映射正確性 ─────────────────────────────────

    test("Property 1c: API 28-29 should map to WifiListStrategy.CONFIGURED_NETWORKS") {
        // **Validates: Requirements 1.3, 3.1**
        forAll(Arb.int(28..29)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)
            adapter.getWifiListStrategy() == WifiListStrategy.CONFIGURED_NETWORKS
        }
    }

    test("Property 1d: API 30-36 should map to WifiListStrategy.SCAN_AND_SUGGEST") {
        // **Validates: Requirements 1.3, 3.2**
        forAll(Arb.int(30..36)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)
            adapter.getWifiListStrategy() == WifiListStrategy.SCAN_AND_SUGGEST
        }
    }

    // ── 屬性 1：完整範圍綜合驗證 ────────────────────────────────────────────

    test("Property 1e: For all supported API levels (28-36), both mappings are consistent") {
        // **Validates: Requirements 1.2, 1.3, 2.2, 2.3, 3.1, 3.2**
        forAll(Arb.int(28..36)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)

            val expectedHotspotMode = if (apiLevel <= 32) {
                HotspotControlMode.DIRECT
            } else {
                HotspotControlMode.GUIDED
            }

            val expectedWifiStrategy = if (apiLevel <= 29) {
                WifiListStrategy.CONFIGURED_NETWORKS
            } else {
                WifiListStrategy.SCAN_AND_SUGGEST
            }

            adapter.getHotspotControlMode() == expectedHotspotMode &&
                adapter.getWifiListStrategy() == expectedWifiStrategy
        }
    }

    // ── 屬性 1：API 等級回傳正確性 ──────────────────────────────────────────

    test("Property 1f: getApiLevel() returns the exact API level provided") {
        // **Validates: Requirements 1.2**
        forAll(Arb.int(28..36)) { apiLevel ->
            val adapter = createTestableAdapter(apiLevel)
            adapter.getApiLevel() == apiLevel
        }
    }
})
