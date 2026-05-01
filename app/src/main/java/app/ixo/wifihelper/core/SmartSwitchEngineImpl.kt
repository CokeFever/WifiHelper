package app.ixo.wifihelper.core

import android.util.Log
import app.ixo.wifihelper.BuildConfig
import app.ixo.wifihelper.adapter.HotspotApiAdapter
import app.ixo.wifihelper.adapter.WifiApiAdapter
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.model.ConnectionResult
import app.ixo.wifihelper.model.HotspotResult
import app.ixo.wifihelper.model.HotspotState
import app.ixo.wifihelper.model.NetworkMode
import app.ixo.wifihelper.model.SmartSwitchState
import app.ixo.wifihelper.util.NetworkSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SmartSwitchEngine] 的實作類別。
 *
 * 注入 [WifiApiAdapter]、[HotspotApiAdapter]、[NetworkStateMonitor]、[PreferenceRepository]，
 * 透過 30 秒掃描週期執行核心決策邏輯，自動在 WiFi 連線與 Hotspot 之間切換。
 *
 * 核心決策規則（需求 3.6, 3.7, 4.3, 4.4, 4.5）：
 * - smartSwitchEnabled = false → 不產生任何切換動作
 * - smartSwitchEnabled = true 且 mobileDataAvailable = false 且 bestKnownWifiRssi > signalThreshold → 關閉 Hotspot，連線 WiFi
 * - smartSwitchEnabled = true 且 mobileDataAvailable = true 且 bestKnownWifiRssi ≤ signalThreshold → 維持行動數據，啟用 Hotspot
 * - smartSwitchEnabled = true 且 mobileDataAvailable = true 且當前為 WiFi 連線 → 中斷 WiFi，恢復 Hotspot
 */
@Singleton
class SmartSwitchEngineImpl @Inject constructor(
    private val wifiApiAdapter: WifiApiAdapter,
    private val hotspotApiAdapter: HotspotApiAdapter,
    private val networkStateMonitor: NetworkStateMonitor,
    private val preferenceRepository: PreferenceRepository
) : SmartSwitchEngine {

    companion object {
        private const val TAG = "SmartSwitchEngine"

        /** 掃描週期間隔（毫秒） */
        const val SCAN_INTERVAL_MS = 30_000L

        /** 連線失敗後的重試等待時間（毫秒）：60 秒 */
        const val RETRY_WAIT_MS = 60_000L

        /** 連續失敗封鎖門檻 */
        const val FAILURE_BLOCK_THRESHOLD = 3
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    /** 手動排除的 SSID 集合（當次執行期間有效） */
    private val excludedSsids = mutableSetOf<String>()

    /** SSID 連線失敗計數器 */
    private val failureCounters = mutableMapOf<String, Int>()

    /** 被封鎖的 SSID 集合（連續 3 次失敗） */
    private val blockedSsids = mutableSetOf<String>()

    /** SSID 最後一次連線失敗的時間戳（毫秒） */
    private val lastFailureTime = mutableMapOf<String, Long>()

    /**
     * 時間提供者，用於取得當前時間。
     * 預設使用 [System.currentTimeMillis]，可在測試中替換。
     */
    internal var timeProvider: () -> Long = { System.currentTimeMillis() }

    private val _state = MutableStateFlow(
        SmartSwitchState(
            isRunning = false,
            currentMode = NetworkMode.DISCONNECTED,
            lastScanTime = 0L,
            connectedSsid = null,
            hotspotState = HotspotState.UNKNOWN,
            mobileDataAvailable = false,
            knownNetworksCount = 0,
            failedAttempts = emptyMap()
        )
    )

    override fun getState(): StateFlow<SmartSwitchState> = _state.asStateFlow()

    override fun start() {
        if (scanJob?.isActive == true) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Engine already running, ignoring start()")
            return
        }

        Log.i(TAG, "Starting SmartSwitchEngine")
        updateState { copy(isRunning = true) }

        scanJob = scope.launch {
            while (isActive) {
                try {
                    executeScanCycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during scan cycle", e)
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping SmartSwitchEngine")
        scanJob?.cancel()
        scanJob = null
        updateState {
            copy(
                isRunning = false,
                currentMode = NetworkMode.DISCONNECTED
            )
        }
    }

    override fun excludeSsid(ssid: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Excluding SSID: $ssid")
        excludedSsids.add(ssid)
    }

    override fun resetExclusions() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Resetting all SSID exclusions")
        excludedSsids.clear()
    }

    /**
     * 核心決策函式：根據輸入狀態決定切換動作。
     *
     * 此函式為純邏輯函式（不依賴外部狀態），方便獨立測試。
     *
     * 決策規則：
     * 1. smartSwitchEnabled = false → [SwitchDecision.NO_ACTION]
     * 2. mobileDataAvailable = false 且 bestKnownWifiRssi > signalThreshold → [SwitchDecision.CONNECT_WIFI]
     * 3. mobileDataAvailable = true 且 currentMode = WIFI_CONNECTED → [SwitchDecision.RESTORE_HOTSPOT]
     * 4. mobileDataAvailable = true 且 bestKnownWifiRssi ≤ signalThreshold（或無 WiFi） → [SwitchDecision.RESTORE_HOTSPOT]
     * 5. 其他情況 → [SwitchDecision.MAINTAIN_CURRENT]
     *
     * @param smartSwitchEnabled 智慧切換是否啟用
     * @param mobileDataAvailable 行動數據是否可用
     * @param bestKnownWifiRssi 最佳已知 WiFi 的 RSSI 值，null 表示無可用 WiFi
     * @param signalThreshold 訊號強度門檻（dBm）
     * @param currentMode 當前網路模式
     * @return 決策結果
     */
    fun makeDecision(
        smartSwitchEnabled: Boolean,
        mobileDataAvailable: Boolean,
        bestKnownWifiRssi: Int?,
        signalThreshold: Int,
        currentMode: NetworkMode
    ): SwitchDecision {
        // Rule 1: 智慧切換停用 → 不動作
        if (!smartSwitchEnabled) {
            return SwitchDecision.NO_ACTION
        }

        // Rule 3: 行動數據可用且當前為 WiFi 連線 → 中斷 WiFi，恢復 Hotspot
        if (mobileDataAvailable && currentMode == NetworkMode.WIFI_CONNECTED) {
            return SwitchDecision.RESTORE_HOTSPOT
        }

        // Rule 2a: 已經連上 WiFi 且訊號良好 → 維持當前狀態
        if (currentMode == NetworkMode.WIFI_CONNECTED && bestKnownWifiRssi != null && bestKnownWifiRssi > signalThreshold) {
            return SwitchDecision.MAINTAIN_CURRENT
        }

        // Rule 2b: 行動數據不可用且有良好 WiFi 且尚未連線 → 關閉 Hotspot，連線 WiFi
        if (!mobileDataAvailable && bestKnownWifiRssi != null && bestKnownWifiRssi > signalThreshold) {
            return SwitchDecision.CONNECT_WIFI
        }

        // Rule 4: 行動數據可用且 WiFi 訊號不足 → 維持行動數據，啟用 Hotspot
        if (mobileDataAvailable && (bestKnownWifiRssi == null || bestKnownWifiRssi <= signalThreshold)) {
            return SwitchDecision.RESTORE_HOTSPOT
        }

        // 其他情況：維持當前狀態
        return SwitchDecision.MAINTAIN_CURRENT
    }

    /**
     * 執行一次掃描週期：掃描 WiFi、收集狀態、做出決策、執行動作。
     */
    private suspend fun executeScanCycle() {
        val smartSwitchEnabled = preferenceRepository.isSmartSwitchEnabled()
        val signalThreshold = preferenceRepository.getSignalThreshold()
        val mobileDataAvailable = networkStateMonitor.isMobileDataAvailable()

        // 更新行動數據狀態
        updateState { copy(mobileDataAvailable = mobileDataAvailable) }

        if (!smartSwitchEnabled) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Smart switch disabled, skipping scan cycle")
            updateState { copy(lastScanTime = System.currentTimeMillis()) }
            return
        }

        // 更新狀態為掃描中
        updateState { copy(currentMode = NetworkMode.SWITCHING) }

        // 掃描 WiFi 並取得已知網路
        val knownNetworks = try {
            wifiApiAdapter.getKnownNetworks()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get known networks", e)
            emptyList()
        }

        // 合併排除清單：手動排除 + 封鎖的 SSID + 重試等待中的 SSID
        val retryWaitSsids = getRetryWaitSsids()
        val allExcluded = excludedSsids + blockedSsids + retryWaitSsids

        // 使用 NetworkSelector 選擇最佳網路
        val bestNetwork = NetworkSelector.selectBestNetwork(
            networks = knownNetworks,
            signalThreshold = signalThreshold,
            excludedSsids = allExcluded
        )

        val bestRssi = bestNetwork?.rssi
        val currentMode = determineCurrentMode()

        // 更新狀態
        updateState {
            copy(
                lastScanTime = System.currentTimeMillis(),
                knownNetworksCount = knownNetworks.size,
                failedAttempts = failureCounters.toMap()
            )
        }

        // 做出決策
        val decision = makeDecision(
            smartSwitchEnabled = smartSwitchEnabled,
            mobileDataAvailable = mobileDataAvailable,
            bestKnownWifiRssi = bestRssi,
            signalThreshold = signalThreshold,
            currentMode = currentMode
        )

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Decision: $decision (mobile=$mobileDataAvailable, bestRssi=$bestRssi, " +
                    "threshold=$signalThreshold, mode=$currentMode)"
            )
        }

        // 執行決策
        executeDecision(decision, bestNetwork?.ssid)
    }

    /**
     * 根據決策結果執行對應的網路操作。
     */
    private suspend fun executeDecision(decision: SwitchDecision, targetSsid: String?) {
        when (decision) {
            SwitchDecision.CONNECT_WIFI -> {
                if (targetSsid == null) {
                    Log.w(TAG, "CONNECT_WIFI decision but no target SSID")
                    return
                }
                connectToWifi(targetSsid)
            }

            SwitchDecision.RESTORE_HOTSPOT -> {
                restoreHotspot()
            }

            SwitchDecision.NO_ACTION -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "No action required")
            }

            SwitchDecision.MAINTAIN_CURRENT -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "Maintaining current state")
            }
        }
    }

    /**
     * 執行 WiFi 連線：先關閉 Hotspot，再連線至目標網路。
     */
    private suspend fun connectToWifi(targetSsid: String) {
        Log.i(TAG, "Connecting to WiFi: $targetSsid")
        updateState { copy(currentMode = NetworkMode.SWITCHING) }

        // 先關閉 Hotspot
        try {
            hotspotApiAdapter.disableHotspot()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable hotspot before WiFi connection", e)
        }

        // 取得目標網路的完整資訊
        val knownNetworks = wifiApiAdapter.getKnownNetworks()
        val targetNetwork = knownNetworks.firstOrNull { it.ssid == targetSsid }

        if (targetNetwork == null) {
            Log.w(TAG, "Target network $targetSsid not found in known networks")
            recordFailure(targetSsid)
            updateState { copy(currentMode = NetworkMode.DISCONNECTED) }
            return
        }

        // 嘗試連線
        val result = wifiApiAdapter.connectToNetwork(targetNetwork)
        when (result) {
            is ConnectionResult.Success -> {
                Log.i(TAG, "Successfully connected to WiFi: $targetSsid")
                // 重置該 SSID 的失敗計數與失敗時間戳
                failureCounters.remove(targetSsid)
                lastFailureTime.remove(targetSsid)
                updateState {
                    copy(
                        currentMode = NetworkMode.WIFI_CONNECTED,
                        connectedSsid = targetSsid
                    )
                }
            }

            is ConnectionResult.Failure -> {
                Log.w(TAG, "Failed to connect to WiFi: $targetSsid, reason=${result.reason}")
                recordFailure(targetSsid)
                updateState { copy(currentMode = NetworkMode.DISCONNECTED) }
            }
        }
    }

    /**
     * 恢復 Hotspot：先中斷 WiFi，再啟動 Hotspot。
     */
    private suspend fun restoreHotspot() {
        Log.i(TAG, "Restoring hotspot")
        updateState { copy(currentMode = NetworkMode.SWITCHING) }

        // 先中斷 WiFi
        try {
            wifiApiAdapter.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect WiFi before hotspot restore", e)
        }

        // 啟動 Hotspot
        val result = hotspotApiAdapter.enableHotspot()
        when (result) {
            is HotspotResult.Success -> {
                Log.i(TAG, "Hotspot restored successfully")
                updateState {
                    copy(
                        currentMode = NetworkMode.HOTSPOT_ACTIVE,
                        connectedSsid = null,
                        hotspotState = HotspotState.ENABLED
                    )
                }
            }

            is HotspotResult.NeedUserAction -> {
                Log.i(TAG, "Hotspot requires user action (guided mode)")
                updateState {
                    copy(
                        currentMode = NetworkMode.MOBILE_DATA,
                        connectedSsid = null,
                        hotspotState = HotspotState.UNKNOWN
                    )
                }
            }

            is HotspotResult.Failure -> {
                Log.e(TAG, "Failed to restore hotspot: ${result.reason}")
                updateState {
                    copy(
                        currentMode = NetworkMode.MOBILE_DATA,
                        connectedSsid = null,
                        hotspotState = HotspotState.DISABLED
                    )
                }
            }
        }
    }

    /**
     * 記錄 SSID 連線失敗，連續 3 次失敗則封鎖該 SSID。
     * 同時記錄失敗時間戳，用於 60 秒重試等待。
     */
    private fun recordFailure(ssid: String) {
        val count = (failureCounters[ssid] ?: 0) + 1
        failureCounters[ssid] = count
        lastFailureTime[ssid] = timeProvider()
        if (BuildConfig.DEBUG) Log.d(TAG, "Failure recorded for $ssid: $count consecutive failures")

        if (count >= FAILURE_BLOCK_THRESHOLD) {
            blockedSsids.add(ssid)
            Log.w(TAG, "SSID blocked after $count consecutive failures: $ssid")
        }
    }

    /**
     * 取得目前處於重試等待期間（60 秒內曾失敗）的 SSID 集合。
     * 已被封鎖的 SSID 不包含在此集合中（它們已在 blockedSsids 中處理）。
     */
    internal fun getRetryWaitSsids(): Set<String> {
        val now = timeProvider()
        return lastFailureTime
            .filter { (ssid, failTime) ->
                ssid !in blockedSsids && (now - failTime) < RETRY_WAIT_MS
            }
            .keys
    }

    /**
     * 判斷當前網路模式。
     */
    private suspend fun determineCurrentMode(): NetworkMode {
        val networkState = networkStateMonitor.observeNetworkState().value

        return when {
            networkState.isWifiConnected -> NetworkMode.WIFI_CONNECTED
            hotspotApiAdapter.getHotspotState() == HotspotState.ENABLED -> NetworkMode.HOTSPOT_ACTIVE
            networkState.isMobileDataConnected -> NetworkMode.MOBILE_DATA
            else -> NetworkMode.DISCONNECTED
        }
    }

    /**
     * 以 copy 方式更新 [_state]，確保 StateFlow 發出新值。
     */
    private inline fun updateState(transform: SmartSwitchState.() -> SmartSwitchState) {
        _state.value = _state.value.transform()
    }
}
