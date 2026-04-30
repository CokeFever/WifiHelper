package app.ixo.wifihelper.core

/**
 * 智慧切換引擎的決策結果。
 *
 * 將決策邏輯的輸出抽象為列舉型別，使核心決策函式可獨立測試，
 * 不依賴實際的 WiFi/Hotspot 操作。
 *
 * 需求：3.6, 3.7, 4.3, 4.4, 4.5
 */
enum class SwitchDecision {
    /** 智慧切換已停用，或無需變更 */
    NO_ACTION,

    /** 關閉 Hotspot，連線至最佳 WiFi */
    CONNECT_WIFI,

    /** 中斷 WiFi 連線，恢復 Hotspot */
    RESTORE_HOTSPOT,

    /** 維持當前狀態不變 */
    MAINTAIN_CURRENT
}
