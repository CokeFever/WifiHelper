package app.ixo.wifihelper.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt DI Module：版本適配實作的聚合模組。
 *
 * 此模組作為版本適配層的入口點。實際的版本分支提供邏輯
 * 已由以下模組各自處理：
 * - [WifiAdapterModule]：根據 API 等級提供 [WifiApiAdapter] 實作
 *   （API 28-29 → [WifiApiAdapterLegacy]，API 30+ → [WifiApiAdapterModern]）
 * - [HotspotAdapterModule]：根據 API 等級提供 [HotspotApiAdapter] 實作
 *   （API 28-32 → [HotspotApiAdapterDirect]，API 33+ → [HotspotApiAdapterGuided]）
 *
 * [VersionAdapter] → [VersionAdapterImpl] 的綁定由 [AppModule] 處理，
 * 因為 VersionAdapterImpl 不需要根據 API 等級動態選擇實作。
 *
 * 此模組保留作為未來可能新增的版本適配提供者的擴充點。
 *
 * 需求：1.1, 1.3
 */
@Module(
    includes = [
        WifiAdapterModule::class,
        HotspotAdapterModule::class
    ]
)
@InstallIn(SingletonComponent::class)
object AdapterModule
