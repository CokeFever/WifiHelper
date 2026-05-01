package app.ixo.wifihelper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ixo.wifihelper.adapter.HotspotApiAdapter
import app.ixo.wifihelper.core.NetworkStateMonitor
import app.ixo.wifihelper.core.SmartSwitchEngine
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.NetworkMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard 畫面的 UI 狀態。
 *
 * 將 [app.ixo.wifihelper.model.SmartSwitchState] 映射為 UI 層可直接使用的扁平結構。
 */
data class DashboardUiState(
    /** 智慧切換開關是否啟用 */
    val smartSwitchEnabled: Boolean = false,
    /** 當前 Hotspot 狀態 */
    val hotspotState: HotspotState = HotspotState.UNKNOWN,
    /** 當前網路模式 */
    val networkMode: NetworkMode = NetworkMode.DISCONNECTED,
    /** 目前連線的 WiFi SSID（null 表示未連線） */
    val connectedSsid: String? = null,
    /** 偵測到的已知網路數量 */
    val knownNetworksCount: Int = 0,
    /** 引擎是否正在運作 */
    val isRunning: Boolean = false
)

/**
 * Dashboard 主控面板的 ViewModel。
 *
 * 注入 [SmartSwitchEngine]、[HotspotApiAdapter]、[PreferenceRepository]，
 * 收集引擎狀態並映射為 [DashboardUiState]，提供智慧切換開關與 Hotspot 操作方法。
 *
 * 需求：2.1, 2.5, 4.1
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val smartSwitchEngine: SmartSwitchEngine,
    private val hotspotApiAdapter: HotspotApiAdapter,
    private val preferenceRepository: PreferenceRepository,
    private val networkStateMonitor: NetworkStateMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        collectEngineState()
        collectNetworkState()
        loadInitialHotspotState()
    }

    /**
     * 載入初始 Hotspot 狀態。
     *
     * SmartSwitchEngine 僅在掃描週期中更新 Hotspot 狀態，
     * 因此在 ViewModel 初始化時主動讀取一次，避免 UI 顯示 UNKNOWN。
     */
    private fun loadInitialHotspotState() {
        viewModelScope.launch {
            val state = hotspotApiAdapter.getHotspotState()
            _uiState.value = _uiState.value.copy(hotspotState = state)
        }
    }

    /**
     * 重新讀取 Hotspot 狀態。
     *
     * 供 Fragment 在 onResume 時呼叫，確保使用者從系統設定返回後
     * UI 能反映最新的 Hotspot 狀態（需求 2.7）。
     */
    fun refreshHotspotState() {
        viewModelScope.launch {
            val state = hotspotApiAdapter.getHotspotState()
            _uiState.value = _uiState.value.copy(hotspotState = state)
        }
    }

    /**
     * 收集 [SmartSwitchEngine] 的狀態並映射為 [DashboardUiState]。
     *
     * 使用 copy() 更新，保留 NetworkStateMonitor 提供的 connectedSsid 和 networkMode。
     * 引擎的 connectedSsid 僅在引擎主動連線時才有值，
     * 而 NetworkStateMonitor 始終反映系統的實際連線狀態。
     */
    private fun collectEngineState() {
        viewModelScope.launch {
            smartSwitchEngine.getState().collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    smartSwitchEnabled = preferenceRepository.isSmartSwitchEnabled(),
                    hotspotState = engineState.hotspotState,
                    knownNetworksCount = engineState.knownNetworksCount,
                    isRunning = engineState.isRunning
                )
            }
        }
    }

    /**
     * 收集 [NetworkStateMonitor] 的狀態，直接反映當前網路連線狀態。
     *
     * 這確保即使智慧切換引擎未啟動，UI 仍能顯示正確的 WiFi 連線資訊。
     */
    private fun collectNetworkState() {
        viewModelScope.launch {
            networkStateMonitor.observeNetworkState().collect { networkState ->
                _uiState.value = _uiState.value.copy(
                    connectedSsid = networkState.wifiSsid,
                    networkMode = when {
                        networkState.isWifiConnected -> NetworkMode.WIFI_CONNECTED
                        networkState.isMobileDataConnected -> NetworkMode.MOBILE_DATA
                        else -> _uiState.value.networkMode
                    }
                )
            }
        }
    }

    /**
     * 切換智慧切換開關。
     *
     * 啟用時啟動引擎，停用時停止引擎。
     * 需求：4.1
     */
    fun toggleSmartSwitch() {
        val currentEnabled = preferenceRepository.isSmartSwitchEnabled()
        val newEnabled = !currentEnabled

        preferenceRepository.setSmartSwitchEnabled(newEnabled)

        if (newEnabled) {
            smartSwitchEngine.start()
        } else {
            smartSwitchEngine.stop()
        }

        // 立即更新 UI 狀態中的開關值
        _uiState.value = _uiState.value.copy(smartSwitchEnabled = newEnabled)
    }

    /**
     * 切換 Hotspot 狀態。
     *
     * 根據 [HotspotApiAdapter.getControlMode] 決定行為：
     * - [HotspotControlMode.DIRECT]：直接啟動/關閉 Hotspot
     * - [HotspotControlMode.GUIDED]：回傳 [HotspotResult.NeedUserAction]，由 UI 層跳轉系統設定
     *
     * 需求：2.1, 2.5
     *
     * @return Hotspot 操作結果
     */
    suspend fun toggleHotspot(): HotspotResult {
        val currentState = _uiState.value.hotspotState

        return if (currentState == HotspotState.ENABLED) {
            val result = hotspotApiAdapter.disableHotspot()
            if (result is HotspotResult.Success) {
                _uiState.value = _uiState.value.copy(hotspotState = HotspotState.DISABLED)
            }
            result
        } else {
            val result = hotspotApiAdapter.enableHotspot()
            if (result is HotspotResult.Success) {
                _uiState.value = _uiState.value.copy(hotspotState = HotspotState.ENABLED)
            }
            result
        }
    }
}
