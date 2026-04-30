# 實作計畫：Auto WiFi Manager

## 概述

本計畫將 Auto WiFi Manager 的設計拆解為可逐步實作的編碼任務。每個任務建立在前一個任務的基礎上，從專案結構與核心介面開始，逐步實作各功能模組，最終整合所有元件。使用 Kotlin 語言，搭配 Hilt 依賴注入、Kotest 屬性測試、JUnit 5 單元測試。

## Tasks

- [x] 1. 建立 Android 專案結構與基礎設定
  - [x] 1.1 建立 Android 專案骨架與 Gradle 設定
    - 建立標準 Android 專案目錄結構（app/src/main, app/src/test, app/src/androidTest）
    - 設定 build.gradle.kts：compileSdk=36, targetSdk=35, minSdk=28
    - 加入依賴：Hilt, Kotlin Coroutines, WorkManager, Kotest, Mockk, JUnit 5, Turbine
    - 設定 Kotest property testing 模組
    - _需求：8.1, 8.2, 8.3_

  - [x] 1.2 設定 AndroidManifest.xml 與權限宣告
    - 宣告所有必要權限（ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_NETWORK_STATE, CHANGE_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, WRITE_SETTINGS, NEARBY_WIFI_DEVICES）
    - 為每項權限加入 `<uses-permission>` 與適當的 `maxSdkVersion` 限制
    - 宣告 Foreground Service 的 `foregroundServiceType="connectedDevice"`
    - _需求：5.3, 8.4_

  - [x] 1.3 定義核心資料模型與列舉型別
    - 建立 `model/` 套件，定義 `HotspotControlMode`, `WifiListStrategy`, `HotspotState`, `HotspotResult`, `NetworkMode`, `SecurityType`, `ConnectionResult`, `ConnectionFailureReason`
    - 建立 `KnownWifiNetwork`, `ScanResultInfo`, `WifiConnectionInfo`, `SmartSwitchState`, `NetworkState`, `UserPreferences` 資料類別
    - _需求：1.3, 3.1, 3.2_

- [x] 2. 實作版本適配層（Version Adapter Layer）
  - [x] 2.1 實作 VersionAdapter 介面與實作類別
    - 建立 `adapter/VersionAdapter.kt` 介面
    - 建立 `adapter/VersionAdapterImpl.kt` 實作，根據 `Build.VERSION.SDK_INT` 回傳對應的 `HotspotControlMode` 與 `WifiListStrategy`
    - 建立 `adapter/PermissionInfo.kt` 資料類別，定義各版本所需權限清單
    - 使用 Hilt `@Singleton` 註冊
    - _需求：1.1, 1.2, 1.3_

  - [x] 2.2 撰寫屬性測試：版本適配映射正確性
    - **屬性 1：版本適配映射正確性**
    - 對 API 28-36 的所有值，驗證 HotspotControlMode 與 WifiListStrategy 的映射正確
    - **驗證需求：1.2, 1.3, 2.2, 2.3, 3.1, 3.2**

  - [x] 2.3 撰寫單元測試：VersionAdapter 邊界條件
    - 測試 API 28（最低版本）與 API 36（最高版本）的行為
    - 測試 API 32 → 33 的 Hotspot 模式切換邊界
    - 測試 API 29 → 30 的 WiFi 策略切換邊界
    - _需求：1.2, 1.3_

- [x] 3. 實作偏好設定儲存庫（Preference Repository）
  - [x] 3.1 實作 PreferenceRepository 介面與實作類別
    - 建立 `data/PreferenceRepository.kt` 介面
    - 建立 `data/PreferenceRepositoryImpl.kt`，使用 EncryptedSharedPreferences 儲存
    - 實作 `validateIntegrity()` 方法：偵測無效型別、缺失鍵值、超出範圍數值
    - 實作 `resetToDefaults()` 方法
    - 使用 StateFlow 暴露偏好設定變更
    - 使用 Hilt `@Singleton` 註冊
    - _需求：4.7, 7.1, 7.3_

  - [x] 3.2 撰寫屬性測試：偏好設定持久化往返
    - **屬性 8：偏好設定持久化往返**
    - 對任意合法 UserPreferences（signalThreshold 在 [-100, -30] 範圍），驗證儲存後讀取值相同
    - **驗證需求：4.7**

  - [x] 3.3 撰寫屬性測試：損毀偏好設定重置為預設值
    - **屬性 9：損毀偏好設定重置為預設值**
    - 對任意損毀狀態（無效型別、缺失鍵值、超出範圍數值），驗證 validateIntegrity() 偵測到損毀且後續讀取回傳預設值
    - **驗證需求：7.3**

  - [x] 3.4 撰寫單元測試：PreferenceRepository 邊界條件
    - 測試 signalThreshold 邊界值（-100, -30）
    - 測試首次啟動時的預設值
    - 測試 resetToDefaults() 行為
    - _需求：4.7, 7.1, 7.3_

- [x] 4. 檢查點 - 確認基礎層測試通過
  - 確保所有測試通過，如有問題請詢問使用者。

- [x] 5. 實作網路狀態監控（Network State Monitor）
  - [x] 5.1 實作 NetworkStateMonitor 介面與實作類別
    - 建立 `core/NetworkStateMonitor.kt` 介面
    - 建立 `core/NetworkStateMonitorImpl.kt`，使用 `ConnectivityManager.NetworkCallback` 監聽網路變化
    - 透過 StateFlow 發布 `NetworkState` 變更
    - 實作 `isMobileDataAvailable()`, `isWifiEnabled()`, `getCurrentWifiRssi()` 方法
    - 使用 Hilt `@Singleton` 註冊
    - _需求：3.6, 3.7, 4.3, 4.4_

  - [x] 5.2 撰寫單元測試：NetworkStateMonitor
    - 使用 Mockk 模擬 ConnectivityManager
    - 測試行動網路連線/斷線狀態偵測
    - 測試 WiFi 連線/斷線狀態偵測
    - 測試 RSSI 值讀取
    - _需求：3.6, 3.7_

- [x] 6. 實作 WiFi API 適配器（WiFi Api Adapter）
  - [x] 6.1 實作 WifiApiAdapter 介面與版本分支實作
    - 建立 `adapter/WifiApiAdapter.kt` 介面
    - 建立 `adapter/WifiApiAdapterLegacy.kt`（API 28-29）：使用 `getConfiguredNetworks()` + `enableNetwork()`
    - 建立 `adapter/WifiApiAdapterModern.kt`（API 30+）：使用 `getScanResults()` + `WifiNetworkSuggestion`
    - 建立 Hilt Module 根據 API 等級提供對應實作
    - _需求：3.1, 3.2, 3.3_

  - [x] 6.2 實作 WiFi 網路選擇邏輯
    - 在 WifiApiAdapter 實作中加入最佳網路選擇演算法：從超過門檻的已知網路中選擇 RSSI 最高者
    - 實作 `startScan()` 方法觸發 WiFi 掃描
    - _需求：3.5_

  - [x] 6.3 撰寫屬性測試：最佳網路選擇
    - **屬性 3：最佳網路選擇**
    - 對任意已知 WiFi 網路清單與任意門檻值，驗證選擇結果為超過門檻的最強訊號網路；若無超過門檻者則不連線
    - **驗證需求：3.5**

  - [x] 6.4 撰寫單元測試：WifiApiAdapter
    - 測試空掃描結果
    - 測試所有網路都低於門檻
    - 測試多個網路相同 RSSI
    - _需求：3.1, 3.2, 3.5_

- [x] 7. 實作 Hotspot API 適配器（Hotspot Api Adapter）
  - [x] 7.1 實作 HotspotApiAdapter 介面與版本分支實作
    - 建立 `adapter/HotspotApiAdapter.kt` 介面
    - 建立 `adapter/HotspotApiAdapterDirect.kt`（API 28-32）：使用反射呼叫 `ConnectivityManager.startTethering()`
    - 建立 `adapter/HotspotApiAdapterGuided.kt`（API 33+）：使用 Intent 跳轉系統設定
    - 建立 Hilt Module 根據 API 等級提供對應實作
    - _需求：2.1, 2.2, 2.3_

  - [x] 7.2 撰寫單元測試：HotspotApiAdapter
    - 測試直接模式的啟動/關閉流程
    - 測試引導模式回傳 NeedUserAction 結果
    - 測試失敗情境的錯誤處理
    - _需求：2.2, 2.3, 2.4, 2.6_

- [x] 8. 實作智慧切換引擎（Smart Switch Engine）
  - [x] 8.1 實作 SmartSwitchEngine 介面與核心決策邏輯
    - 建立 `core/SmartSwitchEngine.kt` 介面
    - 建立 `core/SmartSwitchEngineImpl.kt`，注入 WifiApiAdapter, HotspotApiAdapter, NetworkStateMonitor, PreferenceRepository
    - 實作核心決策邏輯：根據 smartSwitchEnabled、mobileDataAvailable、bestKnownWifiRssi、signalThreshold 決定切換動作
    - 實作 30 秒掃描週期
    - _需求：3.4, 3.6, 3.7, 4.1, 4.3, 4.4, 4.5_

  - [x] 8.2 實作 SSID 失敗封鎖與手動排除邏輯
    - 實作連線失敗計數器：連續 3 次失敗封鎖該 SSID
    - 實作 60 秒重試等待
    - 實作 `excludeSsid()` 方法：手動排除的 SSID 在當次執行期間不再自動連線
    - 實作 `resetExclusions()` 方法
    - _需求：3.8, 3.9, 3.10_

  - [x] 8.3 撰寫屬性測試：智慧切換決策邏輯
    - **屬性 2：智慧切換決策邏輯**
    - 對任意 (smartSwitchEnabled, mobileDataAvailable, bestKnownWifiRssi, signalThreshold) 組合，驗證決策結果符合規則
    - **驗證需求：3.6, 3.7, 4.3, 4.4, 4.5**

  - [x] 8.4 撰寫屬性測試：SSID 失敗封鎖
    - **屬性 4：SSID 失敗封鎖**
    - 對任意 SSID 與任意連續失敗次數 N，驗證封鎖條件為 N ≥ 3
    - **驗證需求：3.9**

  - [x] 8.5 撰寫屬性測試：手動排除阻止自動連線
    - **屬性 5：手動排除阻止自動連線**
    - 對任意已排除的 SSID，即使訊號最強且超過門檻，也不應被選為連線目標
    - **驗證需求：3.10**

  - [x] 8.6 撰寫單元測試：SmartSwitchEngine 狀態機
    - 測試狀態轉換：Idle → Scanning → WifiConnecting → WifiConnected
    - 測試狀態轉換：WifiConnected → HotspotRestoring → HotspotActive
    - 測試停用智慧切換時的狀態重置
    - _需求：4.3, 4.4, 4.5_

- [x] 9. 檢查點 - 確認核心邏輯層測試通過
  - 確保所有測試通過，如有問題請詢問使用者。

- [x] 10. 實作錯誤訊息轉換層
  - [x] 10.1 實作使用者可見錯誤訊息轉換
    - 建立 `util/ErrorMessageMapper.kt`
    - 實作 `HotspotResult.Failure` → 使用者友善訊息的轉換邏輯
    - 確保轉換後的訊息不包含原始技術錯誤字串、例外類別名稱或堆疊追蹤
    - 每個錯誤訊息包含建議的下一步動作
    - _需求：2.6_

  - [x] 10.2 撰寫屬性測試：錯誤訊息不洩漏技術細節
    - **屬性 7：錯誤訊息不洩漏技術細節**
    - 對任意技術性錯誤原因字串，驗證轉換後的使用者訊息不包含原始技術字串、例外類別名稱或堆疊追蹤
    - **驗證需求：2.6**

- [x] 11. 實作 Foreground Service 與通知
  - [x] 11.1 實作 WifiManagerForegroundService
    - 建立 `service/WifiManagerForegroundService.kt`
    - 使用 Hilt 注入 SmartSwitchEngine
    - 實作 Foreground Service 生命週期（onCreate, onStartCommand, onDestroy）
    - 建立通知頻道與持續性通知
    - 根據 SmartSwitchState 更新通知內容（WiFi 連線中、Hotspot 啟用中、掃描中、已停用）
    - _需求：5.1, 5.5_

  - [x] 11.2 實作 WorkManager 服務重啟排程
    - 建立 `service/ServiceRestartWorker.kt`
    - 實作服務被殺後的重啟邏輯
    - 設定 WorkManager 的 `ExistingPeriodicWorkPolicy`
    - _需求：5.2_

  - [x] 11.3 實作 BootReceiver 開機廣播接收器
    - 建立 `receiver/BootReceiver.kt`
    - 接收 `BOOT_COMPLETED` 廣播
    - 根據 PreferenceRepository 的 autoStartEnabled 決定是否啟動服務
    - 在 AndroidManifest.xml 中註冊 receiver
    - _需求：4.6_

  - [x] 11.4 撰寫屬性測試：通知內容反映運作狀態
    - **屬性 10：通知內容反映運作狀態**
    - 對任意 SmartSwitchState，驗證通知內容包含能辨識當前運作模式的描述文字
    - **驗證需求：5.5**

  - [x] 11.5 撰寫單元測試：Service 與 WorkManager
    - 測試 BootReceiver 在 autoStartEnabled=true 時啟動服務
    - 測試 BootReceiver 在 autoStartEnabled=false 時不啟動服務
    - 測試通知頻道建立
    - _需求：4.6, 5.1, 5.2_

- [x] 12. 檢查點 - 確認服務層測試通過
  - 確保所有測試通過，如有問題請詢問使用者。

- [x] 13. 實作 UI 層 - ViewModel 與狀態管理
  - [x] 13.1 實作 DashboardViewModel
    - 建立 `ui/dashboard/DashboardViewModel.kt`
    - 注入 SmartSwitchEngine, HotspotApiAdapter, PreferenceRepository
    - 透過 StateFlow 暴露 UI 狀態：智慧切換開關、Hotspot 狀態、WiFi 連線狀態、已知網路數量
    - 實作 Hotspot 操作按鈕邏輯（根據控制模式決定行為）
    - 實作智慧切換開關切換邏輯
    - _需求：2.1, 2.5, 4.1_

  - [x] 13.2 實作 SettingsViewModel
    - 建立 `ui/settings/SettingsViewModel.kt`
    - 注入 PreferenceRepository
    - 透過 StateFlow 暴露設定狀態：開機自動啟動開關、訊號強度門檻
    - 實作設定變更邏輯
    - _需求：4.2, 4.7_

  - [x] 13.3 撰寫屬性測試：Hotspot 狀態與 UI 狀態同步
    - **屬性 6：Hotspot 狀態與 UI 狀態同步**
    - 對任意 HotspotState 變更事件，驗證 ViewModel 發出的 UI 狀態正確反映新狀態
    - **驗證需求：2.5**

  - [x] 13.4 撰寫單元測試：ViewModel 狀態管理
    - 使用 Turbine 測試 StateFlow 發射
    - 測試智慧切換開關切換後的狀態變化
    - 測試 Hotspot 操作在不同控制模式下的行為
    - _需求：2.1, 2.5, 4.1_

- [x] 14. 實作 UI 層 - 權限處理
  - [x] 14.1 實作權限請求與說明流程
    - 建立 `ui/permission/PermissionHandler.kt`
    - 實作首次啟動時的權限請求流程
    - 實作權限被拒絕時的說明對話框（包含權限用途說明與重新請求入口）
    - 處理 API 版本差異的權限請求（如 API 33+ 的 POST_NOTIFICATIONS）
    - _需求：5.3, 5.4_

  - [x] 14.2 撰寫屬性測試：權限拒絕顯示說明
    - **屬性 11：權限拒絕顯示說明**
    - 對任意被拒絕的必要權限，驗證 App 顯示用途說明訊息且包含重新請求入口
    - **驗證需求：5.4**

- [x] 15. 實作 UI 層 - 佈局與畫面
  - [x] 15.1 實作 MainActivity 與 Fragment 容器
    - 建立 `ui/MainActivity.kt`
    - 設定 Hilt 的 `@AndroidEntryPoint`
    - 實作 Fragment 切換邏輯（Dashboard / Settings）
    - 設定螢幕方向適配（讀取螢幕寬度決定佈局模式）
    - _需求：6.1, 6.2_

  - [x] 15.2 實作 DashboardFragment 佈局
    - 建立 `res/layout/fragment_dashboard.xml`（單欄，<600dp）
    - 建立 `res/layout-w600dp/fragment_dashboard.xml`（雙欄，≥600dp）
    - 包含：智慧切換主開關、Hotspot 操作按鈕、WiFi 連線狀態、已知網路數量
    - 確保按鈕最小尺寸 72dp × 72dp，互動元素最小 48dp × 48dp
    - 確保文字最小 16sp，使用高對比度色彩
    - _需求：6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [x] 15.3 實作 SettingsFragment 佈局
    - 建立 `res/layout/fragment_settings.xml`
    - 包含：開機自動啟動開關、訊號強度門檻調整
    - 確保互動元素尺寸符合需求
    - _需求：4.2, 6.3, 6.6_

  - [x] 15.4 實作 DashboardFragment 與 SettingsFragment 邏輯
    - 建立 `ui/dashboard/DashboardFragment.kt`，綁定 DashboardViewModel
    - 建立 `ui/settings/SettingsFragment.kt`，綁定 SettingsViewModel
    - 實作 UI 事件處理與狀態觀察
    - _需求：2.1, 4.1, 4.2_

  - [x] 15.5 撰寫單元測試：佈局元素尺寸與存在性
    - 使用 Robolectric 驗證佈局元素存在
    - 驗證按鈕最小尺寸設定
    - 驗證文字最小字體大小
    - _需求：6.3, 6.4, 6.5, 6.6_

- [x] 16. 整合與連接所有元件
  - [x] 16.1 建立 Hilt Application 與 DI Module
    - 建立 `AutoWifiManagerApplication.kt`，標記 `@HiltAndroidApp`
    - 建立 `di/AppModule.kt`，提供所有 Singleton 綁定
    - 建立 `di/AdapterModule.kt`，根據 API 等級提供版本適配實作
    - 確保所有元件正確注入與連接
    - _需求：1.1, 1.3_

  - [x] 16.2 連接 UI 層與 Service 層
    - 在 MainActivity 中實作服務啟動/停止邏輯
    - 連接 DashboardFragment 的智慧切換開關與 Foreground Service
    - 實作從系統設定返回時的狀態重新偵測（onResume）
    - _需求：2.7, 4.1, 4.5_

  - [x] 16.3 實作完整的智慧切換流程整合
    - 確保 SmartSwitchEngine 正確協調 WiFi 連線與 Hotspot 切換
    - 確保行動網路恢復時自動中斷 WiFi 並恢復 Hotspot
    - 確保行動網路中斷時自動掃描並連線 WiFi
    - 確保手動排除的 SSID 在當次執行期間不被自動連線
    - _需求：3.6, 3.7, 4.3, 4.4, 4.5, 3.10_

  - [x] 16.4 撰寫整合測試
    - 測試 Foreground Service 啟動與通知顯示
    - 測試 WorkManager 排程
    - 測試 BootReceiver 觸發服務啟動
    - 測試完整的智慧切換流程（模擬網路狀態變化）
    - _需求：4.6, 5.1, 5.2, 5.5_

- [x] 17. 最終檢查點 - 確認所有測試通過
  - 確保所有測試通過，如有問題請詢問使用者。

## 備註

- 標記 `*` 的任務為選用任務，可跳過以加速 MVP 開發
- 每個任務引用具體需求以確保可追溯性
- 檢查點確保逐步驗證實作正確性
- 屬性測試驗證 11 個正確性屬性的普遍正確性
- 單元測試驗證具體範例與邊界條件
- 所有版本適配邏輯封裝在 Adapter 層，核心邏輯不直接依賴 Android API 版本
