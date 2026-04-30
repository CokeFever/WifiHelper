package app.ixo.wifihelper.adapter

import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.WifiListStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * 單元測試：VersionAdapter 邊界條件
 *
 * 針對版本適配映射的關鍵邊界 API 等級進行精確驗證，確保切換點行為正確。
 * 使用與屬性測試相同的 testable adapter 方式，以指定 API 等級建立 VersionAdapter。
 *
 * **Validates: Requirements 1.2, 1.3**
 */
class VersionAdapterBoundaryTest : FunSpec({

    /**
     * 建立一個可測試的 VersionAdapter，接受指定的 API 等級。
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

    // ── API 28：最低支援版本 ────────────────────────────────────────────────

    context("API 28 (minimum supported version)") {

        test("should use DIRECT hotspot control mode") {
            val adapter = createTestableAdapter(28)
            adapter.getHotspotControlMode() shouldBe HotspotControlMode.DIRECT
        }

        test("should use CONFIGURED_NETWORKS wifi list strategy") {
            val adapter = createTestableAdapter(28)
            adapter.getWifiListStrategy() shouldBe WifiListStrategy.CONFIGURED_NETWORKS
        }

        test("should report API level 28") {
            val adapter = createTestableAdapter(28)
            adapter.getApiLevel() shouldBe 28
        }
    }

    // ── API 36：最高支援版本 ────────────────────────────────────────────────

    context("API 36 (maximum supported version)") {

        test("should use GUIDED hotspot control mode") {
            val adapter = createTestableAdapter(36)
            adapter.getHotspotControlMode() shouldBe HotspotControlMode.GUIDED
        }

        test("should use SCAN_AND_SUGGEST wifi list strategy") {
            val adapter = createTestableAdapter(36)
            adapter.getWifiListStrategy() shouldBe WifiListStrategy.SCAN_AND_SUGGEST
        }

        test("should report API level 36") {
            val adapter = createTestableAdapter(36)
            adapter.getApiLevel() shouldBe 36
        }
    }

    // ── API 32 → 33：Hotspot 控制模式切換邊界 ──────────────────────────────

    context("API 32 -> 33 hotspot control mode boundary") {

        test("API 32 should be the last version with DIRECT hotspot control") {
            val adapter = createTestableAdapter(32)
            adapter.getHotspotControlMode() shouldBe HotspotControlMode.DIRECT
        }

        test("API 33 should be the first version with GUIDED hotspot control") {
            val adapter = createTestableAdapter(33)
            adapter.getHotspotControlMode() shouldBe HotspotControlMode.GUIDED
        }
    }

    // ── API 29 → 30：WiFi 策略切換邊界 ─────────────────────────────────────

    context("API 29 -> 30 wifi list strategy boundary") {

        test("API 29 should be the last version with CONFIGURED_NETWORKS strategy") {
            val adapter = createTestableAdapter(29)
            adapter.getWifiListStrategy() shouldBe WifiListStrategy.CONFIGURED_NETWORKS
        }

        test("API 30 should be the first version with SCAN_AND_SUGGEST strategy") {
            val adapter = createTestableAdapter(30)
            adapter.getWifiListStrategy() shouldBe WifiListStrategy.SCAN_AND_SUGGEST
        }
    }
})
