package com.example.autowifimanager.core

import com.example.autowifimanager.model.SmartSwitchState
import kotlinx.coroutines.flow.StateFlow

/**
 * 智慧切換引擎介面：協調 WiFi 連線與 Hotspot 切換的核心業務邏輯。
 *
 * 引擎根據使用者偏好設定、行動網路狀態與 WiFi 訊號強度，
 * 自動決定是否切換至 WiFi 連線或恢復 Hotspot。
 *
 * 需求：3.4, 3.6, 3.7, 4.1, 4.3, 4.4, 4.5
 */
interface SmartSwitchEngine {

    /**
     * 啟動智慧切換引擎。
     *
     * 開始每 30 秒的背景掃描週期，根據網路狀態與偏好設定
     * 自動執行 WiFi 連線或 Hotspot 切換。
     */
    fun start()

    /**
     * 停止智慧切換引擎。
     *
     * 取消所有背景掃描與切換任務，維持當前連線狀態不變。
     */
    fun stop()

    /**
     * 取得當前引擎狀態的 [StateFlow]。
     *
     * 上層元件（如 ViewModel、Foreground Service）可透過此 Flow
     * 觀察引擎狀態變化並更新 UI 或通知。
     */
    fun getState(): StateFlow<SmartSwitchState>

    /**
     * 手動排除某 SSID，使其在當次執行期間不被自動連線。
     *
     * @param ssid 要排除的 WiFi 網路 SSID
     */
    fun excludeSsid(ssid: String)

    /**
     * 重置所有手動排除的 SSID。
     */
    fun resetExclusions()
}
