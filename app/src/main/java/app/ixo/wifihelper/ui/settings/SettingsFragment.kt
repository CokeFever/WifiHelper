package app.ixo.wifihelper.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.ixo.wifihelper.R
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 偏好設定 Fragment。
 *
 * 提供開機自動啟動開關與訊號強度門檻調整（SeekBar）。
 * 綁定 [SettingsViewModel] 觀察設定狀態變化並處理使用者互動事件。
 *
 * SeekBar 範圍對應：
 * - SeekBar progress 0 → -100 dBm（最弱）
 * - SeekBar progress 70 → -30 dBm（最強）
 * - 公式：threshold = progress + MIN_THRESHOLD
 *
 * 需求：4.2
 */
@AndroidEntryPoint
class SettingsFragment : Fragment() {

    companion object {
        /** 訊號強度門檻最小值（dBm） */
        const val MIN_THRESHOLD = -100

        /** 訊號強度門檻最大值（dBm） */
        const val MAX_THRESHOLD = -30

        /** SeekBar 最大值 = MAX_THRESHOLD - MIN_THRESHOLD */
        const val SEEKBAR_MAX = MAX_THRESHOLD - MIN_THRESHOLD
    }

    private val viewModel: SettingsViewModel by viewModels()

    private lateinit var autoStartSwitch: SwitchMaterial
    private lateinit var signalThresholdValue: TextView
    private lateinit var signalThresholdSeekbar: SeekBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupEventHandlers()
        observeUiState()
    }

    /**
     * 綁定佈局中的 View 元件。
     */
    private fun bindViews(view: View) {
        autoStartSwitch = view.findViewById(R.id.auto_start_switch)
        signalThresholdValue = view.findViewById(R.id.signal_threshold_value)
        signalThresholdSeekbar = view.findViewById(R.id.signal_threshold_seekbar)

        // 設定 SeekBar 範圍
        signalThresholdSeekbar.max = SEEKBAR_MAX
    }

    /**
     * 設定 UI 事件處理器。
     *
     * - 開機自動啟動開關：切換 [SettingsViewModel.toggleAutoStart]
     * - 訊號強度門檻 SeekBar：呼叫 [SettingsViewModel.setSignalThreshold]
     */
    private fun setupEventHandlers() {
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            // 只在使用者手動操作時觸發（避免 updateUi 設值時重複觸發）
            if (isChecked == viewModel.uiState.value.autoStartEnabled) return@setOnCheckedChangeListener
            viewModel.toggleAutoStart()
        }

        signalThresholdSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val threshold = progress + MIN_THRESHOLD
                    viewModel.setSignalThreshold(threshold)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No-op
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // No-op
            }
        })
    }

    /**
     * 觀察 [SettingsViewModel.uiState] 並更新 UI。
     *
     * 使用 [repeatOnLifecycle] 確保僅在 Fragment 可見時收集狀態。
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    /**
     * 根據 [SettingsUiState] 更新所有 UI 元件。
     */
    private fun updateUi(state: SettingsUiState) {
        // 更新開機自動啟動開關（避免觸發 listener 的無限迴圈）
        if (autoStartSwitch.isChecked != state.autoStartEnabled) {
            autoStartSwitch.isChecked = state.autoStartEnabled
        }

        // 更新訊號強度門檻顯示值
        signalThresholdValue.text = getString(
            R.string.signal_threshold_format,
            state.signalThreshold
        )

        // 更新 SeekBar 位置
        val seekBarProgress = state.signalThreshold - MIN_THRESHOLD
        if (signalThresholdSeekbar.progress != seekBarProgress) {
            signalThresholdSeekbar.progress = seekBarProgress
        }
    }
}
