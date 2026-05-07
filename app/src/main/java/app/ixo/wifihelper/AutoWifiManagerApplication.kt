package app.ixo.wifihelper

import android.app.Application
import androidx.work.Configuration
import app.ixo.wifihelper.util.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Auto WiFi Manager 的 Application 類別。
 *
 * 標記 [HiltAndroidApp] 以啟用 Hilt 依賴注入框架，
 * 實作 [Configuration.Provider] 讓 WorkManager 使用 Hilt 的 WorkerFactory，
 * 使 @HiltWorker 標記的 Worker 能正確注入依賴。
 *
 * 需求：1.1, 1.3, 5.2
 */
@HiltAndroidApp
class AutoWifiManagerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
