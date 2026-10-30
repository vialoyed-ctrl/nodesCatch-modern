# nodesCatch Modern

Windows 代理节点延迟、下载测速和导出工具。基于 [AHackerX/nodesCatchNext](https://github.com/AHackerX/nodesCatchNext) 维护版源码继续修改，原项目源自不良林的 nodesCatch V2.0。保留上游 MIT 许可与署名。

**安卓预览版：**独立的节点导入、测速、排序和导出 APK，Android 8.0 起，无需 Root、无需运行其他代理客户端。[安卓说明与源码](android/README.md) · [安卓 APK 下载](https://github.com/vialoyed-ctrl/nodesCatch-modern/releases/tag/android-v0.1.3)。安卓子项目使用 GPL-3.0，Windows 项目继续使用 MIT。

## 下载与启动

前往 [Releases](https://github.com/vialoyed-ctrl/nodesCatch-modern/releases/latest) 下载 `nodesCatch-modern-v4.1.1-windows-x64.zip`，解压后双击 **Start.cmd**。

需要 Windows x64 和 .NET Framework 4.8。首次启动需要连接 GitHub，脚本会从上游下载 Mihomo v1.19.33，并按固定 SHA256 检查下载文件；也会尝试安装可选的订阅转换器。无需管理员权限。内核和转换器未内置到本仓库的下载包，来源见 [第三方说明](THIRD_PARTY_NOTICES.md)。

如果下载内核失败，可手动从 [Mihomo 官方发布页](https://github.com/MetaCubeX/mihomo/releases/tag/v1.19.33) 下载 Windows x64 compatible 版本，把解压后的 EXE 命名为 `mihomo-nodes.exe` 放在程序旁，再运行 Start.cmd。可选转换器下载失败不影响原生协议的测速与 Mihomo 导出；需要旧格式转换时可参照第三方说明安装。

下载包不含任何节点、订阅或历史测速记录。首次启动会使用空白配置；已有 `nodeConfig.json` 不会被启动脚本覆盖。

## 使用

1. 导入自己的有效节点或订阅。分享链接可直接 Ctrl+V；Clash/Mihomo YAML 沿用上游解析器。
2. 选中节点，Ctrl+R 测 HTTPS 延迟，Ctrl+T 测下载速度。
3. 导出分享链接、Base64 订阅或 Mihomo 配置时，平均速度会放到节点名称最前面：

   ```text
   5.23MB/s 日本东京01
   512KB/s 新加坡02
   ```

没有有效速度结果时保留原名，重复导出不叠加速度前缀。导出命名不改动列表中的原名。Mihomo 导出的重名会添加序号。

从 v4.1.1 起，**导出 Mihomo 配置文件**会自动加入以下规则，并使用规则模式：

```yaml
rules:
  - DOMAIN-SUFFIX,qq.com,DIRECT
  - DOMAIN-SUFFIX,gtimg.com,DIRECT
  - DOMAIN-SUFFIX,qpic.cn,DIRECT
  - PROCESS-NAME,QQ.exe,DIRECT
  - MATCH,PROXY
```

将导出的 YAML 导入 Clash Verge 后，常见 QQ 域名和可识别的 QQ.exe 流量会优先直连，其余连接走 PROXY。进程规则依赖客户端能够识别进程；域名规则可以匹配浏览器中的相关请求。更新规则后，重新建立的连接才按新规则路由。

分享链接和 Base64 通用订阅只携带节点信息，无法附带分流规则；要自动带入规则，请使用 Mihomo 配置文件导出。

软件自带独立的 Mihomo 进程，不需要打开其他 Clash 客户端。其他代理客户端的 TUN 模式可能影响测试路径。

下载默认使用 `https://proof.ovh.net/files/100Mb.dat`，可在设置中修改。每个节点下载约 10 秒或文件下载完成时结束；100MB 是文件大小，并非固定消耗。批量测速会累加流量。先测延迟再挑选少量节点测下载，可以节省流量。HTTP 403 是下载请求遭到拒绝，不应仅凭这一项判断节点失效。

## 本版修改

- 测速配置直接交给 Mihomo，绕过旧版订阅转换器的协议限制。
- 为每个节点建立独立本地代理端口，下载测速按设置的并发数进行。
- 支持当前模型可表达的 VMess、Shadowsocks/SS2022、Trojan、VLESS/Reality、Hysteria2、AnyTLS、HTTP/SOCKS 和 SSR 节点。
- 修复空 short-id 的 Reality 节点被跳过、配置字符串转义、延迟 API URL 编码及配置加载超时。
- 修复下载峰值按实际采样间隔计算；延迟 API 请求不经过系统代理。
- 分享链接、Base64 和 Mihomo 配置导出加入平均速度前缀；Mihomo 导出使用原生配置生成。

## 范围与验证

支持常见 TCP、WebSocket、gRPC 和 H2 配置；未增加 TUIC/WireGuard 链接导入，不支持 XHTTP、mKCP 等特殊传输。Clash YAML 解析器可能无法保留某些高级字段。未知或不支持的配置应使用完整支持该格式的客户端。

本地已验证现代协议样例的导入/内核配置、回环延迟和下载测速，以及导出前缀的中文、引号、重名、错误结果和多协议编码。协议配置通过检查不代表任意远程服务器都可连接。可选转换器、推送等上游功能不属于本版完整验证范围。

## 构建与测试

安装 .NET 8 SDK 和 .NET Framework 4.8 开发工具，Windows 下运行：

```powershell
dotnet build nodesCatchNext.csproj -c Release
dotnet build tests/ExportRegression.csproj -c Release
Copy-Item release/nodeConfig.example.json bin/Release/net48/nodeConfig.example.json
./scripts/install-runtime.ps1 -Destination ./bin/Release/net48
./tests/bin/Release/net48/ExportRegression.exe ./bin/Release/net48
./scripts/package.ps1
```

GitHub Actions 会构建并验证导出，产物只包含明确允许的程序、示例配置、启动脚本和许可文件。用户配置、日志、缓存和运行时内核均被排除。

## 许可与致谢

应用源码使用 [MIT](LICENSE)。感谢 [不良林](https://bulianglin.com/archives/nodescatch.html)、[AHackerX/nodesCatchNext](https://github.com/AHackerX/nodesCatchNext)、[MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) 和 Newtonsoft.Json。依赖遵循各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
