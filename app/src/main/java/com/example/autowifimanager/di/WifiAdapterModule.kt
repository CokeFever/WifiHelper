package com.example.autowifimanager.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import com.example.autowifimanager.adapter.WifiApiAdapter
import com.example.autowifimanager.adapter.WifiApiAdapterLegacy
import com.example.autowifimanager.adapter.WifiApiAdapterModern
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module：根據 Android API 等級提供對應的 [WifiApiAdapter] 實作。
 *
 * - API 28-29：提供 [WifiApiAdapterLegacy]（使用 getConfiguredNetworks + enableNetwork）
 * - API 30+：提供 [WifiApiAdapterModern]（使用 getScanResults + WifiNetworkSpecifier）
 */
@Module
@InstallIn(SingletonComponent::class)
object WifiAdapterModule {

    @Provides
    @Singleton
    fun provideWifiManager(
        @ApplicationContext context: Context
    ): WifiManager {
        return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(
        @ApplicationContext context: Context
    ): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideWifiApiAdapter(
        wifiManager: WifiManager,
        connectivityManager: ConnectivityManager
    ): WifiApiAdapter {
        // Build.VERSION_CODES.Q = API 29
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // API 28-29：使用 getConfiguredNetworks + enableNetwork
            WifiApiAdapterLegacy(wifiManager)
        } else {
            // API 30+：使用 getScanResults + WifiNetworkSpecifier
            WifiApiAdapterModern(wifiManager, connectivityManager)
        }
    }
}
