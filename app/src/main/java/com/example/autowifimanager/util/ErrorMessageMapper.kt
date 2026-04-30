package com.example.autowifimanager.util

import com.example.autowifimanager.model.HotspotResult

/**
 * 錯誤訊息轉換器：將 [HotspotResult.Failure] 中的技術性錯誤原因
 * 轉換為使用者友善的中文訊息。
 *
 * 設計原則：
 * 1. 轉換後的訊息不包含原始技術錯誤字串、例外類別名稱或堆疊追蹤
 * 2. 每個錯誤訊息包含建議的下一步動作
 * 3. 技術細節僅記錄至 Logcat，不向使用者暴露
 *
 * @see HotspotResult.Failure
 */
object ErrorMessageMapper {

    /**
     * 將 [HotspotResult.Failure] 轉換為使用者友善的錯誤訊息。
     *
     * 根據技術性錯誤原因的關鍵字進行分類，回傳對應的中文訊息與建議動作。
     * 若無法辨識錯誤類型，則回傳通用錯誤訊息。
     *
     * @param failure 包含技術性錯誤原因的失敗結果
     * @return 使用者友善的中文錯誤訊息，包含建議的下一步動作
     */
    fun toUserMessage(failure: HotspotResult.Failure): String {
        val reason = failure.reason.lowercase()
        return when {
            reason.contains("permission") || reason.contains("denied") ||
                reason.contains("security") ->
                "操作未成功，可能缺少必要權限。請前往系統設定確認權限已開啟。"

            reason.contains("timeout") || reason.contains("timed out") ->
                "操作逾時，請稍後再試。"

            reason.contains("not supported") || reason.contains("unsupported") ||
                reason.contains("not available") ->
                "目前裝置不支援此操作。請前往系統設定手動操作。"

            reason.contains("already") || reason.contains("in progress") ->
                "操作正在進行中，請稍候。"

            reason.contains("network") || reason.contains("connectivity") ||
                reason.contains("connection") ->
                "網路連線異常，請確認網路狀態後重試。"

            reason.contains("reflection") || reason.contains("invoke") ||
                reason.contains("method") ->
                "操作未成功，請前往系統設定手動操作。"

            else ->
                "操作未成功，請重試。若問題持續，請前往系統設定手動操作。"
        }
    }
}
