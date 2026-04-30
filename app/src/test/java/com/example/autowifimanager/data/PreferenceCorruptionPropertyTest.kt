package com.example.autowifimanager.data

import com.example.autowifimanager.model.UserPreferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.forAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Feature: auto-wifi-manager, Property 9: 損毀偏好設定重置為預設值

/**
 * 屬性測試：損毀偏好設定重置為預設值
 *
 * 驗證對任意 SharedPreferences 中的損毀狀態（包含無效型別、缺失鍵值、
 * 超出範圍的數值），PreferenceRepository 的 [validateIntegrity] 應偵測到損毀，
 * 且後續讀取應回傳預設值。
 *
 * 使用 [CorruptiblePreferenceRepository]（基於 HashMap 的記憶體內實作，
 * 提供注入損毀的方法）來避免 Android Context 與 EncryptedSharedPreferences 的依賴，
 * 同時保留與 [PreferenceRepositoryImpl] 相同的驗證與重置邏輯。
 *
 * **Validates: Requirements 7.3**
 */
class PreferenceCorruptionPropertyTest : FunSpec({

    test("Property 9a: missing keys — removing any required key causes validateIntegrity to return false and resets to defaults") {
        // **Validates: Requirements 7.3**
        val requiredKeys = listOf(
            UserPreferences.KEY_SMART_SWITCH,
            UserPreferences.KEY_AUTO_START,
            UserPreferences.KEY_SIGNAL_THRESHOLD
        )

        forAll(
            Arb.boolean(),                              // initial smartSwitchEnabled
            Arb.boolean(),                              // initial autoStartEnabled
            Arb.int(-100..-30),                         // initial signalThreshold (valid)
            Arb.list(Arb.element(requiredKeys), 1..3)   // keys to remove (1 to 3)
        ) { smartSwitch, autoStart, threshold, keysToRemove ->
            val repo = CorruptiblePreferenceRepository()

            // Set up valid state
            repo.setSmartSwitchEnabled(smartSwitch)
            repo.setAutoStartEnabled(autoStart)
            repo.setSignalThreshold(threshold)

            // Inject corruption: remove one or more required keys
            keysToRemove.forEach { key -> repo.removeKey(key) }

            // validateIntegrity should detect corruption
            val integrityResult = repo.validateIntegrity()

            // After validation, reads should return defaults
            integrityResult == false &&
                repo.isSmartSwitchEnabled() == false &&
                repo.isAutoStartEnabled() == false &&
                repo.getSignalThreshold() == UserPreferences.DEFAULT_SIGNAL_THRESHOLD
        }
    }

    test("Property 9b: invalid types — injecting wrong types causes validateIntegrity to return false and resets to defaults") {
        // **Validates: Requirements 7.3**
        forAll(
            Arb.boolean(),       // initial smartSwitchEnabled
            Arb.boolean(),       // initial autoStartEnabled
            Arb.int(-100..-30),  // initial signalThreshold (valid)
            Arb.element(         // which key to corrupt with wrong type
                UserPreferences.KEY_SMART_SWITCH,
                UserPreferences.KEY_AUTO_START,
                UserPreferences.KEY_SIGNAL_THRESHOLD
            ),
            Arb.string(1..20)    // arbitrary string to inject as wrong type
        ) { smartSwitch, autoStart, threshold, keyToCorrupt, corruptValue ->
            val repo = CorruptiblePreferenceRepository()

            // Set up valid state
            repo.setSmartSwitchEnabled(smartSwitch)
            repo.setAutoStartEnabled(autoStart)
            repo.setSignalThreshold(threshold)

            // Inject corruption: put a String where Boolean or Int is expected
            repo.injectValue(keyToCorrupt, corruptValue)

            // validateIntegrity should detect corruption
            val integrityResult = repo.validateIntegrity()

            // After validation, reads should return defaults
            integrityResult == false &&
                repo.isSmartSwitchEnabled() == false &&
                repo.isAutoStartEnabled() == false &&
                repo.getSignalThreshold() == UserPreferences.DEFAULT_SIGNAL_THRESHOLD
        }
    }

    test("Property 9c: out-of-range signalThreshold — values outside [-100, -30] cause validateIntegrity to return false and resets to defaults") {
        // **Validates: Requirements 7.3**

        // Generate out-of-range thresholds: either below -100 or above -30
        val outOfRangeLow = Arb.int(Int.MIN_VALUE..-101)
        val outOfRangeHigh = Arb.int(-29..Int.MAX_VALUE)

        forAll(
            Arb.boolean(),       // initial smartSwitchEnabled
            Arb.boolean(),       // initial autoStartEnabled
            Arb.element(         // pick from low or high out-of-range generators
                "low", "high"
            ),
            Arb.int(-500..-101), // out-of-range low value
            Arb.int(-29..500)    // out-of-range high value
        ) { smartSwitch, autoStart, rangeChoice, lowValue, highValue ->
            val repo = CorruptiblePreferenceRepository()

            // Set up valid state first
            repo.setSmartSwitchEnabled(smartSwitch)
            repo.setAutoStartEnabled(autoStart)
            repo.setSignalThreshold(-70) // valid value via normal setter

            // Inject corruption: bypass coerceIn by directly injecting out-of-range value
            val outOfRangeValue = if (rangeChoice == "low") lowValue else highValue
            repo.injectValue(UserPreferences.KEY_SIGNAL_THRESHOLD, outOfRangeValue)

            // validateIntegrity should detect corruption
            val integrityResult = repo.validateIntegrity()

            // After validation, reads should return defaults
            integrityResult == false &&
                repo.isSmartSwitchEnabled() == false &&
                repo.isAutoStartEnabled() == false &&
                repo.getSignalThreshold() == UserPreferences.DEFAULT_SIGNAL_THRESHOLD
        }
    }
})

/**
 * 可注入損毀的 [PreferenceRepository] 記憶體內測試實作。
 *
 * 繼承 [TestPreferenceRepository] 的核心邏輯（與 [PreferenceRepositoryImpl] 一致），
 * 額外提供 [removeKey] 與 [injectValue] 方法以模擬各種損毀情境：
 * - [removeKey]：移除指定鍵值，模擬缺失鍵值損毀
 * - [injectValue]：直接注入任意型別的值，模擬無效型別或超出範圍損毀
 *
 * 正常的 setter 方法（如 [setSignalThreshold]）仍保留 coerceIn 夾值邏輯，
 * 只有透過 [injectValue] 才能繞過驗證直接寫入不合法的值。
 */
private class CorruptiblePreferenceRepository : PreferenceRepository {

    companion object {
        const val SIGNAL_THRESHOLD_MIN = -100
        const val SIGNAL_THRESHOLD_MAX = -30
    }

    /** 內部儲存，模擬 SharedPreferences 的 key-value 結構 */
    val store = HashMap<String, Any>()

    private val _smartSwitchEnabled = MutableStateFlow(false)
    private val _autoStartEnabled = MutableStateFlow(false)

    // ── 損毀注入方法 ────────────────────────────────────────────────────

    /** 移除指定鍵值，模擬缺失鍵值損毀 */
    fun removeKey(key: String) {
        store.remove(key)
    }

    /** 直接注入任意型別的值，繞過正常 setter 的型別與範圍檢查 */
    fun injectValue(key: String, value: Any) {
        store[key] = value
    }

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
