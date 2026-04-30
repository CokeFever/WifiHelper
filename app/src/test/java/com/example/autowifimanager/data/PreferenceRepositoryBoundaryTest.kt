package com.example.autowifimanager.data

import com.example.autowifimanager.model.UserPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 單元測試：PreferenceRepository 邊界條件
 *
 * 測試 signalThreshold 邊界值、首次啟動預設值、resetToDefaults() 行為，
 * 以及 StateFlow 在重置後的更新。
 *
 * 使用基於 HashMap 的 [TestPreferenceRepository] 記憶體內實作，
 * 避免 Android Context 與 EncryptedSharedPreferences 的依賴。
 *
 * 需求：4.7, 7.1, 7.3
 */
class PreferenceRepositoryBoundaryTest : FunSpec({

    // ── 1. 首次啟動預設值 ─────────────────────────────────────────────────

    test("default values on first launch: smartSwitchEnabled=false, autoStartEnabled=false, signalThreshold=-70") {
        val repo = BoundaryTestPreferenceRepository()

        repo.isSmartSwitchEnabled() shouldBe false
        repo.isAutoStartEnabled() shouldBe false
        repo.getSignalThreshold() shouldBe UserPreferences.DEFAULT_SIGNAL_THRESHOLD
    }

    // ── 2. signalThreshold 邊界值：-100 應被精確保留 ──────────────────────

    test("signalThreshold boundary: setting -100 should be preserved exactly") {
        val repo = BoundaryTestPreferenceRepository()

        repo.setSignalThreshold(-100)

        repo.getSignalThreshold() shouldBe -100
    }

    // ── 3. signalThreshold 邊界值：-30 應被精確保留 ───────────────────────

    test("signalThreshold boundary: setting -30 should be preserved exactly") {
        val repo = BoundaryTestPreferenceRepository()

        repo.setSignalThreshold(-30)

        repo.getSignalThreshold() shouldBe -30
    }

    // ── 4. signalThreshold 夾值：-101 應被夾至 -100 ──────────────────────

    test("signalThreshold clamping: setting -101 should be clamped to -100") {
        val repo = BoundaryTestPreferenceRepository()

        repo.setSignalThreshold(-101)

        repo.getSignalThreshold() shouldBe -100
    }

    // ── 5. signalThreshold 夾值：-29 應被夾至 -30 ────────────────────────

    test("signalThreshold clamping: setting -29 should be clamped to -30") {
        val repo = BoundaryTestPreferenceRepository()

        repo.setSignalThreshold(-29)

        repo.getSignalThreshold() shouldBe -30
    }

    // ── 6. resetToDefaults()：設定自訂值後重置應恢復所有預設值 ──────────────

    test("resetToDefaults: after setting custom values, reset should restore all defaults") {
        val repo = BoundaryTestPreferenceRepository()

        // 設定自訂值
        repo.setSmartSwitchEnabled(true)
        repo.setAutoStartEnabled(true)
        repo.setSignalThreshold(-50)

        // 確認自訂值已生效
        repo.isSmartSwitchEnabled() shouldBe true
        repo.isAutoStartEnabled() shouldBe true
        repo.getSignalThreshold() shouldBe -50

        // 重置
        repo.resetToDefaults()

        // 驗證所有值恢復為預設
        repo.isSmartSwitchEnabled() shouldBe false
        repo.isAutoStartEnabled() shouldBe false
        repo.getSignalThreshold() shouldBe UserPreferences.DEFAULT_SIGNAL_THRESHOLD
    }

    // ── 7. resetToDefaults()：StateFlow 也應被更新 ───────────────────────

    test("resetToDefaults: StateFlows should also be updated after reset") {
        val repo = BoundaryTestPreferenceRepository()

        // 設定自訂值
        repo.setSmartSwitchEnabled(true)
        repo.setAutoStartEnabled(true)

        // 確認 StateFlow 反映自訂值
        repo.observeSmartSwitchEnabled().value shouldBe true
        repo.observeAutoStartEnabled().value shouldBe true

        // 重置
        repo.resetToDefaults()

        // 驗證 StateFlow 也恢復為預設值
        repo.observeSmartSwitchEnabled().value shouldBe false
        repo.observeAutoStartEnabled().value shouldBe false
    }
})

/**
 * 基於 HashMap 的 [PreferenceRepository] 記憶體內測試實作。
 *
 * 複製 [PreferenceRepositoryImpl] 的核心邏輯：
 * - Boolean 偏好設定直接儲存/讀取
 * - signalThreshold 儲存時使用 [coerceIn] 夾值至 [-100, -30] 範圍
 * - 預設值與 [UserPreferences] 的預設值一致
 * - [resetToDefaults] 重置所有值並更新 StateFlow
 * - [validateIntegrity] 檢查鍵值存在性、型別正確性、範圍合法性
 */
private class BoundaryTestPreferenceRepository : PreferenceRepository {

    companion object {
        const val SIGNAL_THRESHOLD_MIN = -100
        const val SIGNAL_THRESHOLD_MAX = -30
    }

    private val store = HashMap<String, Any>()

    private val _smartSwitchEnabled = MutableStateFlow(false)
    private val _autoStartEnabled = MutableStateFlow(false)

    // ── 智慧切換開關 ──────────────────────────────────────────────────────

    override fun isSmartSwitchEnabled(): Boolean {
        return store[UserPreferences.KEY_SMART_SWITCH] as? Boolean ?: false
    }

    override fun setSmartSwitchEnabled(enabled: Boolean) {
        store[UserPreferences.KEY_SMART_SWITCH] = enabled
        _smartSwitchEnabled.value = enabled
    }

    override fun observeSmartSwitchEnabled(): StateFlow<Boolean> {
        return _smartSwitchEnabled.asStateFlow()
    }

    // ── 開機自動啟動開關 ──────────────────────────────────────────────────

    override fun isAutoStartEnabled(): Boolean {
        return store[UserPreferences.KEY_AUTO_START] as? Boolean ?: false
    }

    override fun setAutoStartEnabled(enabled: Boolean) {
        store[UserPreferences.KEY_AUTO_START] = enabled
        _autoStartEnabled.value = enabled
    }

    override fun observeAutoStartEnabled(): StateFlow<Boolean> {
        return _autoStartEnabled.asStateFlow()
    }

    // ── 訊號強度門檻 ────────────────────────────────────────────────────

    override fun getSignalThreshold(): Int {
        return store[UserPreferences.KEY_SIGNAL_THRESHOLD] as? Int
            ?: UserPreferences.DEFAULT_SIGNAL_THRESHOLD
    }

    override fun setSignalThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(SIGNAL_THRESHOLD_MIN, SIGNAL_THRESHOLD_MAX)
        store[UserPreferences.KEY_SIGNAL_THRESHOLD] = clamped
    }

    // ── 重置與驗證 ──────────────────────────────────────────────────────

    override fun resetToDefaults() {
        store[UserPreferences.KEY_SMART_SWITCH] = false
        store[UserPreferences.KEY_AUTO_START] = false
        store[UserPreferences.KEY_SIGNAL_THRESHOLD] = UserPreferences.DEFAULT_SIGNAL_THRESHOLD

        _smartSwitchEnabled.value = false
        _autoStartEnabled.value = false
    }

    override fun validateIntegrity(): Boolean {
        try {
            val requiredKeys = listOf(
                UserPreferences.KEY_SMART_SWITCH,
                UserPreferences.KEY_AUTO_START,
                UserPreferences.KEY_SIGNAL_THRESHOLD
            )
            for (key in requiredKeys) {
                if (!store.containsKey(key)) {
                    resetToDefaults()
                    return false
                }
            }

            if (store[UserPreferences.KEY_SMART_SWITCH] !is Boolean) {
                resetToDefaults()
                return false
            }
            if (store[UserPreferences.KEY_AUTO_START] !is Boolean) {
                resetToDefaults()
                return false
            }
            if (store[UserPreferences.KEY_SIGNAL_THRESHOLD] !is Int) {
                resetToDefaults()
                return false
            }

            val threshold = store[UserPreferences.KEY_SIGNAL_THRESHOLD] as Int
            if (threshold < SIGNAL_THRESHOLD_MIN || threshold > SIGNAL_THRESHOLD_MAX) {
                resetToDefaults()
                return false
            }

            return true
        } catch (e: Exception) {
            resetToDefaults()
            return false
        }
    }
}
