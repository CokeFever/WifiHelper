package com.example.autowifimanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autowifimanager.data.PreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings 畫面的 UI 狀態。
 */
data class SettingsUiState(
    /** 開機自動啟動是否啟用 */
    val autoStartEnabled: Boolean = false,
    /** 訊號強度門檻（dBm） */
    val signalThreshold: Int = -70
)

/**
 * Settings 偏好設定的 ViewModel。
 *
 * 注入 [PreferenceRepository]，透過 StateFlow 暴露設定狀態，
 * 提供開機自動啟動開關與訊號強度門檻的變更方法。
 *
 * 需求：4.2, 4.7
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentSettings()
        observePreferenceChanges()
    }

    /**
     * 從 [PreferenceRepository] 載入當前設定值。
     */
    private fun loadCurrentSettings() {
        _uiState.value = SettingsUiState(
            autoStartEnabled = preferenceRepository.isAutoStartEnabled(),
            signalThreshold = preferenceRepository.getSignalThreshold()
        )
    }

    /**
     * 觀察 [PreferenceRepository] 的開機自動啟動開關變更。
     */
    private fun observePreferenceChanges() {
        viewModelScope.launch {
            preferenceRepository.observeAutoStartEnabled().collect { enabled ->
                _uiState.value = _uiState.value.copy(autoStartEnabled = enabled)
            }
        }
    }

    /**
     * 切換開機自動啟動開關。
     *
     * 需求：4.2
     */
    fun toggleAutoStart() {
        val newEnabled = !_uiState.value.autoStartEnabled
        preferenceRepository.setAutoStartEnabled(newEnabled)
        _uiState.value = _uiState.value.copy(autoStartEnabled = newEnabled)
    }

    /**
     * 設定訊號強度門檻。
     *
     * 值會被 [PreferenceRepository] 限制在 [-100, -30] 範圍內。
     *
     * 需求：4.7
     *
     * @param threshold 新的訊號強度門檻（dBm）
     */
    fun setSignalThreshold(threshold: Int) {
        preferenceRepository.setSignalThreshold(threshold)
        // 讀回實際儲存的值（可能被 clamp）
        val actualThreshold = preferenceRepository.getSignalThreshold()
        _uiState.value = _uiState.value.copy(signalThreshold = actualThreshold)
    }
}
