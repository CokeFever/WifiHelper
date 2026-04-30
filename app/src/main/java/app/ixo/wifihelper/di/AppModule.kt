package app.ixo.wifihelper.di

import app.ixo.wifihelper.adapter.VersionAdapter
import app.ixo.wifihelper.adapter.VersionAdapterImpl
import app.ixo.wifihelper.core.NetworkStateMonitor
import app.ixo.wifihelper.core.NetworkStateMonitorImpl
import app.ixo.wifihelper.core.SmartSwitchEngine
import app.ixo.wifihelper.core.SmartSwitchEngineImpl
import app.ixo.wifihelper.data.PreferenceRepository
import app.ixo.wifihelper.data.PreferenceRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt DI Module：將介面綁定至對應的實作類別。
 *
 * 使用 [@Binds] 宣告介面與實作的對應關係，Hilt 會自動提供
 * 已標記 [@Inject] 建構子的實作類別實例。
 *
 * 綁定清單：
 * - [VersionAdapter] → [VersionAdapterImpl]
 * - [PreferenceRepository] → [PreferenceRepositoryImpl]
 * - [NetworkStateMonitor] → [NetworkStateMonitorImpl]
 * - [SmartSwitchEngine] → [SmartSwitchEngineImpl]
 *
 * 注意：[WifiApiAdapter] 與 [HotspotApiAdapter] 分別由
 * [WifiAdapterModule] 與 [HotspotAdapterModule] 提供，
 * 因為它們需要根據 API 等級動態選擇實作。
 *
 * 需求：1.1, 1.3
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindVersionAdapter(impl: VersionAdapterImpl): VersionAdapter

    @Binds
    @Singleton
    abstract fun bindPreferenceRepository(impl: PreferenceRepositoryImpl): PreferenceRepository

    @Binds
    @Singleton
    abstract fun bindNetworkStateMonitor(impl: NetworkStateMonitorImpl): NetworkStateMonitor

    @Binds
    @Singleton
    abstract fun bindSmartSwitchEngine(impl: SmartSwitchEngineImpl): SmartSwitchEngine
}
