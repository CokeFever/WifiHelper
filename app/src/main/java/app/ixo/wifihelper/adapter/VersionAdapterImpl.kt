package app.ixo.wifihelper.adapter

import android.Manifest
import android.os.Build
import app.ixo.wifihelper.model.HotspotControlMode
import app.ixo.wifihelper.model.WifiListStrategy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [VersionAdapter] 的實作類別。
 *
 * 根據 [Build.VERSION.SDK_INT] 偵測當前 Android 版本，並回傳對應的
 * Hotspot 控制模式、WiFi 清單取得策略與所需權限清單。
 *
 * 版本適配對照：
 * - Hotspot 控制：API 28-32 → DIRECT，API 33-36 → GUIDED
 * - WiFi 清單策略：API 28-29 → CONFIGURED_NETWORKS，API 30-36 → SCAN_AND_SUGGEST
 */
@Singleton
class VersionAdapterImpl @Inject constructor() : VersionAdapter {

    override fun getApiLevel(): Int = Build.VERSION.SDK_INT

    override fun getHotspotControlMode(): HotspotControlMode {
        return if (getApiLevel() <= 32) {
            HotspotControlMode.DIRECT
        } else {
            HotspotControlMode.GUIDED
        }
    }

    override fun getWifiListStrategy(): WifiListStrategy {
        return if (getApiLevel() <= 29) {
            WifiListStrategy.CONFIGURED_NETWORKS
        } else {
            WifiListStrategy.SCAN_AND_SUGGEST
        }
    }

    override fun getRequiredPermissions(): List<PermissionInfo> {
        val apiLevel = getApiLevel()
        return ALL_PERMISSIONS.filter { permission ->
            apiLevel >= permission.minApiLevel &&
                (permission.maxApiLevel == null || apiLevel <= permission.maxApiLevel)
        }
    }

    companion object {
        /**
         * 所有版本可能需要的權限完整清單。
         * 各權限透過 minApiLevel / maxApiLevel 限定適用範圍。
         */
        internal val ALL_PERMISSIONS = listOf(
            PermissionInfo(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "WiFi 掃描需要精確位置權限"
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                minApiLevel = 29,
                maxApiLevel = null,
                description = "背景 WiFi 掃描需要背景位置權限"
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_WIFI_STATE,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "讀取 WiFi 狀態"
            ),
            PermissionInfo(
                permission = Manifest.permission.CHANGE_WIFI_STATE,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "變更 WiFi 連線"
            ),
            PermissionInfo(
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "讀取網路狀態"
            ),
            PermissionInfo(
                permission = Manifest.permission.CHANGE_NETWORK_STATE,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "變更網路狀態"
            ),
            PermissionInfo(
                permission = Manifest.permission.FOREGROUND_SERVICE,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "前景服務運作"
            ),
            PermissionInfo(
                permission = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                minApiLevel = 34,
                maxApiLevel = null,
                description = "前景服務類型宣告（connectedDevice）"
            ),
            PermissionInfo(
                permission = "android.permission.POST_NOTIFICATIONS",
                minApiLevel = 33,
                maxApiLevel = null,
                description = "顯示通知權限"
            ),
            PermissionInfo(
                permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
                minApiLevel = 28,
                maxApiLevel = null,
                description = "接收開機廣播以自動啟動服務"
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_SETTINGS,
                minApiLevel = 28,
                maxApiLevel = 32,
                description = "Hotspot 直接控制所需權限"
            ),
            PermissionInfo(
                permission = "android.permission.NEARBY_WIFI_DEVICES",
                minApiLevel = 33,
                maxApiLevel = null,
                description = "WiFi 裝置探索權限"
            ),
            PermissionInfo(
                permission = "android.permission.SCHEDULE_EXACT_ALARM",
                minApiLevel = 31,
                maxApiLevel = null,
                description = "精確排程以確保定時任務準確執行"
            )
        )
    }
}
