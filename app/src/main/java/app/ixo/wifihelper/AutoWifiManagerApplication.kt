package app.ixo.wifihelper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Auto WiFi Manager 的 Application 類別。
 *
 * 標記 [HiltAndroidApp] 以啟用 Hilt 依賴注入框架，
 * 作為 Hilt 元件階層的根節點，觸發 Hilt 的程式碼生成。
 *
 * 需求：1.1, 1.3
 */
@HiltAndroidApp
class AutoWifiManagerApplication : Application()
