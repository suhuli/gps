# 雅迪智行：双地图构建说明

## 构建身份

- 默认安装包名：`com.yadea.smartmoto`，源码 namespace：`com.yadea.smartmoto`。
- 固定证书 SHA-1：`DB:08:33:1A:25:37:F2:BC:72:0D:54:A8:17:2B:E5:38:89:AF:89:95`。
- debug / release 使用同一已有证书。不得重新生成签名或使用原作者的密钥。
- 固定密钥缺失时，APK 打包失败，不回退到随机 debug 签名。
- 版本：2.6.0-beta4 / versionCode 262。仅作为待真机验证的测试版本。

## SDK 和工具链（2026-10-25 锁定）

| 组件 | 版本 |
| --- | --- |
| 百度地图 / 搜索 | 8.2.0.2 |
| 百度定位 | 9.7.0 |
| 高德三合一 Maven 包 | 11.3.100_loc11.3.101_sea9.8.1 |
| MMKV（保留 API 21 支持的 1.x 分支） | 1.3.17 |
| Android Gradle Plugin | 8.6.1 |
| Gradle | 8.7 |
| Kotlin | 1.9.25 |
| 构建 JDK | 17 |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 21 |

高德组合包仅提供 ARM 原生库，因此本版本只输出 `armeabi-v7a` 和 `arm64-v8a`；不声称支持 x86 模拟器。Kotlin Android Extensions 已移除，改用 DataBinding / ViewBinding；不再依赖 FreeReflection，不再修改用户的动画缩放设置。

## Actions

仓库仅保留 main 主线，Actions 仅允许构建 main；源码与辅助脚本固定到同一工作流提交。

在 **Build Android APK → Run workflow** 中保持唯一的 main 源分支，选择包名、构建类型，可填写：

- `baidu_map_key`：百度 Android Key；留空读取 `BAIDU_MAP_KEY` Secret。
- `amap_map_key`：高德 Android Key；留空读取 `AMAP_MAP_KEY` Secret。

两家 Key 分别绑定真实包名和上面的固定签名。若平台区分调试和发布证书，两项均填相同 SHA-1。输入优先于 Secret；没有 Key 时不使用原作者的 Key，应用选择对应地图时会提示重新配置。

**手动输入框不是 Secret。** 虽然辅助脚本遮罩日志且不把 Key 提交到 Git，运行参数、事件文件及 APK 中的 Key 仍可能被读取。优先用仓库 Secret，并在平台限制 API 权限、配额，验证后更换临时 Key。

已有四个签名 Secrets 保持原值：

```
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

构建会执行坐标转换和轨迹解析单元测试；验证实际 APK 名称、包名、固定证书、ARM64 ELF LOAD 对齐和 ZIP 16 KB 对齐。这些检查不能代替手机执行测试。

本地沙箱内存仅约 2 GB，首个 beta 的本地打包排除了 Lint Vital 分析任务；不把它宣称为 Lint 通过。Actions 未排除 Lint，并为 Gradle 提供 3 GB 堆，但远端执行仍需有效仓库写入授权与实际运行验证。

## 本地构建

安装 JDK 17、Android Platform 35、Build Tools 35.0.0，配置 `ANDROID_HOME` 或 `local.properties`。

通过进程环境设置两个地图 Key 和已有签名文件，不要把配置值写入源码：

```
BAIDU_MAP_KEY
AMAP_MAP_KEY
GPS_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

然后执行：

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
```

## 使用与能力边界

- 首次启动先阅读隐私说明，用户同意后再初始化所选地图服务。未选择地图不创建地图渲染器或位置客户端。
- 启动可选择百度或高德并勾选“记住选择”；扩展 → 模拟功能设置可重新切换。
- 切换地图会停止现有模拟并清空当前路线，历史地点保留。
- 地图主界面、选点搜索、逆地理编码、路线选择、路线生成、模拟页和悬浮窗均使用同一所选服务。
- 长按导航按钮可选择驾车、普通骑行、步行、电动车。
- 高德原生 Android 搜索 SDK 无电动车专用算路接口：高德电动车请求会明确拒绝，要求改用普通骑行或百度电动车模式，不会静默替换路线类型。
- 高德普通骑行和步行途经点按段串联；百度步行不支持本实现的途经点，明确提示移除。
- 境外转换判断修复了新加坡/马来西亚误偏移，但当前仍是大陆/海南粗略范围判断，不是精确行政边界；边境地区和其他相邻国家必须单独核对，不宣称全球坐标转换已完全准确。
- 内部路线统一为 GCJ-02，仅写入系统模拟位置前转换为 WGS84；已有历史和输入模型暂保留百度 `LatLng` 作为兼容数据容器，不代表调用百度地图服务。
- 文件导入支持系统文件选择器或应用内文件列表，单文件最多 5 MB / 50000 点；完整校验成功后才替换路线。支持 `经度,纬度;经度,纬度;` 格式及 UTF-8 BOM。
- 模拟通过前台服务运行，通知提供暂停/继续/停止。系统仍可能受厂商电池管理策略限制。
- 不再申请手机状态、全部应用列表、系统日志或“始终允许”的后台定位权限；启动位置前台服务仍需精确位置授权。Android 13+ 建议允许通知。
- 外部广播 action 使用实际安装包名 + `.navi`（默认 `com.yadea.smartmoto.navi`），并要求 `${applicationId}.permission.CONTROL_MOCK_LOCATION` 签名级权限。外部应用必须同签名并声明该权限；仍受 Android 后台启动限制，不保证旧的任意应用广播或 adb 命令兼容。
- 原作者 Bugly 上报和原作者应用更新通道已停用。没有新建任何遥测服务器。
- 撤回隐私同意会停止模拟、清除本应用地点历史和地图偏好、退出进程；已导入和保存的轨迹文件不会自动删除。第三方服务已处理的数据应按相应政策联系服务方处理。

## 必须执行的真机验收

分别检查两张地图的有效 Key、网络断开、搜索和逆地理编码、各路线类型与途经点、路线切换、文件导入、锁屏与返回前台、通知操作、撤回权限、两家服务反复切换、旧版本同签名升级、ARM64 16 KB 页大小设备。编译成功不是这些功能已通过验收。

本阶段没有声称完成所有 UI 组件的全面升级、应用商店审核、完整第三方隐私合规审计或模拟定位识别规避。部分非核心旧 UI 依赖暂保留，以控制双地图升级风险。

## 原作者声明

保留原项目作者署名、历史和 README 中的使用/分发限制；本次修改不新增 MIT、Apache 等宽松许可，也不改变原项目授权条件。公开仓库不代表允许任意分发或商业使用。

## beta2 清理与百度鉴权

源码 namespace、Java/Kotlin 包路径、Manifest 组件、XML 自定义视图和测试统一为 `com.yadea.smartmoto`。默认 applicationId 和固定证书不变，无须因为源码迁移更换地图 Key 的包名或 SHA-1。广播调用方须改用新 action；不再注册旧 action。

原作者版本检查 API、响应模型、HTTP 客户端、更新按钮/图标/弹窗和旧下载链接/二维码已从当前源码移除；扩展页不再列出 GitHub 地址或 APK 下载地址。原作者署名、许可证/使用限制和 Git 历史保留。

百度官方错误码：240 = APP 服务被禁用；230 = APP Mcode 校验失败。240 优先检查百度控制台该 AK 的 Android SDK 应用类型、应用是否启用、相关服务权限，不通过改源码 namespace 或更换固定签名来规避服务授权。控制台服务开通后先完全退出并重启应用复测；若换了 AK，需要重新构建。

官方错误码说明：https://lbsyun.baidu.com/docs/android?title=androidsdk/guide/addition-func/errorcode

## beta3 构建配置修正

用户确认此前提供的百度 Android Key 有误，本版通过仓库 Secret 更新为用户纠正后的 Key。完整 Key 不进入源码；高德 Key、安装包名及固定签名保持不变。版本号递增至 261，便于覆盖旧测试版。在线鉴权需以设备复测为准，不将成功打包等同于 240 已解决。

## beta4 启动界面

使用用户提供的图标，包含常规多密度与自适应启动图标。首次使用先显示“进入雅迪智行”及未默认勾选的隐私同意，再选择地图。选择成功后记住配置，下次直接进入主界面。独立的隐私弹页已移除，勾选同意之前不初始化地图 SDK。设置可切换地图或撤回同意；必要定位/模拟位置权限缺失时才重新显示引导。已有同意与地图配置可沿用。地图 Key 与固定证书不变。

## 唯一主线基准

当前唯一主线版本为 2.6.0-beta4 / 262，用户已验证。旧分支、旧版本标签和旧 Actions 构建已清理，Git 提交历史保留。唯一 Release v2.6.0-beta4 永久保存用户已验证的原始 APK；Actions 附件仍按保留期过期。没有更改应用代码、地图 Secrets 或固定证书。本次仅维护构建入口和说明文档，不产生新应用版本。
