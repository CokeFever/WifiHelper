# WiFi Helper

車用 Android WiFi 智慧管理 App，專為 **ApplePie / CarPlay AI Box** 等車用 Android 裝置設計。

## 解決什麼問題？

ApplePie 這類車用 Android 盒子通常透過手機 WiFi Hotspot 取得網路。但在某些場景下（例如停車場有 WiFi、到家後連家用 WiFi），你會希望裝置自動切換到可用的 WiFi 網路，省下手機的行動數據。等你開車離開、行動網路恢復時，又希望它自動切回 Hotspot 模式分享網路給車上其他裝置。

**WiFi Helper 就是自動幫你做這件事。**

你只需要做一個決定：開不開「智慧切換」。剩下的全部自動。

## 功能

- **智慧切換** — 偵測到良好 WiFi 訊號且行動網路不可用時，自動關閉 Hotspot 並連線 WiFi；行動網路恢復時自動切回 Hotspot
- **零設定** — 直接使用 Android 系統已記憶的 WiFi 網路，不需要在 App 中重新輸入任何 SSID 或密碼
- **跨版本適配** — 支援 Android 9（API 28）至 Android 16（API 36），所有版本差異在背景自動處理
- **Hotspot 一鍵操作** — 統一的操作按鈕，不管底層是直接控制（API 28-32）還是引導至系統設定（API 33+）
- **背景穩定運作** — Foreground Service + WorkManager 確保服務不被系統殺掉
- **開機自動啟動** — 車機開機後自動啟動背景服務
- **車機優化介面** — 橫向大螢幕優先設計，大按鈕（72dp+）、大字體（16sp+）、高對比度

## 適用裝置

- [NavLynx ApplePie](https://www.navlynx.com/) 系列（ApplePie Mini、ApplePie Ultra 等）
- 其他 CarPlay AI Box / Android Auto Box
- Android 車機主機（Android 9.0+）
- 任何需要自動管理 WiFi/Hotspot 切換的 Android 裝置

## 截圖

> 開發中，待補充

## 技術架構

```
UI Layer (MVVM)
├── MainActivity (Fragment 切換、Service 生命週期)
├── DashboardFragment (智慧切換開關、Hotspot 操作、狀態顯示)
└── SettingsFragment (開機自動啟動、訊號門檻)

Core Logic Layer
├── SmartSwitchEngine (核心決策邏輯、30 秒掃描週期)
├── NetworkStateMonitor (行動網路/WiFi 狀態監控)
└── NetworkSelector (最佳網路選擇演算法)

Adapter Layer (版本適配)
├── WifiApiAdapter (Legacy: API 28-29 / Modern: API 30+)
├── HotspotApiAdapter (Direct: API 28-32 / Guided: API 33+)
└── VersionAdapter (API 等級偵測與策略分配)

Service Layer
├── WifiManagerForegroundService (背景服務)
├── ServiceRestartWorker (WorkManager 重啟保障)
└── BootReceiver (開機廣播)

Data Layer
└── PreferenceRepository (EncryptedSharedPreferences)
```

## 建置

### 環境需求

- Android Studio Meerkat (2025.1) 或更新
- JDK 21
- Android SDK (compileSdk 36)

### 建置與測試

```bash
# 跑全部測試（182 個）
./gradlew test

# Build release APK
./gradlew assembleRelease

# Build release AAB (Google Play 上架用)
./gradlew bundleRelease
```

### CI/CD

推送 `v*` tag 會自動觸發 GitHub Actions：
1. 跑全部單元測試
2. Build release APK + AAB
3. 建立 GitHub Release 並附上產物

```bash
git tag v0.0.2
git push origin v0.0.2
```

## 測試

專案包含 182 個測試，涵蓋 11 個正確性屬性的屬性基礎測試（Property-Based Testing）：

| 屬性 | 驗證內容 |
|------|---------|
| 版本適配映射 | API 28-36 的 Hotspot/WiFi 策略映射正確 |
| 智慧切換決策 | 所有輸入組合的決策結果符合規則 |
| 最佳網路選擇 | 選擇超過門檻的最強訊號網路 |
| SSID 失敗封鎖 | 連續 3 次失敗封鎖該 SSID |
| 手動排除 | 被排除的 SSID 不被自動連線 |
| Hotspot 狀態同步 | UI 正確反映 Hotspot 狀態變更 |
| 錯誤訊息安全 | 使用者訊息不洩漏技術細節 |
| 偏好設定往返 | 儲存後讀取值完全一致 |
| 損毀偵測重置 | 偵測到損毀自動重置為預設值 |
| 通知內容正確 | 通知文字反映當前運作模式 |
| 權限說明完整 | 每個被拒權限都有用途說明 |

## 授權

MIT License

## 作者

[@CokeFever](https://github.com/CokeFever)
