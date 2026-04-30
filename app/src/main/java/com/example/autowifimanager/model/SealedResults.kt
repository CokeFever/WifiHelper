package com.example.autowifimanager.model

import android.content.Intent

/**
 * Hotspot 操作結果。
 */
sealed class HotspotResult {
    /** 操作成功 */
    object Success : HotspotResult()
    /** 需要使用者手動操作（引導模式） */
    data class NeedUserAction(val intent: Intent) : HotspotResult()
    /** 操作失敗 */
    data class Failure(val reason: String) : HotspotResult()
}

/**
 * WiFi 連線結果。
 */
sealed class ConnectionResult {
    /** 連線成功 */
    object Success : ConnectionResult()
    /** 連線失敗 */
    data class Failure(val reason: ConnectionFailureReason) : ConnectionResult()
}
