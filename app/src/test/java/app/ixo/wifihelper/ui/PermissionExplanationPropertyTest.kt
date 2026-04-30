package app.ixo.wifihelper.ui

import app.ixo.wifihelper.ui.permission.PermissionHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.forAll

// Feature: auto-wifi-manager, Property 11: 權限拒絕顯示說明

/**
 * 屬性測試：權限拒絕顯示說明
 *
 * 對任意被拒絕的必要權限，驗證 App 顯示用途說明訊息且包含重新請求入口。
 *
 * 測試策略：
 * 1. 對 API 28-36 的每個等級，取得該等級所需的所有權限
 * 2. 對每個權限，驗證 [PermissionHandler.getExplanationForDeniedPermission] 回傳的
 *    [PermissionExplanation] 具有非空的 title、description，且 hasReRequestEntry 為 true
 *
 * **Validates: Requirements 5.4**
 */
class PermissionExplanationPropertyTest : FunSpec({

    // ── Arb 生成器 ─────────────────────────────────────────────────────────

    /** 支援的 API 等級範圍 */
    val arbApiLevel = Arb.int(28..36)

    // ── 屬性 11：權限拒絕顯示說明 ──────────────────────────────────────────

    test("Property 11: Every required permission has a non-empty explanation with re-request entry") {
        // **Validates: Requirements 5.4**
        //
        // 對任意 API 等級，取得該等級所需的所有權限，
        // 驗證每個權限都有非空的用途說明且包含重新請求入口。
        forAll(arbApiLevel) { apiLevel ->
            val handler = PermissionHandler(apiLevel = apiLevel)
            val requiredPermissions = handler.getRequiredPermissions()

            // 該 API 等級至少需要一些權限
            requiredPermissions.isNotEmpty() &&
                requiredPermissions.all { permissionInfo ->
                    val explanation = handler.getExplanationForDeniedPermission(permissionInfo.permission)

                    // 核心斷言：
                    // 1. 說明的權限名稱與請求的權限一致
                    explanation.permission == permissionInfo.permission &&
                        // 2. 標題非空
                        explanation.title.isNotEmpty() &&
                        // 3. 描述非空
                        explanation.description.isNotEmpty() &&
                        // 4. 包含重新請求入口
                        explanation.hasReRequestEntry
                }
        }
    }

    test("Property 11a: Explanation description contains re-request guidance text") {
        // **Validates: Requirements 5.4**
        //
        // 對任意 API 等級的任意必要權限，驗證說明文字中包含
        // 引導使用者重新授權的描述（如「重新授權」或「系統設定」）。
        forAll(arbApiLevel) { apiLevel ->
            val handler = PermissionHandler(apiLevel = apiLevel)
            val requiredPermissions = handler.getRequiredPermissions()

            requiredPermissions.all { permissionInfo ->
                val explanation = handler.getExplanationForDeniedPermission(permissionInfo.permission)

                // 說明文字應包含重新請求的引導
                explanation.description.contains("重新授權") ||
                    explanation.description.contains("系統設定")
            }
        }
    }

    test("Property 11b: API 33+ includes POST_NOTIFICATIONS and NEARBY_WIFI_DEVICES permissions") {
        // **Validates: Requirements 5.4**
        //
        // 對 API 33-36，驗證所需權限包含 POST_NOTIFICATIONS 與 NEARBY_WIFI_DEVICES，
        // 且這些權限都有對應的說明。
        forAll(Arb.int(33..36)) { apiLevel ->
            val handler = PermissionHandler(apiLevel = apiLevel)
            val requiredPermissions = handler.getRequiredPermissions()
            val permissionNames = requiredPermissions.map { it.permission }

            val hasPostNotifications = "android.permission.POST_NOTIFICATIONS" in permissionNames
            val hasNearbyWifi = "android.permission.NEARBY_WIFI_DEVICES" in permissionNames

            // 驗證這些 API 33+ 權限存在且有說明
            if (hasPostNotifications && hasNearbyWifi) {
                val postNotifExplanation = handler.getExplanationForDeniedPermission(
                    "android.permission.POST_NOTIFICATIONS"
                )
                val nearbyWifiExplanation = handler.getExplanationForDeniedPermission(
                    "android.permission.NEARBY_WIFI_DEVICES"
                )

                postNotifExplanation.title.isNotEmpty() &&
                    postNotifExplanation.description.isNotEmpty() &&
                    postNotifExplanation.hasReRequestEntry &&
                    nearbyWifiExplanation.title.isNotEmpty() &&
                    nearbyWifiExplanation.description.isNotEmpty() &&
                    nearbyWifiExplanation.hasReRequestEntry
            } else {
                false // These permissions should be present for API 33+
            }
        }
    }

    test("Property 11c: API 28-32 includes WRITE_SETTINGS but not POST_NOTIFICATIONS") {
        // **Validates: Requirements 5.4**
        //
        // 對 API 28-32，驗證所需權限包含 WRITE_SETTINGS（Hotspot 直接控制），
        // 但不包含 POST_NOTIFICATIONS（API 33+ 才需要）。
        forAll(Arb.int(28..32)) { apiLevel ->
            val handler = PermissionHandler(apiLevel = apiLevel)
            val requiredPermissions = handler.getRequiredPermissions()
            val permissionNames = requiredPermissions.map { it.permission }

            val hasWriteSettings = "android.permission.WRITE_SETTINGS" in permissionNames
            val hasPostNotifications = "android.permission.POST_NOTIFICATIONS" in permissionNames

            // WRITE_SETTINGS 應存在，POST_NOTIFICATIONS 不應存在
            hasWriteSettings && !hasPostNotifications
        }
    }
})
