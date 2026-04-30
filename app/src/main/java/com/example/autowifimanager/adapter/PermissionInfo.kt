package com.example.autowifimanager.adapter

/**
 * 權限資訊資料類別：定義各版本所需的權限及其適用 API 範圍。
 *
 * @property permission Android 權限名稱（例如 Manifest.permission.ACCESS_FINE_LOCATION）
 * @property minApiLevel 此權限所需的最低 API 等級
 * @property maxApiLevel 此權限所需的最高 API 等級（null 表示無上限）
 * @property description 權限用途的使用者可讀說明
 */
data class PermissionInfo(
    val permission: String,
    val minApiLevel: Int,
    val maxApiLevel: Int?,
    val description: String
)
