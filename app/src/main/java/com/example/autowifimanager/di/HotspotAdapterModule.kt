package com.example.autowifimanager.di

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import com.example.autowifimanager.adapter.HotspotApiAdapter
import com.example.autowifimanager.adapter.HotspotApiAdapterDirect
import com.example.autowifimanager.adapter.HotspotApiAdapterGuided
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module：根據 Android API 等級提供對應的 [HotspotApiAdapter] 實作。
 *
 * - API 28-32：提供 [HotspotApiAdapterDirect]（透過反射直接控制 Tethering）
 * - API 33+：提供 [HotspotApiAdapterGuided]（透過 Intent 引導至系統設定）
 *
 * 此 Module 依賴 [WifiAdapterModule] 提供的 [WifiManager] 與 [ConnectivityManager]。
 */
@Module
@InstallIn(SingletonComponent::class)
object HotspotAdapterModule {

    @Provides
    @Singleton
    fun provideHotspotApiAdapter(
        connectivityManager: ConnectivityManager,
        wifiManager: WifiManager
    ): HotspotApiAdapter {
        // Build.VERSION_CODES.S_V2 = API 32
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // API 28-32：直接控制模式，透過反射呼叫 Tethering API
            HotspotApiAdapterDirect(connectivityManager, wifiManager)
        } else {
            // API 33+：引導控制模式，透過 Intent 跳轉系統設定
            HotspotApiAdapterGuided(wifiManager)
        }
    }
}
