package app.ixo.wifihelper.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.ixo.wifihelper.R
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.NetworkMode
import app.ixo.wifihelper.ui.MainActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 主控面板 Fragment。
 *
 * 顯示智慧切換主開關、Hotspot 操作按鈕、WiFi 連線狀態與已知網路數量。
 * 綁定 [DashboardViewModel] 觀察 UI 狀態變化並處理使用者互動事件。
 *
 * 智慧切換開關切換時，會同步通知 [MainActivity] 啟動/停止 Foreground Service。
 *
 * 需求：2.1, 2.7, 4.1, 4.5
 */
@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var smartSwitchToggle: SwitchMaterial
    private lateinit var hotspotButton: MaterialButton
    private lateinit var hotspotStateText: TextView
    private lateinit var wifiStatusText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupEventHandlers()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        // Refresh hotspot state when returning from system settings (requirement 2.7)
        viewModel.refreshHotspotState()
    }

    /**
     * 綁定佈局中的 View 元件。
     */
    private fun bindViews(view: View) {
        smartSwitchToggle = view.findViewById(R.id.smart_switch_toggle)
        hotspotButton = view.findViewById(R.id.hotspot_button)
        hotspotStateText = view.findViewById(R.id.hotspot_state_text)
        wifiStatusText = view.findViewById(R.id.wifi_status_text)
    }

    /**
     * 設定 UI 事件處理器。
     *
     * - 智慧切換開關：切換 [DashboardViewModel.toggleSmartSwitch]，
     *   並通知 [MainActivity] 同步 Foreground Service 狀態
     * - Hotspot 按鈕：呼叫 [DashboardViewModel.toggleHotspot]，處理操作結果
     */
    private fun setupEventHandlers() {
        smartSwitchToggle.setOnCheckedChangeListener { _, isChecked ->
            // 只在使用者手動操作時觸發（避免 updateUi 設值時重複觸發）
            if (isChecked == viewModel.uiState.value.smartSwitchEnabled) return@setOnCheckedChangeListener

            viewModel.toggleSmartSwitch()
            (activity as? MainActivity)?.syncServiceState()
        }

        hotspotButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.toggleHotspot()
                handleHotspotResult(result)
            }
        }
    }

    /**
     * 觀察 [DashboardViewModel.uiState] 並更新 UI。
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
     * 根據 [DashboardUiState] 更新所有 UI 元件。
     */
    private fun updateUi(state: DashboardUiState) {
        // 更新智慧切換開關（避免觸發 listener 的無限迴圈）
        if (smartSwitchToggle.isChecked != state.smartSwitchEnabled) {
            smartSwitchToggle.isChecked = state.smartSwitchEnabled
        }

        // 更新 Hotspot 狀態文字
        hotspotStateText.text = when (state.hotspotState) {
            HotspotState.ENABLED -> getString(R.string.hotspot_state_enabled)
            HotspotState.DISABLED -> getString(R.string.hotspot_state_disabled)
            HotspotState.UNKNOWN -> getString(R.string.hotspot_state_unknown)
        }

        // 更新 WiFi 連線狀態
        wifiStatusText.text = if (state.connectedSsid != null) {
            getString(R.string.wifi_connected_format, state.connectedSsid)
        } else {
            getString(R.string.wifi_disconnected)
        }
    }

    /**
     * 處理 Hotspot 操作結果。
     *
     * - [HotspotResult.Success]：無需額外處理，UI 會透過狀態觀察自動更新
     * - [HotspotResult.NeedUserAction]：啟動系統設定 Intent（引導模式）
     * - [HotspotResult.Failure]：顯示 Toast 錯誤訊息
     */
    private fun handleHotspotResult(result: HotspotResult) {
        when (result) {
            is HotspotResult.Success -> {
                // UI 會透過 StateFlow 自動更新
            }
            is HotspotResult.NeedUserAction -> {
                try {
                    startActivity(result.intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    // Fallback 至通用無線設定頁面
                    try {
                        startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        )
                    } catch (e2: android.content.ActivityNotFoundException) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.hotspot_settings_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            is HotspotResult.Failure -> {
                Toast.makeText(
                    requireContext(),
                    result.reason,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
