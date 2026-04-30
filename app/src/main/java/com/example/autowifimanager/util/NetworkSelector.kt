package com.example.autowifimanager.util

import com.example.autowifimanager.model.KnownWifiNetwork

/**
 * 最佳網路選擇工具：從已知 WiFi 網路清單中選擇最佳連線目標。
 *
 * 選擇演算法：
 * 1. 排除被手動排除或封鎖的 SSID
 * 2. 過濾訊號強度嚴格超過門檻的網路
 * 3. 從過濾結果中選擇 RSSI 最高者
 * 4. 若無網路超過門檻，回傳 null（不嘗試連線）
 *
 * 此為純函式，無 Android 依賴，方便單元測試與屬性測試。
 *
 * **Validates: Requirements 3.5**
 */
object NetworkSelector {

    /**
     * 從已知網路清單中選擇最佳連線目標。
     *
     * @param networks 已知 WiFi 網路清單（含 RSSI 等資訊）
     * @param signalThreshold 訊號強度門檻（dBm），僅選擇 RSSI 嚴格大於此值的網路
     * @param excludedSsids 需排除的 SSID 集合（手動排除與 SSID 封鎖），預設為空
     * @return 最佳網路（RSSI 最高且超過門檻者），若無符合條件的網路則回傳 null
     */
    fun selectBestNetwork(
        networks: List<KnownWifiNetwork>,
        signalThreshold: Int,
        excludedSsids: Set<String> = emptySet()
    ): KnownWifiNetwork? {
        return networks
            .filter { it.ssid !in excludedSsids }
            .filter { it.rssi > signalThreshold }
            .maxByOrNull { it.rssi }
    }
}
