# 需求文件：Auto WiFi Manager

## 簡介

Auto WiFi Manager 是一款車用 Android WiFi 智慧管理應用程式，定位為「WiFi Helper」。此 App 的核心目標是讓車機使用者能夠以最無感、最自動化的方式管理 WiFi 連線與熱點分享行為，完全不需要理解技術細節或版本差異。App 直接利用 Android 系統已記憶的 WiFi 網路清單進行智慧連線，使用者不需要在 App 中重新輸入任何 SSID 或密碼。使用者的核心決定只有一個：要不要讓 App 在偵測到良好 WiFi 訊號且行動網路不可用時，自動在 WiFi Hotspot 與 WiFi 連線之間切換。App 支援 Android 9.0（API 28）至 Android 16（API 36）的跨版本適配，所有版本差異的處理皆在背景自動完成，使用者看到的永遠是一致且簡潔的操作介面。介面以橫向（Landscape）車機螢幕為主要設計方向，同時相容直向手機使用，操作元素需清晰、大尺寸、易於觸控。App 預期上架 Google Play。

## 詞彙表

- **App**：Auto WiFi Manager 應用程式本身，對外定位為「WiFi Helper」
- **WiFi_Manager**：App 中負責 WiFi 連線管理的核心模組
- **Hotspot_Manager**：App 中負責 WiFi 熱點（Hotspot）啟動與管理的模組
- **Preference_Engine**：App 中負責儲存與執行使用者偏好設定的模組
- **Version_Adapter**：App 中負責在背景偵測 Android 版本並自動適配對應 API 能力的模組，使用者不會感知此模組的存在
- **UI_Renderer**：App 中負責畫面佈局與方向適配的介面渲染模組
- **SSID**：WiFi 網路的服務集識別碼（Service Set Identifier），用於識別特定的 WiFi 網路
- **系統已記憶 WiFi 清單**：Android 系統中使用者已設定並記憶的 WiFi 網路清單，在 API 28-29 可透過 WifiManager.getConfiguredNetworks() 取得，在 API 30+ 因隱私限制改為透過 WifiManager.getScanResults() 比對或 WifiNetworkSuggestion API 處理
- **訊號強度門檻**：App 用來判斷 WiFi 訊號是否足夠良好以進行自動連線的 RSSI 數值標準（預設 -70 dBm）
- **直接控制模式**：在較舊的 Android 版本上，App 可透過 API 直接啟動或關閉 WiFi 熱點的內部操作方式（使用者不可見）
- **引導控制模式**：在較新的 Android 版本上，App 無法直接操作熱點，改為自動跳轉至系統設定頁面的內部操作方式（使用者不可見）
- **智慧切換**：App 在偵測到良好 WiFi 訊號且行動網路（4G/5G）不可用時，自動在 WiFi Hotspot 與 WiFi 連線之間切換的核心行為
- **開機自動啟動**：裝置開機完成後，App 自動啟動背景服務以執行智慧切換功能

## 需求

### 需求 1：Android 版本透明適配

**使用者故事：** 身為車機使用者，我希望 App 自動處理所有 Android 版本差異，讓我完全不需要知道版本資訊，只看到能用的功能就好。

#### 驗收條件

1. WHEN App 啟動時，THE Version_Adapter SHALL 在背景偵測當前裝置的 Android API 等級並記錄於 App 內部狀態，不向使用者顯示任何版本相關資訊
2. THE Version_Adapter SHALL 支援 Android 9.0（API 28）至 Android 16（API 36）的所有版本
3. WHEN Version_Adapter 完成版本偵測後，THE Version_Adapter SHALL 在內部建立當前版本的功能適配策略，決定各功能模組應使用直接控制模式或引導控制模式
4. THE UI_Renderer SHALL 根據 Version_Adapter 的內部適配策略，僅渲染當前版本可操作的功能元素，自然地省略不支援的功能，不顯示任何「不支援」或「不可用」的提示
5. THE App SHALL 確保不同 Android 版本的使用者看到的介面風格與操作流程保持一致，版本差異僅影響內部實作方式

### 需求 2：WiFi 熱點管理

**使用者故事：** 身為車機使用者，我希望能透過 App 用一個按鈕管理 WiFi 熱點，不需要知道背後的技術細節，這樣我可以方便地分享車機的網路給乘客使用。

#### 驗收條件

1. THE Hotspot_Manager SHALL 在介面上提供一個統一的熱點操作按鈕，使用者無需知道背後使用的是直接控制模式或引導控制模式
2. WHILE 裝置的 Android 版本支援直接控制模式（API 28 至 API 32），WHEN 使用者點擊熱點操作按鈕時，THE Hotspot_Manager SHALL 直接在 App 內啟動或關閉 WiFi 熱點
3. WHILE 裝置的 Android 版本使用引導控制模式（API 33 及以上），WHEN 使用者點擊熱點操作按鈕時，THE Hotspot_Manager SHALL 自動開啟系統設定中的熱點設定頁面
4. WHEN 使用者點擊熱點操作按鈕且處於直接控制模式時，THE Hotspot_Manager SHALL 在 3 秒內完成熱點狀態的切換
5. WHEN 熱點狀態變更完成後，THE Hotspot_Manager SHALL 更新 UI 上的熱點狀態指示器以反映當前狀態
6. IF 熱點啟動失敗，THEN THE Hotspot_Manager SHALL 顯示簡潔的錯誤訊息，僅說明操作未成功並建議重試，不暴露技術細節
7. WHILE 裝置使用引導控制模式，WHEN 使用者從系統設定返回 App 時，THE Hotspot_Manager SHALL 自動偵測當前熱點狀態並更新 UI 上的狀態指示器

### 需求 3：WiFi 自動連線管理

**使用者故事：** 身為車機使用者，我希望 App 能直接使用我手機上已記憶的 WiFi 網路來自動連線，不需要在 App 中重新輸入任何 SSID 或密碼，這樣我完全不需要額外設定就能享受智慧連線。

#### 驗收條件

1. WHILE 裝置的 Android 版本為 API 28 至 API 29，THE WiFi_Manager SHALL 透過 WifiManager.getConfiguredNetworks() 取得系統已記憶 WiFi 清單
2. WHILE 裝置的 Android 版本為 API 30 及以上，THE WiFi_Manager SHALL 透過 WifiManager.getScanResults() 比對系統已記憶的 WiFi 網路，或透過 WifiNetworkSuggestion API 協助系統進行連線決策
3. THE WiFi_Manager SHALL 在介面上顯示目前偵測到的系統已記憶 WiFi 網路數量與連線狀態，使用者無需進行任何手動輸入
4. WHILE 智慧切換功能已啟用，THE WiFi_Manager SHALL 每 30 秒掃描一次周圍可用的 WiFi 網路
5. WHEN 掃描結果中包含一個或多個系統已記憶的 WiFi 網路且訊號強度超過訊號強度門檻時，THE WiFi_Manager SHALL 自動連線至訊號強度最高的系統已記憶 WiFi 網路
6. WHILE 智慧切換功能已啟用且行動數據（4G/5G）未連線，THE WiFi_Manager SHALL 優先執行自動 WiFi 連線
7. WHILE 智慧切換功能已啟用且行動數據（4G/5G）已連線且 WiFi 訊號低於訊號強度門檻，THE WiFi_Manager SHALL 維持行動數據連線而不嘗試切換至 WiFi
8. IF 自動連線嘗試失敗，THEN THE WiFi_Manager SHALL 記錄失敗原因並在 60 秒後重新嘗試連線
9. IF 連續三次自動連線嘗試均失敗，THEN THE WiFi_Manager SHALL 停止對該 SSID 的自動連線嘗試並通知使用者
10. WHEN 使用者手動中斷某個 WiFi 連線時，THE WiFi_Manager SHALL 在當次 App 執行期間停止對該 SSID 的自動連線
11. IF Version_Adapter 偵測到當前 Android 版本無法取得系統已記憶 WiFi 清單，THEN THE WiFi_Manager SHALL 顯示簡潔的提示訊息說明需要使用者手動前往系統 WiFi 設定進行連線

### 需求 4：使用者偏好與智慧切換控制

**使用者故事：** 身為車機使用者，我只需要做一個簡單的決定：要不要讓 App 在有好的 WiFi 訊號且沒有行動網路時，自動幫我切換 WiFi Hotspot 和 WiFi 連線，這樣我幾乎不需要任何設定就能使用。

#### 驗收條件

1. THE Preference_Engine SHALL 在介面上提供一個「智慧切換」主開關，控制 App 是否在偵測到良好 WiFi 訊號且行動網路（4G/5G）不可用時，自動在 WiFi Hotspot 與 WiFi 連線之間切換
2. THE Preference_Engine SHALL 在介面上提供一個「開機自動啟動」開關，控制 App 是否在裝置開機後自動啟動背景服務
3. WHEN 使用者啟用「智慧切換」開關時，THE Preference_Engine SHALL 啟動 WiFi_Manager 的自動掃描與連線功能，並在行動網路不可用且偵測到良好 WiFi 訊號時自動關閉 Hotspot 並連線至 WiFi
4. WHEN 使用者啟用「智慧切換」開關且行動網路（4G/5G）恢復可用時，THE Preference_Engine SHALL 自動中斷 WiFi 連線並恢復 WiFi Hotspot 以分享行動網路
5. WHEN 使用者關閉「智慧切換」開關時，THE Preference_Engine SHALL 停止所有自動切換行為，維持當前連線狀態不變
6. WHILE 使用者已啟用「開機自動啟動」開關，WHEN 裝置開機完成且 App 收到開機廣播時，THE Preference_Engine SHALL 自動啟動背景服務並根據「智慧切換」開關的狀態決定是否執行自動切換
7. THE Preference_Engine SHALL 將「智慧切換」與「開機自動啟動」兩個開關的狀態持久化儲存，確保 App 更新或重啟後設定不遺失

### 需求 5：背景服務與系統限制處理

**使用者故事：** 身為車機使用者，我希望 App 的自動化功能能在背景穩定運作，即使 Android 系統有背景限制也能正常執行。

#### 驗收條件

1. THE App SHALL 使用 Android Foreground Service 搭配持續性通知來維持背景自動化任務的運作
2. WHEN App 的背景服務被系統終止時，THE App SHALL 透過 WorkManager 排程在可行的時間點重新啟動背景服務
3. THE App SHALL 在首次啟動時請求所有必要的執行時期權限，包含：精確位置權限、背景位置權限、WiFi 狀態變更權限、通知權限
4. IF 使用者拒絕授予必要權限，THEN THE App SHALL 顯示簡潔的說明訊息解釋該權限的用途，並提供重新請求權限的入口
5. WHILE App 的背景服務正在運作，THE App SHALL 在系統通知列顯示一則持續性通知，標示目前的運作狀態
6. WHEN Android 版本為 API 31 及以上時，THE App SHALL 請求 SCHEDULE_EXACT_ALARM 權限以確保定時任務的精確執行

### 需求 6：使用者介面與佈局適配

**使用者故事：** 身為車機使用者，我希望 App 的介面在車機的橫向螢幕上清晰好操作，同時在手機的直向螢幕上也能正常使用。

#### 驗收條件

1. THE UI_Renderer SHALL 以橫向（Landscape）佈局作為主要設計方向
2. WHEN 裝置螢幕方向為直向（Portrait）時，THE UI_Renderer SHALL 自動調整佈局以適應直向顯示
3. THE UI_Renderer SHALL 確保所有可觸控的互動元素最小尺寸為 48dp × 48dp
4. THE UI_Renderer SHALL 確保主要操作按鈕的最小尺寸為 72dp × 72dp，以符合車用操作的安全需求
5. THE UI_Renderer SHALL 使用高對比度的色彩配置，確保在日光直射環境下文字與圖示仍可清晰辨識
6. THE UI_Renderer SHALL 確保所有文字元素的最小字體大小為 16sp
7. WHILE 裝置螢幕寬度小於 600dp，THE UI_Renderer SHALL 使用單欄式佈局
8. WHILE 裝置螢幕寬度大於或等於 600dp，THE UI_Renderer SHALL 使用雙欄式佈局以充分利用橫向空間

### 需求 7：資料儲存與安全性

**使用者故事：** 身為使用者，我希望 App 安全地儲存我的偏好設定，且不需要額外儲存任何 WiFi 密碼，因為 App 直接使用系統已記憶的 WiFi 資訊。

#### 驗收條件

1. THE App SHALL 使用 SharedPreferences 或 EncryptedSharedPreferences 儲存使用者偏好設定（智慧切換開關狀態、開機自動啟動開關狀態）
2. THE App SHALL 不儲存任何 WiFi SSID 密碼，所有 WiFi 連線認證資訊由 Android 系統管理
3. IF App 偵測到儲存資料損毀，THEN THE App SHALL 將偏好設定重置為預設值並通知使用者
4. THE App SHALL 在使用者解除安裝時，透過 Android 系統的預設行為清除所有本地儲存資料

### 需求 8：Google Play 上架合規

**使用者故事：** 身為開發者，我希望 App 符合 Google Play 的上架政策，這樣 App 能順利發布並持續在商店中上架。

#### 驗收條件

1. THE App SHALL 宣告 compileSdk 為最新穩定版 Android SDK
2. THE App SHALL 設定 targetSdk 為 Google Play 當前要求的最低目標 SDK 版本
3. THE App SHALL 設定 minSdk 為 API 28（Android 9.0）
4. THE App SHALL 在 AndroidManifest.xml 中正確宣告所有使用的權限，並為每項權限提供使用理由說明
5. THE App SHALL 提供隱私權政策頁面，說明 App 收集與使用的資料類型
6. IF App 使用 Foreground Service，THEN THE App SHALL 在 Google Play Console 中提交 Foreground Service 使用聲明並說明用途
