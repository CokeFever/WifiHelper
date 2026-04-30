package app.ixo.wifihelper.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import app.ixo.wifihelper.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PreferenceRepository 的實作，使用 EncryptedSharedPreferences 安全儲存偏好設定。
 *
 * 內部使用 MutableStateFlow 追蹤偏好設定變更，對外暴露為不可變的 StateFlow。
 * 提供資料完整性驗證，偵測到損毀時自動重置為預設值。
 */
@Singleton
class PreferenceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferenceRepository {

    companion object {
        private const val PREFS_FILE_NAME = "auto_wifi_manager_prefs"

        /** 訊號強度門檻的合法範圍 */
        const val SIGNAL_THRESHOLD_MIN = -100
        const val SIGNAL_THRESHOLD_MAX = -30
    }

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _smartSwitchEnabled = MutableStateFlow(false)
    private val _autoStartEnabled = MutableStateFlow(false)

    init {
        // 初始化時從 SharedPreferences 載入當前值
        _smartSwitchEnabled.value = prefs.getBoolean(
            UserPreferences.KEY_SMART_SWITCH, false
        )
        _autoStartEnabled.value = prefs.getBoolean(
            UserPreferences.KEY_AUTO_START, false
        )
    }

    // ── 智慧切換開關 ──────────────────────────────────────────────────────

    override fun isSmartSwitchEnabled(): Boolean {
        return prefs.getBoolean(UserPreferences.KEY_SMART_SWITCH, false)
    }

    override fun setSmartSwitchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UserPreferences.KEY_SMART_SWITCH, enabled).apply()
        _smartSwitchEnabled.value = enabled
    }

    override fun observeSmartSwitchEnabled(): StateFlow<Boolean> {
        return _smartSwitchEnabled.asStateFlow()
    }

    // ── 開機自動啟動開關 ──────────────────────────────────────────────────

    override fun isAutoStartEnabled(): Boolean {
        return prefs.getBoolean(UserPreferences.KEY_AUTO_START, false)
    }

    override fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UserPreferences.KEY_AUTO_START, enabled).apply()
        _autoStartEnabled.value = enabled
    }

    override fun observeAutoStartEnabled(): StateFlow<Boolean> {
        return _autoStartEnabled.asStateFlow()
    }

    // ── 訊號強度門檻 ────────────────────────────────────────────────────

    override fun getSignalThreshold(): Int {
        return prefs.getInt(
            UserPreferences.KEY_SIGNAL_THRESHOLD,
            UserPreferences.DEFAULT_SIGNAL_THRESHOLD
        )
    }

    override fun setSignalThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(SIGNAL_THRESHOLD_MIN, SIGNAL_THRESHOLD_MAX)
        prefs.edit().putInt(UserPreferences.KEY_SIGNAL_THRESHOLD, clamped).apply()
    }

    // ── 重置與驗證 ──────────────────────────────────────────────────────

    override fun resetToDefaults() {
        prefs.edit()
            .putBoolean(UserPreferences.KEY_SMART_SWITCH, false)
            .putBoolean(UserPreferences.KEY_AUTO_START, false)
            .putInt(UserPreferences.KEY_SIGNAL_THRESHOLD, UserPreferences.DEFAULT_SIGNAL_THRESHOLD)
            .apply()

        _smartSwitchEnabled.value = false
        _autoStartEnabled.value = false
    }

    override fun validateIntegrity(): Boolean {
        try {
            val allEntries = prefs.all

            // 檢查 1：缺失鍵值
            val requiredKeys = listOf(
                UserPreferences.KEY_SMART_SWITCH,
                UserPreferences.KEY_AUTO_START,
                UserPreferences.KEY_SIGNAL_THRESHOLD
            )
            for (key in requiredKeys) {
                if (!allEntries.containsKey(key)) {
                    resetToDefaults()
                    return false
                }
            }

            // 檢查 2：無效型別
            val smartSwitchValue = allEntries[UserPreferences.KEY_SMART_SWITCH]
            if (smartSwitchValue !is Boolean) {
                resetToDefaults()
                return false
            }

            val autoStartValue = allEntries[UserPreferences.KEY_AUTO_START]
            if (autoStartValue !is Boolean) {
                resetToDefaults()
                return false
            }

            val thresholdValue = allEntries[UserPreferences.KEY_SIGNAL_THRESHOLD]
            if (thresholdValue !is Int) {
                resetToDefaults()
                return false
            }

            // 檢查 3：signalThreshold 超出範圍
            if (thresholdValue < SIGNAL_THRESHOLD_MIN || thresholdValue > SIGNAL_THRESHOLD_MAX) {
                resetToDefaults()
                return false
            }

            return true
        } catch (e: Exception) {
            // 任何讀取異常都視為損毀
            resetToDefaults()
            return false
        }
    }
}
