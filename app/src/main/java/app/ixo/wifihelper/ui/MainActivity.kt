package app.ixo.wifihelper.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import app.ixo.wifihelper.R
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.service.ServiceRestartWorker
import app.ixo.wifihelper.service.WifiManagerForegroundService
import app.ixo.wifihelper.ui.dashboard.DashboardFragment
import app.ixo.wifihelper.ui.settings.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 主畫面 Activity，管理 Fragment 切換、螢幕方向適配與 Foreground Service 生命週期。
 *
 * 使用 [BottomNavigationView] 在 [DashboardFragment] 與 [SettingsFragment] 之間切換。
 * 根據螢幕寬度決定佈局模式：
 * - 螢幕寬度 < 600dp：單欄佈局
 * - 螢幕寬度 ≥ 600dp：雙欄佈局（由 layout-w600dp 資源限定符自動處理）
 *
 * 負責根據智慧切換開關狀態啟動/停止 [WifiManagerForegroundService]，
 * 並在從系統設定返回時（onResume）重新偵測狀態。
 *
 * 需求：2.7, 4.1, 4.5, 6.1, 6.2
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        /** 雙欄佈局的螢幕寬度斷點（dp） */
        const val DUAL_COLUMN_BREAKPOINT_DP = 600

        private const val KEY_SELECTED_NAV_ITEM = "selected_nav_item"
    }

    @Inject
    lateinit var preferenceRepository: PreferenceRepository

    private lateinit var bottomNavigation: BottomNavigationView

    /**
     * 當前是否為寬螢幕模式（≥600dp）。
     * 由 [determineLayoutMode] 根據螢幕寬度計算。
     */
    var isWideScreen: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        isWideScreen = determineLayoutMode()

        bottomNavigation = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()

        // 首次啟動時載入 DashboardFragment
        if (savedInstanceState == null) {
            switchFragment(DashboardFragment())
            bottomNavigation.selectedItemId = R.id.nav_dashboard
        } else {
            // 恢復選中的導航項目
            val selectedId = savedInstanceState.getInt(KEY_SELECTED_NAV_ITEM, R.id.nav_dashboard)
            bottomNavigation.selectedItemId = selectedId
        }

        // 根據偏好設定決定是否啟動服務
        syncServiceState()
    }

    override fun onResume() {
        super.onResume()
        // 從系統設定返回時重新同步服務狀態（需求 2.7）
        // 這確保使用者在系統設定中手動操作 Hotspot 後，
        // App 能正確偵測並反映當前狀態
        syncServiceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_NAV_ITEM, bottomNavigation.selectedItemId)
    }

    /**
     * 根據 [PreferenceRepository] 中的智慧切換開關狀態，
     * 啟動或停止 [WifiManagerForegroundService]。
     *
     * - 智慧切換啟用：啟動 Foreground Service 並排程 WorkManager 重啟保障
     * - 智慧切換停用：停止 Foreground Service 並取消 WorkManager 排程
     *
     * 需求：4.1, 4.5
     */
    fun syncServiceState() {
        val smartSwitchEnabled = preferenceRepository.isSmartSwitchEnabled()

        if (smartSwitchEnabled) {
            startWifiManagerService()
        } else {
            stopWifiManagerService()
        }
    }

    /**
     * 啟動 [WifiManagerForegroundService] 並排程 WorkManager 重啟保障。
     */
    private fun startWifiManagerService() {
        val serviceIntent = Intent(this, WifiManagerForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 排程 WorkManager 作為服務被殺後的重啟保障
        ServiceRestartWorker.schedule(this)
    }

    /**
     * 停止 [WifiManagerForegroundService] 並取消 WorkManager 排程。
     */
    private fun stopWifiManagerService() {
        val serviceIntent = Intent(this, WifiManagerForegroundService::class.java)
        stopService(serviceIntent)

        // 取消 WorkManager 重啟排程
        ServiceRestartWorker.cancel(this)
    }

    /**
     * 設定底部導航列的項目選擇監聽器。
     *
     * 根據選擇的項目切換對應的 Fragment：
     * - [R.id.nav_dashboard] → [DashboardFragment]
     * - [R.id.nav_settings] → [SettingsFragment]
     */
    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    switchFragment(DashboardFragment())
                    true
                }
                R.id.nav_settings -> {
                    switchFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 切換 Fragment 容器中的 Fragment。
     *
     * @param fragment 要顯示的 Fragment
     */
    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * 根據螢幕寬度決定佈局模式。
     *
     * 讀取螢幕寬度（dp），若 ≥ [DUAL_COLUMN_BREAKPOINT_DP] 則使用雙欄佈局。
     * Android 資源系統會自動根據 layout-w600dp 限定符選擇對應的佈局檔案。
     *
     * @return true 表示寬螢幕模式（≥600dp），false 表示窄螢幕模式（<600dp）
     */
    private fun determineLayoutMode(): Boolean {
        val screenWidthDp = resources.configuration.screenWidthDp
        return screenWidthDp >= DUAL_COLUMN_BREAKPOINT_DP
    }
}
