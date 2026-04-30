package com.example.autowifimanager.adapter

import com.example.autowifimanager.model.HotspotControlMode
import com.example.autowifimanager.model.WifiListStrategy

/**
 * 版本適配器介面：偵測 Android 版本並提供功能能力查詢。
 *
 * 負責在背景偵測當前裝置的 Android API 等級，並根據版本決定各功能模組
 * 應使用的控制模式與策略，使上層邏輯不需直接依賴 Android API 版本。
 */
interface VersionAdapter {

    /** 取得當前裝置的 API 等級 */
    fun getApiLevel(): Int

    /**
     * 查詢 Hotspot 控制模式。
     *
     * - API 28-32 → [HotspotControlMode.DIRECT]（可透過反射直接控制）
     * - API 33-36 → [HotspotControlMode.GUIDED]（需引導至系統設定）
     */
    fun getHotspotControlMode(): HotspotControlMode

    /**
     * 查詢 WiFi 清單取得策略。
     *
     * - API 28-29 → [WifiListStrategy.CONFIGURED_NETWORKS]（使用 getConfiguredNetworks()）
     * - API 30-36 → [WifiListStrategy.SCAN_AND_SUGGEST]（使用 getScanResults() + WifiNetworkSuggestion）
     */
    fun getWifiListStrategy(): WifiListStrategy

    /**
     * 查詢當前版本所需的權限清單。
     *
     * 回傳的權限清單會根據當前 API 等級過濾，僅包含該版本需要的權限。
     */
    fun getRequiredPermissions(): List<PermissionInfo>
}
