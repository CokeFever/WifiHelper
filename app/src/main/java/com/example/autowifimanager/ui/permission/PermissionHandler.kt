package com.example.autowifimanager.ui.permission

import android.Manifest
import android.os.Build
import com.example.autowifimanager.adapter.PermissionInfo

/**
 * 權限說明資料類別：描述被拒絕權限的用途說明與重新請求入口。
 *
 * @property permission Android 權限名稱
 * @property title 權限的簡短標題（中文）
 * @property description 權限用途的詳細說明（中文），包含重新請求入口描述
 * @property hasReRequestEntry 是否包含重新請求權限的入口
 */
data class PermissionExplanation(
    val permission: String,
    val title: String,
    val description: String,
    val hasReRequestEntry: Boolean = true
)

/**
 * 權限請求與說明流程處理器。
 *
 * 負責：
 * 1. 定義所有必要權限及其中文說明
 * 2. 根據當前 API 等級取得所需權限清單
 * 3. 當權限被拒絕時，提供用途說明訊息與重新請求入口
 * 4. 處理 API 版本差異的權限請求（如 API 33+ 的 POST_NOTIFICATIONS、NEARBY_WIFI_DEVICES）
 *
 * 此類別不依賴 Android Context，方便進行單元測試與屬性測試。
 * 透過建構子參數 [apiLevel] 注入 API 等級，預設使用 [Build.VERSION.SDK_INT]。
 *
 * _需求：5.3, 5.4_
 */
class PermissionHandler(
    private val apiLevel: Int = Build.VERSION.SDK_INT
) {

    /**
     * 取得當前 API 等級所需的所有權限資訊清單。
     *
     * 根據 [apiLevel] 過濾，僅回傳該版本需要的權限。
     */
    fun getRequiredPermissions(): List<PermissionInfo> {
        return ALL_PERMISSIONS.filter { info ->
            apiLevel >= info.minApiLevel &&
                (info.maxApiLevel == null || apiLevel <= info.maxApiLevel)
        }
    }

    /**
     * 取得被拒絕權限的用途說明。
     *
     * 對任意被拒絕的必要權限，回傳包含用途說明訊息與重新請求入口的 [PermissionExplanation]。
     * 若該權限不在已知清單中，回傳通用說明。
     *
     * @param permission 被拒絕的權限名稱
     * @return 該權限的用途說明，包含重新請求入口
     */
    fun getExplanationForDeniedPermission(permission: String): PermissionExplanation {
        return PERMISSION_EXPLANATIONS[permission]
            ?: PermissionExplanation(
                permission = permission,
                title = "必要權限",
                description = "此權限為 App 正常運作所必需。請點擊下方按鈕重新授予權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            )
    }

    /**
     * 取得所有被拒絕權限的用途說明清單。
     *
     * @param deniedPermissions 被拒絕的權限名稱清單
     * @return 每個被拒絕權限的用途說明
     */
    fun getExplanationsForDeniedPermissions(
        deniedPermissions: List<String>
    ): List<PermissionExplanation> {
        return deniedPermissions.map { getExplanationForDeniedPermission(it) }
    }

    companion object {

        /**
         * 所有版本可能需要的權限完整清單，含 API 範圍限制。
         * 與 [VersionAdapterImpl.ALL_PERMISSIONS] 保持一致。
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

        /**
         * 權限用途說明對照表（中文）。
         *
         * 每個說明包含：
         * - 權限用途的詳細描述
         * - 重新請求權限的入口描述
         *
         * 所有說明的 [PermissionExplanation.hasReRequestEntry] 均為 true，
         * 確保使用者可以重新授予權限。
         */
        internal val PERMISSION_EXPLANATIONS: Map<String, PermissionExplanation> = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to PermissionExplanation(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "精確位置權限",
                description = "WiFi 掃描功能需要精確位置權限才能偵測周圍的 WiFi 網路。" +
                    "沒有此權限，App 將無法自動搜尋並連線至已知的 WiFi 網路。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定 > 應用程式 > WiFi Helper > 權限中手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to PermissionExplanation(
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                title = "背景位置權限",
                description = "背景 WiFi 掃描需要背景位置權限，以便在 App 未開啟時仍能自動偵測 WiFi 網路。" +
                    "沒有此權限，智慧切換功能將無法在背景運作。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定 > 應用程式 > WiFi Helper > 權限 > 位置中選擇「一律允許」。",
                hasReRequestEntry = true
            ),
            Manifest.permission.ACCESS_WIFI_STATE to PermissionExplanation(
                permission = Manifest.permission.ACCESS_WIFI_STATE,
                title = "WiFi 狀態讀取權限",
                description = "App 需要讀取 WiFi 狀態以監控目前的連線情況與訊號強度。" +
                    "沒有此權限，App 將無法顯示 WiFi 連線狀態。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.CHANGE_WIFI_STATE to PermissionExplanation(
                permission = Manifest.permission.CHANGE_WIFI_STATE,
                title = "WiFi 狀態變更權限",
                description = "App 需要變更 WiFi 狀態以執行自動連線與中斷操作。" +
                    "沒有此權限，智慧切換功能將無法自動連線至 WiFi 網路。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.ACCESS_NETWORK_STATE to PermissionExplanation(
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                title = "網路狀態讀取權限",
                description = "App 需要讀取網路狀態以偵測行動數據（4G/5G）的連線情況。" +
                    "沒有此權限，App 將無法判斷是否需要切換至 WiFi 連線。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.CHANGE_NETWORK_STATE to PermissionExplanation(
                permission = Manifest.permission.CHANGE_NETWORK_STATE,
                title = "網路狀態變更權限",
                description = "App 需要變更網路狀態以在 WiFi 與行動數據之間進行切換。" +
                    "沒有此權限，智慧切換功能將無法正常運作。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.FOREGROUND_SERVICE to PermissionExplanation(
                permission = Manifest.permission.FOREGROUND_SERVICE,
                title = "前景服務權限",
                description = "App 需要前景服務權限以在背景持續執行智慧切換功能。" +
                    "沒有此權限，App 的背景自動化功能將無法運作。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" to PermissionExplanation(
                permission = "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
                title = "前景服務裝置連線權限",
                description = "Android 14 以上需要此權限以宣告前景服務的裝置連線類型。" +
                    "沒有此權限，App 的背景服務將無法正常啟動。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            "android.permission.POST_NOTIFICATIONS" to PermissionExplanation(
                permission = "android.permission.POST_NOTIFICATIONS",
                title = "通知權限",
                description = "Android 13 以上需要此權限才能顯示通知。" +
                    "App 使用通知來顯示目前的運作狀態（WiFi 連線中、Hotspot 啟用中等）。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定 > 應用程式 > WiFi Helper > 通知中手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.RECEIVE_BOOT_COMPLETED to PermissionExplanation(
                permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
                title = "開機廣播權限",
                description = "App 需要接收開機廣播以在裝置開機後自動啟動背景服務。" +
                    "沒有此權限，「開機自動啟動」功能將無法運作。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            ),
            Manifest.permission.WRITE_SETTINGS to PermissionExplanation(
                permission = Manifest.permission.WRITE_SETTINGS,
                title = "系統設定寫入權限",
                description = "在 Android 12 以下版本，App 需要此權限以直接控制 WiFi 熱點的啟動與關閉。" +
                    "沒有此權限，熱點操作功能將無法使用。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定 > 應用程式 > WiFi Helper > 進階中手動開啟。",
                hasReRequestEntry = true
            ),
            "android.permission.NEARBY_WIFI_DEVICES" to PermissionExplanation(
                permission = "android.permission.NEARBY_WIFI_DEVICES",
                title = "鄰近 WiFi 裝置權限",
                description = "Android 13 以上需要此權限以探索鄰近的 WiFi 裝置與網路。" +
                    "沒有此權限，App 將無法掃描周圍的 WiFi 網路。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定 > 應用程式 > WiFi Helper > 權限中手動開啟。",
                hasReRequestEntry = true
            ),
            "android.permission.SCHEDULE_EXACT_ALARM" to PermissionExplanation(
                permission = "android.permission.SCHEDULE_EXACT_ALARM",
                title = "精確排程權限",
                description = "Android 12 以上需要此權限以確保定時掃描任務能準確執行。" +
                    "沒有此權限，WiFi 掃描的定時精確度可能降低。" +
                    "請點擊「重新授權」按鈕授予此權限，或前往系統設定手動開啟。",
                hasReRequestEntry = true
            )
        )
    }
}
