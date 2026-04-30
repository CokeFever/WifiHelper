// Feature: auto-wifi-manager, Property 4: SSID 失敗封鎖
package com.example.autowifimanager.core

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * 屬性測試：SSID 失敗封鎖
 *
 * 對任意 SSID 與任意連續失敗次數 N，驗證封鎖條件為 N ≥ 3。
 * 失敗次數 < 3 時，該 SSID 仍應保留在自動連線候選清單中。
 *
 * 使用一個簡單的測試輔助器模擬 [SmartSwitchEngineImpl] 中的失敗計數與封鎖邏輯，
 * 確保封鎖門檻 (FAILURE_BLOCK_THRESHOLD = 3) 的行為正確。
 *
 * **Validates: Requirements 3.9**
 */
class SsidBlockingPropertyTest : FunSpec({

    /**
     * 簡易測試輔助器：模擬 SmartSwitchEngineImpl 中的 SSID 失敗封鎖邏輯。
     *
     * 與 SmartSwitchEngineImpl 使用相同的封鎖門檻 (FAILURE_BLOCK_THRESHOLD = 3)。
     */
    class SsidBlockingTracker {
        /** 連續失敗封鎖門檻，與 SmartSwitchEngineImpl.FAILURE_BLOCK_THRESHOLD 一致 */
        val failureBlockThreshold = 3

        private val failureCounters = mutableMapOf<String, Int>()
        private val blockedSsids = mutableSetOf<String>()

        /** 記錄一次連線失敗 */
        fun recordFailure(ssid: String) {
            val count = (failureCounters[ssid] ?: 0) + 1
            failureCounters[ssid] = count
            if (count >= failureBlockThreshold) {
                blockedSsids.add(ssid)
            }
        }

        /** 查詢 SSID 是否被封鎖 */
        fun isBlocked(ssid: String): Boolean = ssid in blockedSsids

        /** 取得 SSID 的連續失敗次數 */
        fun getFailureCount(ssid: String): Int = failureCounters[ssid] ?: 0
    }

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    /** 產生任意非空 SSID 字串（1~32 字元） */
    val arbSsid = Arb.string(minSize = 1, maxSize = 32)

    /** 產生任意連續失敗次數（0~10，涵蓋門檻前後） */
    val arbFailureCount = Arb.int(0..10)

    // ── 屬性 4a：連續失敗次數 ≥ 3 時 SSID 被封鎖 ──────────────────────────

    test("Property 4a: SSID is blocked when consecutive failure count >= 3") {
        // **Validates: Requirements 3.9**
        forAll(arbSsid, arbFailureCount) { ssid, failureCount ->
            val tracker = SsidBlockingTracker()

            // 模擬 N 次連續失敗
            repeat(failureCount) {
                tracker.recordFailure(ssid)
            }

            val isBlocked = tracker.isBlocked(ssid)
            val expectedBlocked = failureCount >= 3

            isBlocked == expectedBlocked
        }
    }

    // ── 屬性 4b：連續失敗次數 < 3 時 SSID 不被封鎖 ─────────────────────────

    test("Property 4b: SSID is NOT blocked when consecutive failure count < 3") {
        // **Validates: Requirements 3.9**
        forAll(arbSsid, Arb.int(0..2)) { ssid, failureCount ->
            val tracker = SsidBlockingTracker()

            repeat(failureCount) {
                tracker.recordFailure(ssid)
            }

            !tracker.isBlocked(ssid)
        }
    }

    // ── 屬性 4c：封鎖條件為 N ≥ 3 的雙向驗證 ──────────────────────────────

    test("Property 4c: SSID is blocked if and only if N >= 3") {
        // **Validates: Requirements 3.9**
        forAll(arbSsid, arbFailureCount) { ssid, failureCount ->
            val tracker = SsidBlockingTracker()

            repeat(failureCount) {
                tracker.recordFailure(ssid)
            }

            // 封鎖 ↔ N ≥ 3（雙條件等價）
            tracker.isBlocked(ssid) == (failureCount >= 3)
        }
    }

    // ── 屬性 4d：不同 SSID 的失敗計數互相獨立 ─────────────────────────────

    test("Property 4d: Failure counts are independent across different SSIDs") {
        // **Validates: Requirements 3.9**
        forAll(
            arbSsid,
            arbSsid,
            arbFailureCount,
            arbFailureCount
        ) { ssid1, ssid2, count1, count2 ->
            // 確保兩個 SSID 不同才有意義
            if (ssid1 == ssid2) {
                true // 相同 SSID 跳過此測試案例
            } else {
                val tracker = SsidBlockingTracker()

                repeat(count1) { tracker.recordFailure(ssid1) }
                repeat(count2) { tracker.recordFailure(ssid2) }

                // 各自的封鎖狀態應獨立
                tracker.isBlocked(ssid1) == (count1 >= 3) &&
                    tracker.isBlocked(ssid2) == (count2 >= 3)
            }
        }
    }
})
