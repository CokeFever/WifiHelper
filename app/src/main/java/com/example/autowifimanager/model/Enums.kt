package com.example.autowifimanager.model

/**
 * Hotspot 控制模式：根據 Android API 等級決定使用直接控制或引導控制。
 */
enum class HotspotControlMode {
    /** API 28-32：可透過反射直接控制 */
    DIRECT,
    /** API 33+：需引導至系統設定 */
    GUIDED
}

/**
 * WiFi 清單取得策略：根據 Android API 等級決定取得已知網路的方式。
 */
enum class WifiListStrategy {
    /** API 28-29：使用 getConfiguredNetworks() */
    CONFIGURED_NETWORKS,
    /** API 30+：使用 getScanResults() + WifiNetworkSuggestion */
    SCAN_AND_SUGGEST
}

/**
 * Hotspot 狀態。
 */
enum class HotspotState {
    ENABLED,
    DISABLED,
    UNKNOWN
}

/**
 * 網路模式：表示當前的網路連線狀態。
 */
enum class NetworkMode {
    /** 已連線至 WiFi */
    WIFI_CONNECTED,
    /** Hotspot 啟用中 */
    HOTSPOT_ACTIVE,
    /** 使用行動數據 */
    MOBILE_DATA,
    /** 無連線 */
    DISCONNECTED,
    /** 切換中 */
    SWITCHING
}

/**
 * WiFi 安全類型。
 */
enum class SecurityType {
    OPEN,
    WEP,
    WPA_PSK,
    WPA2_PSK,
    WPA3_SAE,
    UNKNOWN
}

/**
 * WiFi 連線失敗原因。
 */
enum class ConnectionFailureReason {
    NETWORK_NOT_FOUND,
    AUTHENTICATION_FAILED,
    TIMEOUT,
    UNKNOWN
}
