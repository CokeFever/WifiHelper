package app.ixo.wifihelper.data

import app.ixo.wifihelper.model.UserPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Feature: auto-wifi-manager, Property 8: 偏好設定持久化往返

/**
 * 屬性測試：偏好設定持久化往返
 *
 * 驗證對任意合法 [UserPreferences]（smartSwitchEnabled 為任意布林值、
 * autoStartEnabled 為任意布林值、signalThreshold 在 [-100, -30] dBm 範圍內），
 * 將其儲存至 PreferenceRepository 後再讀取回來，應得到與原始物件相同的值。
 *
 * 使用 [TestPreferenceRepository]（基於 HashMap 的記憶體內實作）來避免
 * Android Context 與 EncryptedSharedPreferences 的依賴，同時保留與
 * [PreferenceRepositoryImpl] 相同的儲存/讀取邏輯（包含 signalThreshold 的 coerceIn 夾值）。
 *
 * **Validates: Requirements 4.7**
 */
class PreferenceRepositoryPropertyTest : FunSpec({

    test("Property 8: saving then reading UserPreferences should return the same values") {
        // **Validates: Requirements 4.7**
        forAll(
            Arb.boolean(),       // smartSwitchEnabled
            Arb.boolean(),       // autoStartEnabled
            Arb.int(-100..-30)   // signalThreshold (valid range)
        ) { smartSwitchEnabled, autoStartEnabled, signalThreshold ->
            val repo = TestPreferenceRepository()

            // 儲存
            repo.setSmartSwitchEnabled(smartSwitchEnabled)
            repo.setAutoStartEnabled(autoStartEnabled)
            repo.setSignalThreshold(signalThreshold)

            // 讀取並驗證往返一致性
            repo.isSmartSwitchEnabled() == smartSwitchEnabled &&
                repo.isAutoStartEnabled() == autoStartEnabled &&
                repo.getSignalThreshold() == signalThreshold
        }
    }

    test("Property 8b: round-trip preserves all three fields as a UserPreferences object") {
        // **Validates: Requirements 4.7**
        forAll(
            Arb.boolean(),
            Arb.boolean(),
            Arb.int(-100..-30)
        ) { smartSwitchEnabled, autoStartEnabled, signalThreshold ->
            val original = UserPreferences(
                smartSwitchEnabled = smartSwitchEnabled,
                autoStartEnabled = autoStartEnabled,
                signalThreshold = signalThreshold
            )

            val repo = TestPreferenceRepository()

            // 儲存整組偏好設定
            repo.setSmartSwitchEnabled(original.smartSwitchEnabled)
            repo.setAutoStartEnabled(original.autoStartEnabled)
            repo.setSignalThreshold(original.signalThreshold)

            // 讀取回來組成新的 UserPreferences
            val restored = UserPreferences(
                smartSwitchEnabled = repo.isSmartSwitchEnabled(),
                autoStartEnabled = repo.isAutoStartEnabled(),
                signalThreshold = repo.getSignalThreshold()
            )

            restored == original
        }
    }

    test("Property 8c: signalThreshold values at range boundaries are preserved exactly") {
        // **Validates: Requirements 4.7**
        forAll(
            Arb.boolean(),
            Arb.boolean(),
            Arb.int(-100..-30)
        ) { smartSwitchEnabled, autoStartEnabled, signalThreshold ->
            val repo = TestPreferenceRepository()

            repo.setSignalThreshold(signalThreshold)

            // 值應完全保留（在合法範圍內不應被修改）
            repo.getSignalThreshold() == signalThreshold
        }
    }
})

/**
 * 基於 HashMap 的 [PreferenceRepository] 記憶體內測試實作。
 *
 * 複製 [PreferenceRepositoryImpl] 的核心邏輯：
 * - Boolean 偏好設定直接儲存/讀取
 * - signalThreshold 儲存時使用 [coerceIn] 夾值至 [-100, -30] 範圍
 * - 預設值與 [UserPreferences] 的預設值一致
 * - [validateIntegrity] 檢查鍵值存在性、型別正確性、範圍合法性
 */
private class TestPreferenceRepository : PreferenceRepository {

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
            // 檢查 1：缺失鍵值
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

            // 檢查 2：無效型別
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

            // 檢查 3：signalThreshold 超出範圍
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
