# 使用 GitHub Actions 构建自定义包名 APK

进入 **Actions → Build Android APK → Run workflow**：

| 输入项 | 默认值 | 说明 |
|---|---|---|
| 工作流分支 | `main` | 保留 main，以使用本仓库的构建脚本 |
| `source_branch` | `dev` | 选择 dev（2.5.8）或 main（2.5.5）源码 |
| `application_id` | `com.suhuli.gps` | 自定义 Android 包名，使用小写字母、数字、下划线和点；每段以字母开头 |
| `build_type` | `release` | release 或 debug，均使用同一份固定专用签名 |

构建完成后，在该运行页面的 **Artifacts** 下载 ZIP，其中包含：
- APK
- `BUILD-INFO.txt`：实际包名、源码分支、构建类型、SHA-1
- `signing-certificate.txt`：APK 实际签名证书指纹

Artifacts 保留 14 天，不自动发布 Release。

## 固定签名与 SHA-1

本项目已经配置独立的持久签名，私钥和密码只保存为 Actions Secrets，不提交到公开代码。

```text
SHA-1: DB:08:33:1A:25:37:F2:BC:72:0D:54:A8:17:2B:E5:38:89:AF:89:95
```

SHA-1 由证书决定，不能任意输入。修改包名、切换 main/dev 或 debug/release 不会改变本证书指纹。
构建会核对 APK 的实际包名和 SHA-1；签名 Secrets 缺失时直接失败，不生成临时替代签名。

签名 Secrets 为：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
不要随意删除或替换。私钥文件和密码应另外安全离线备份。APK 下载包不含私钥或密码。
若确实需要换用你已有的签名，需同时替换这四个 Secrets，并更新工作流的预期证书指纹。

## 百度地图 Key

APK 编译成功不代表地图、搜索、算路一定能用。
在百度控制台按**本次自定义包名 + 上述 SHA-1**申请/配置 Android Key，再添加到：

**Settings → Secrets and variables → Actions → New repository secret → `BAIDU_MAP_KEY`**。

未配置时会保留上游源码中的 Key，但它通常不能匹配新包名和新证书。切换包名后应同步调整地图 Key。
原作者的签名文件不用于本工作流的 APK 签名。

## 安装及兼容性

- 相同包名、相同证书：满足 Android 的版本号等条件时可覆盖安装更新。
- 不同包名：视为不同应用，通常可共存，应用数据不自动迁移。
- 与原作者 APK 或此前临时调试签名不同：相同包名时无法直接覆盖安装，可能需先卸载旧版本。
- 这里只更改安装包 applicationId，代码 namespace 保留原值，避免破坏资源及代码引用。
- 应用内部的广播 action 仍兼容原项目；自动化调用时可通过目标包名限制接收应用。
- API 31、Gradle 7.0.2、AGP 7.0.4、Java 11 构建；Android SDK 工具安装使用 Java 17。
- 不包含真机验证，外部依赖仓库不可用时仍可能构建失败。

## 授权

公开可见不等于获得宽松开源授权。原项目 README 的作者声明、使用与分发限制不变；本仓库没有新增 MIT/Apache 等许可证。
