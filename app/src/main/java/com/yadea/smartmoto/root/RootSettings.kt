package com.yadea.smartmoto.root

object RootSettings {
    data class Flag(val key: String, val title: String, val default: Boolean = false)
    val flags = listOf(
        Flag("native_sensor", "原生传感器静止模拟（实验性；符号或加载不支持时拒绝启动）"),
        Flag("wifi_filter", "Wi-Fi 信息与扫描结果过滤（实验性，不改变真实联网）"),
        Flag("need_downgrade_to_2g", "电话/基站信息使用 CDMA 格式模拟（实验性，不切换真实网络）"),
        Flag("vendor_fused", "系统/厂商融合进程增强（需额外 LSPosed 作用域）"),
        Flag("mock_gnss", "模拟卫星状态（实验性，需系统回调支持）"),
        Flag("enable_nmea", "改写 NMEA 定位语句（保留合法校验和）"),
        Flag("enable_agps", "允许 AGPS 相关系统行为"),
        Flag("enable_debug_log", "模块调试日志（可能包含位置信息）"),
        Flag("disable_get_current_location", "屏蔽单次 getCurrentLocation（兼容选项）"),
        Flag("disable_fused_location", "屏蔽系统 fused 提供器（不等同厂商融合适配）"),
        Flag("disable_network_location", "屏蔽 network 提供器"),
        Flag("disable_request_geofence", "屏蔽地理围栏请求"),
        Flag("disable_get_from_location", "屏蔽系统地理编码请求"),
        Flag("disable_register_location_listener", "停用主动监听分发（保留正常系统注册与回调）"),
        Flag("publish_callbacks", "周期主动分发位置回调", true)
    )
}
