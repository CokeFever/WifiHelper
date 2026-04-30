package app.ixo.wifihelper.data

import kotlinx.coroutines.flow.StateFlow

/**
 * 偏好設定儲存庫介面。
 *
 * 管理使用者偏好設定的持久化，包含智慧切換開關、開機自動啟動開關、
 * 訊號強度門檻等設定。透過 StateFlow 暴露偏好設定變更，
 * 並提供資料完整性驗證與重置功能。
 */
interface PreferenceRepository {

    /** 查詢智慧切換是否啟用 */
    fun isSmartSwitchEnabled(): Boolean

    /** 設定智慧切換開關 */
    fun setSmartSwitchEnabled(enabled: Boolean)

    /** 觀察智慧切換開關狀態變更 */
    fun observeSmartSwitchEnabled(): StateFlow<Boolean>

    /** 查詢開機自動啟動是否啟用 */
    fun isAutoStartEnabled(): Boolean

    /** 設定開機自動啟動開關 */
    fun setAutoStartEnabled(enabled: Boolean)

    /** 觀察開機自動啟動開關狀態變更 */
    fun observeAutoStartEnabled(): StateFlow<Boolean>

    /** 取得訊號強度門檻（預設 -70 dBm） */
    fun getSignalThreshold(): Int

    /** 設定訊號強度門檻 */
    fun setSignalThreshold(threshold: Int)

    /** 重置所有偏好設定為預設值 */
    fun resetToDefaults()

    /**
     * 驗證偏好設定資料完整性。
     *
     * 檢查項目：
     * - 缺失鍵值
     * - 無效型別
     * - signalThreshold 超出 [-100, -30] 範圍
     *
     * 若偵測到損毀，自動重置為預設值。
     *
     * @return true 表示資料完整，false 表示偵測到損毀並已重置
     */
    fun validateIntegrity(): Boolean
}
