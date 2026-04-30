// Feature: auto-wifi-manager, Property 7: 錯誤訊息不洩漏技術細節
package com.example.autowifimanager.util

import com.example.autowifimanager.model.HotspotResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll

/**
 * 屬性測試：錯誤訊息不洩漏技術細節
 *
 * 對任意技術性錯誤原因字串，驗證轉換後的使用者訊息不包含原始技術字串、
 * 例外類別名稱或堆疊追蹤資訊。
 *
 * **Validates: Requirements 2.6**
 */
class ErrorMessageMapperPropertyTest : FunSpec({

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    /** 常見 Java/Kotlin 例外類別名稱 */
    val exceptionClassNames = listOf(
        "SecurityException",
        "IllegalStateException",
        "IllegalArgumentException",
        "NullPointerException",
        "NoSuchMethodException",
        "InvocationTargetException",
        "ClassNotFoundException",
        "RuntimeException",
        "IOException",
        "UnsupportedOperationException",
        "ReflectiveOperationException",
        "ConnectTimeoutException",
        "SocketTimeoutException",
        "NetworkOnMainThreadException"
    )

    /** 產生例外類別名稱 */
    val arbExceptionClassName = Arb.element(exceptionClassNames)

    /** 產生堆疊追蹤行（如 "at com.example.SomeClass.method(SomeClass.kt:42)"） */
    val arbStackTraceLine = Arb.string(minSize = 3, maxSize = 20).flatMap { className ->
        Arb.int(1..999).map { lineNum ->
            val sanitized = className.filter { it.isLetterOrDigit() }.ifEmpty { "Cls" }
            "at com.example.$sanitized.method($sanitized.kt:$lineNum)"
        }
    }

    /** 產生帶有例外名稱的技術錯誤字串 */
    val arbExceptionMessage = arbExceptionClassName.flatMap { exName ->
        Arb.string(minSize = 1, maxSize = 50).map { detail ->
            "$exName: $detail"
        }
    }

    /** 產生帶有堆疊追蹤的技術錯誤字串 */
    val arbStackTraceMessage = arbExceptionClassName.flatMap { exName ->
        arbStackTraceLine.map { stackLine ->
            "$exName: some error\n\t$stackLine"
        }
    }

    /** 產生包含 .java: 或 .kt: 模式的字串 */
    val arbFileReferenceMessage = Arb.string(minSize = 1, maxSize = 30).flatMap { name ->
        Arb.int(1..999).flatMap { lineNum ->
            Arb.of(".java", ".kt").map { ext ->
                val sanitized = name.filter { it.isLetterOrDigit() }.ifEmpty { "File" }
                "Error in $sanitized$ext:$lineNum"
            }
        }
    }

    /** 產生任意技術性錯誤字串（純隨機） */
    val arbArbitraryReason = Arb.string(minSize = 1, maxSize = 200)

    // ── 堆疊追蹤模式偵測 ───────────────────────────────────────────────────

    /** 堆疊追蹤模式：匹配 "at package.Class.method" 格式 */
    val stackTracePattern = Regex("""at\s+[\w.]+\.\w+\(""")

    /** 檔案參考模式：匹配 ".java:" 或 ".kt:" 格式 */
    val fileReferencePattern = Regex("""\.(java|kt):\d+""")

    // ── 屬性 7a：任意技術錯誤字串不出現在使用者訊息中 ──────────────────────

    test("Property 7a: User message does not contain the original technical reason string") {
        // **Validates: Requirements 2.6**
        forAll(arbArbitraryReason) { reason ->
            val failure = HotspotResult.Failure(reason)
            val userMessage = ErrorMessageMapper.toUserMessage(failure)

            // 若原始 reason 長度 >= 4 且非純中文，則不應出現在使用者訊息中
            // 短字串（如 "a"）可能碰巧出現在中文訊息中，因此只檢查有意義長度的字串
            if (reason.length >= 4 && reason.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                !userMessage.contains(reason)
            } else {
                true // 極短或純非 ASCII 字串跳過此檢查
            }
        }
    }

    // ── 屬性 7b：例外類別名稱不出現在使用者訊息中 ────────────────────────

    test("Property 7b: User message does not contain exception class names") {
        // **Validates: Requirements 2.6**
        forAll(arbExceptionMessage) { reason ->
            val failure = HotspotResult.Failure(reason)
            val userMessage = ErrorMessageMapper.toUserMessage(failure)

            // 驗證所有已知例外類別名稱都不出現在使用者訊息中
            exceptionClassNames.none { exName ->
                userMessage.contains(exName)
            }
        }
    }

    // ── 屬性 7c：堆疊追蹤不出現在使用者訊息中 ──────────────────────────────

    test("Property 7c: User message does not contain stack trace patterns") {
        // **Validates: Requirements 2.6**
        forAll(arbStackTraceMessage) { reason ->
            val failure = HotspotResult.Failure(reason)
            val userMessage = ErrorMessageMapper.toUserMessage(failure)

            // 驗證使用者訊息不包含堆疊追蹤模式
            !stackTracePattern.containsMatchIn(userMessage) &&
                !fileReferencePattern.containsMatchIn(userMessage)
        }
    }

    // ── 屬性 7d：包含檔案參考的錯誤不洩漏至使用者訊息 ────────────────────

    test("Property 7d: User message does not contain file reference patterns (.java: or .kt:)") {
        // **Validates: Requirements 2.6**
        forAll(arbFileReferenceMessage) { reason ->
            val failure = HotspotResult.Failure(reason)
            val userMessage = ErrorMessageMapper.toUserMessage(failure)

            !fileReferencePattern.containsMatchIn(userMessage)
        }
    }

    // ── 屬性 7e：使用者訊息包含建議的下一步動作 ────────────────────────────

    test("Property 7e: User message contains a suggested next action") {
        // **Validates: Requirements 2.6**
        val suggestedActions = listOf("請重試", "請前往系統設定", "請稍後再試", "請稍候", "請確認")

        forAll(arbArbitraryReason) { reason ->
            val failure = HotspotResult.Failure(reason)
            val userMessage = ErrorMessageMapper.toUserMessage(failure)

            // 使用者訊息應包含至少一個建議動作
            suggestedActions.any { action -> userMessage.contains(action) }
        }
    }
})
