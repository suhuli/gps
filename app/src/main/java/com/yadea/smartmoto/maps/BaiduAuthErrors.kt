package com.yadea.smartmoto.maps

/** Do not display SDK payloads: they can contain credentials or device identifiers. */
object BaiduAuthErrors {
    fun message(status: Int): String = when (status) {
        0 -> "百度地图鉴权成功"
        240 -> "百度地图鉴权失败（240）：APP 服务被禁用，请到百度控制台检查此 Android AK 的应用状态及所需服务权限；这不是 SHA-1 / 包名校验错误。"
        230 -> "百度地图鉴权失败（230）：Mcode 校验失败，请核对百度控制台登记的 SHA-1 与实际安装包名。"
        200 -> "百度地图鉴权失败（200）：AK 不存在，请检查 Android Key 配置。"
        201 -> "百度地图鉴权失败（201）：用户被禁用，请检查百度账号状态。"
        203 -> "百度地图鉴权失败（203）：应用类型错误，请使用 Android SDK 类型的 Key。"
        else -> "百度地图鉴权失败（$status），请按百度官方错误码检查网络、Android Key、应用状态及服务权限。"
    }
}
