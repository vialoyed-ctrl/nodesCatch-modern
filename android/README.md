# 节点测速 Android

独立的安卓节点导入、测速、排序和导出工具。原生 Java 界面，Android 8.0（API 26）起，面向 API 35；兼容设置参考同账号的 floating 等安卓项目。无需 Root、LSPosed，也不建立全局 VPN。内置 Mihomo，不需要运行其他 Clash 应用。

## 功能

- 粘贴节点分享链接、Base64 通用订阅，或从系统文件选择器导入完整 Clash/Mihomo YAML。
- 支持常见 VMess、VLESS/Reality、Trojan、SS/SS2022、Hysteria2、AnyTLS、HTTP/SOCKS 链接；SSR 支持常见链接格式。
- 通过 URL 获取订阅；从“更多 → 更新订阅”选择已有订阅更新，失败保留旧节点。
- 延迟并发 4，下载逐个进行；显示平均速度、峰值和状态，按速度、延迟或名称排序。
- 下载默认每节点最多 10 秒或 10MB，可调整时长、流量上限与测试地址。离开应用停止测速。
- 导出选中节点为完整 Mihomo YAML、分享链接或 Base64 订阅。YAML 保留原始代理字段，速度加入名称前缀，并添加 QQ 直连规则（含 Windows QQ.exe 和安卓 com.tencent.mobileqq）。
- 节点及订阅仅存放在应用私有目录，禁用系统备份，不向本项目服务器上传。没有附带任何真实节点。

## 使用

1. 安装 APK，点击“＋ 导入”，添加自己的节点或订阅。
2. 选择节点，先“测延迟”，再按需“测下载”。下载会消耗节点和手机流量。
3. “更多”可排序、全选、筛选和更新订阅。
4. “导出 → Mihomo YAML”会包含分流规则；链接和 Base64 无法携带分流规则。

QQ 进程分流依赖接收配置的代理客户端具备进程识别能力。已有连接需要重新建立才能按新规则路由。

## 支持范围

YAML 导入可保留内核支持的高级字段，但本应用不解析远程 proxy-providers。链接导入只解析已实现的协议字段；复杂参数或 SS 插件节点建议使用完整 YAML 导出以避免参数损失。纯测速工具不会让手机的其他应用使用代理。首次预览版需要实机验证，模拟器测试不能替代特定 ColorOS 机型测试。

## 构建

需要 JDK 21、Android SDK 36。Windows 先执行 `scripts/prepare-core.ps1`，或从固定上游版本获取 AAR 放入 `app/libs/`：

```text
https://github.com/oviron/libmihomo-android/releases/download/v0.3.12/libmihomo-android-v0.3.12.aar
SHA256 c5f857d00319a7690b1dbf083387f5617454ed5a21eb87488644de2c18b7e42f
```

然后运行：

```sh
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug
```

CI 会在 Android 15 x86_64 模拟器中调用实际 JNI 内核，通过本地 HTTP 节点完成延迟和限流下载测试。

## 许可

本 `android/` 子项目使用 GPL-3.0，完整文本见 [LICENSE](LICENSE)。根目录 Windows 项目继续使用其原 MIT 许可。

内核桥接库：[oviron/libmihomo-android v0.3.12](https://github.com/oviron/libmihomo-android/tree/v0.3.12)，GPL-3.0；内核 [MetaCubeX/mihomo v1.19.33](https://github.com/MetaCubeX/mihomo/tree/v1.19.33)，GPL-3.0。固定 AAR 包含它们的 native 库，发布 APK 时一起分发；对应源码、构建过程和依赖可在上述精确版本获取，并随 APK 发布对应源码归档。

SnakeYAML 2.3、Kotlin 标准库及 Gradle wrapper 使用 Apache-2.0 许可；测试依赖 JUnit 4 使用 EPL-1.0，AndroidX 测试库使用 Apache-2.0。应用不打包测试 APK。
