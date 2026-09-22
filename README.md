# YSpace

YSpace 是一个面向 Android 16+ 的轻量应用隔离与运行时环境观察项目。

它不把 Work Profile 做成第二套“完整桌面”。日常入口只有一个应用列表：点击未隔离应用即可加入工作资料，点击已隔离应用即可直接启动隔离版本。工作资料中的 YSpace 同时作为 Probe，保存该资料内目标 App 的真实运行时环境访问记录。

## 当前实现

- 使用 Android 原生 `ACTION_PROVISION_MANAGED_PROFILE` 创建 Managed Profile。
- 主界面只显示应用列表，不实现额外“工作空间桌面”。
- Root 快速加入：`cmd package install-existing --user <workUser> <package>`。
- 通过 `LauncherApps` 直接启动工作资料中的隔离版本。
- 默认限制跨资料剪贴板、联系人搜索和来电识别。
- 同一个 APK 同时提供 libxposed API 102 观察模块。
- 观察模块只调用原始方法并记录结果，不修改参数、返回值、异常或完整性 verdict。
- 每次目标进程启动建立独立 Session。
- 诊断页只显示实际发生过的访问；没有访问过的项目不会显示“未检测”。
- 诊断状态明确区分“Observer 未注入”和“Observer 已注入但尚无真实检测调用”。
- 主界面和 App 长按菜单提供 LSPosed 诊断设置入口。

## 当前可观察对象

- PackageManager：具体包名查询、应用枚举、Intent 解析。
- 文件环境：目标 App 实际访问过的 `/proc`、`/sys`、`/system`、`/vendor`、`/data/adb` 等环境相关路径。
- 系统属性：实际读取的 property key。
- Settings：实际读取的 Secure / System / Global key。
- Runtime.exec：实际执行的命令。
- 类探测：实际查询的 Xposed / LSPosed / Magisk / KernelSU / Zygisk / Frida 等类名。
- Debugger、Overlay、Accessibility、Telephony、Connectivity、DNS。
- Play Integrity / SafetyNet Java API 请求入口（目标 App 使用对应 SDK 时）。

敏感标识符的返回值默认不落库，只显示是否为空；诊断重点是“目标 App 检测了什么对象”，不是收集个人数据。

## 重要边界

YSpace 的运行时 Observer 是 Java / Android Framework 层观察器。Native 代码直接发起的 syscall、内联汇编、独立 native anti-tamper 检查目前不会被伪装成“未检测”；诊断页会明确显示当前覆盖层。

LSPosed 注入本身可能改变某些高安全 App 的行为。因此 YSpace 把“隔离”和“观察”分开：Work Profile 可以单独使用；只有需要研究真实调用对象时才给目标 App 启用 YSpace 的 LSPosed 作用域。

YSpace 不实现 Play Integrity、硬件证明、银行安全策略或其他完整性验证的伪造/绕过。

## 使用

1. 安装 YSpace。
2. 打开后点“初始化隔离”，完成 Android 系统的一次 Managed Profile 确认。
3. 回到 YSpace，点击应用即可加入隔离；再次点击已隔离应用直接启动。
4. 若要记录真实检测对象，点击 YSpace 的“LSPosed 诊断设置”。
5. 在 LSPosed 中启用 YSpace，切换到 **Work Profile / 工作资料用户**，只把需要观察的工作资料版本目标 App 加入作用域。
6. 不需要勾 Android、SystemUI、Launcher、YSpace 或 Google Play 服务；只有目标 App 使用独立安全组件 APK 时，才把那个独立包额外加入作用域。
7. 强制停止并重新打开目标 App。
8. 在 YSpace 点“真实检测记录”。如果显示“Observer 已注入”，说明 Hook 链已工作；如果显示“Observer 未注入”，应先修正 LSPosed 作用域，而不是把空白结果理解成“目标 App 没有检测”。

当前一键加入后端使用 Root，因此非常适合 KernelSU / Magisk 设备。后续可以增加 Connected Apps/Freighter 风格的非 Root APK 传输后端，而不改变主界面。

## 构建

要求：

- JDK 17
- Gradle 9.4.1
- Android SDK 36（Android 16）
- AGP 9.2.0

```bash
gradle :app:assembleDebug
```

libxposed 使用官方 modern API：

```text
io.github.libxposed:api:102.0.0
```

并且只以 `compileOnly` 方式参与构建。

## 参考方向

项目架构参考 Android 官方 Managed Profile / CrossProfileApps 能力，以及 Fortress/Freighter、Shelter 等 Work Profile 项目的设计思路；运行时观察部分采用独立实现，没有复制这些项目源码。

## License

MIT
