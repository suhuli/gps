# 使用 GitHub Actions 构建 APK

1. 打开本仓库的 **Actions → Build Android APK**。
2. 点击 **Run workflow**；工作流分支保留 `main`。
3. `source_branch` 可选择 `dev`（默认，2.5.8）或 `main`（2.5.5）。
4. 等待运行结束，在该运行页面底部的 **Artifacts** 下载 `gps-分支-debug-编号` ZIP。
5. 解压得到可安装的 debug APK。Artifacts 保留 14 天，不会自动发布 Release。

## 构建环境

- Ubuntu 22.04
- Java 17：安装 Android SDK 工具
- Java 11：运行项目原有 Gradle 7.0.2 / Android Gradle Plugin 7.0.4
- Android SDK Platform 31；Build Tools 30.0.3 / 29.0.3
- 构建任务：`:app:assembleDebug`

## 百度地图 Key 与签名

APK 编译成功不代表地图、搜索或算路一定可用。

可以在 **Settings → Secrets and variables → Actions → New repository secret** 中添加
`BAIDU_MAP_KEY`。该 Key 必须匹配百度控制台登记的包名和本次 APK 签名证书 SHA-1。
包名仍为原项目的 `com.huolala.mockgps`。

本工作流只在 CI 工作目录修改 debug 签名，不使用源码中公开的原作者共享签名。
没有配置自己的持久签名时，不同运行生成的调试证书可能不同；可能需要卸载旧包，
且百度 Key 的签名绑定需要相应调整。正式发布应另行配置由你保管的固定签名和地图 Key。
工作流会在 **Show APK signing certificate** 步骤输出本次证书指纹。

## 范围与授权

工作流是手动触发，不会因普通 push 或外部 PR 自动运行。GITHUB_TOKEN 只有代码读取权限。
当前只提供测试用 debug APK 构建，不提供正式 release 签名，也不验证真机功能。
依赖镜像、JitPack 和旧版依赖不可用时，仍可能需要维护构建配置。

公开仓库不等同于授予开源许可证。原项目 README 的作者声明和使用限制保持不变；
本仓库没有新增 MIT/Apache 等授权，也不改变原作者的权利。
