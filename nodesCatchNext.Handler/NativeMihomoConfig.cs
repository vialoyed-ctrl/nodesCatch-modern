using System;
using System.Collections.Generic;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using nodesCatchNext.Mode;

namespace nodesCatchNext.Handler;

// JSON is also valid YAML. Serialize values to preserve passwords and IPv6 safely.
public static class NativeMihomoConfig
{
    public static string BuildExport(Config config, IEnumerable<int> selected)
    {
        var proxies = new JArray();
        var names = new JArray();
        var used = new HashSet<string>(StringComparer.Ordinal) { "PROXY", "DIRECT", "REJECT", "GLOBAL" };
        foreach (int index in selected)
        {
            if (index < 0 || index >= config.vmess.Count) continue;
            var node = config.vmess[index];
            string basis = ExportNaming.Name(node);
            if (string.IsNullOrWhiteSpace(basis)) basis = "node-" + (index + 1);
            string name = basis;
            for (int suffix = 2; !used.Add(name); suffix++) name = basis + " (" + suffix + ")";
            proxies.Add(Proxy(node, name));
            names.Add(name);
        }
        if (proxies.Count == 0) throw new Exception("没有选中可导出的节点");
        return new JObject {
            ["mixed-port"] = 7890, ["allow-lan"] = false, ["mode"] = "rule", ["log-level"] = "info",
            ["proxies"] = proxies,
            ["proxy-groups"] = new JArray(new JObject { ["name"] = "PROXY", ["type"] = "select", ["proxies"] = names }),
            ["rules"] = new JArray("MATCH,PROXY")
        }.ToString(Formatting.Indented);
    }

    public static string Build(Config config, IEnumerable<int> selected)
    {
        var proxies = new JArray();
        var listeners = new JArray();
        var names = new JArray();
        foreach (int index in selected)
        {
            if (index < 0 || index >= config.vmess.Count) continue;
            int port = config.GetLocalPort("speedtest") + index;
            if (port < 1 || port > 65535) throw new Exception("测速端口超出范围，请减少节点数量或调低本地端口");
            string name = port.ToString();
            proxies.Add(Proxy(config.vmess[index], name));
            names.Add(name);
            listeners.Add(new JObject { ["name"] = "test-" + name, ["type"] = "mixed",
                ["listen"] = "127.0.0.1", ["port"] = port, ["proxy"] = name });
        }
        if (proxies.Count == 0) throw new Exception("没有选中可测速的节点");
        return new JObject {
            ["mixed-port"] = config.localPort, ["allow-lan"] = false,
            ["bind-address"] = "127.0.0.1", ["mode"] = "rule", ["log-level"] = "warning",
            ["external-controller"] = "127.0.0.1:" + config.externalControllerPort,
            ["proxies"] = proxies, ["listeners"] = listeners,
            ["proxy-groups"] = new JArray(new JObject { ["name"] = "PROXY", ["type"] = "select", ["proxies"] = names }),
            ["rules"] = new JArray("MATCH,PROXY")
        }.ToString(Formatting.Indented);
    }

    public static JObject Proxy(VmessItem n, string name)
    {
        var p = new JObject { ["name"] = name, ["server"] = n.address, ["port"] = n.port };
        switch (n.configType)
        {
            case 1:
                p["type"] = "vmess"; p["uuid"] = n.id; p["alterId"] = n.alterId;
                p["cipher"] = string.IsNullOrEmpty(n.security) ? "auto" : n.security;
                break;
            case 3:
                p["type"] = "ss"; p["cipher"] = n.security; p["password"] = n.id;
                if (!string.IsNullOrEmpty(n.plugin))
                {
                    var parts = n.plugin.Split(';');
                    p["plugin"] = parts[0] == "obfs-local" ? "obfs" : parts[0];
                    var opts = new JObject();
                    for (int i = 1; i < parts.Length; i++) {
                        var kv = parts[i].Split(new[] {'='}, 2);
                        string key = kv[0] == "obfs" ? "mode" : kv[0] == "obfs-host" ? "host" : kv[0];
                        if (key.Length > 0) opts[key] = kv.Length > 1 ? (JToken)kv[1] : new JValue(true);
                    }
                    p["plugin-opts"] = opts;
                }
                return p;
            case 4: case 7: case 8:
                p["type"] = n.configType == 4 ? "socks5" : "http";
                if (!string.IsNullOrEmpty(n.security)) p["username"] = n.security;
                if (!string.IsNullOrEmpty(n.id)) p["password"] = n.id;
                if (n.configType == 8) p["tls"] = true;
                Tls(p, n); return p;
            case 5: p["type"] = "vless"; p["uuid"] = n.id;
                if (!string.IsNullOrEmpty(n.flow)) p["flow"] = n.flow;
                break;
            case 6: p["type"] = "trojan"; p["password"] = n.id; break;
            case 9:
                p["type"] = "ssr"; p["cipher"] = n.security; p["password"] = n.id;
                p["protocol"] = n.network; p["obfs"] = n.headerType;
                p["protocol-param"] = n.streamSecurity ?? ""; p["obfs-param"] = n.sni ?? "";
                return p;
            case 11:
                p["type"] = "hysteria2"; p["password"] = n.id;
                if (!string.IsNullOrEmpty(n.path)) { p["obfs"] = n.path; p["obfs-password"] = n.requestHost; }
                if (!string.IsNullOrEmpty(n.publicKey)) p["fingerprint"] = n.publicKey;
                Tls(p, n); return p;
            case 12: p["type"] = "anytls"; p["password"] = n.id; Tls(p, n); return p;
            default: throw new Exception("不支持的节点类型: " + n.configType);
        }
        p["udp"] = true;
        if (n.streamSecurity == "tls" || n.streamSecurity == "reality") p["tls"] = true;
        Tls(p, n);
        if (n.streamSecurity == "reality") {
            if (string.IsNullOrEmpty(n.publicKey)) throw new Exception("Reality 节点缺少 public-key");
            p["tls"] = true;
            p["client-fingerprint"] = string.IsNullOrEmpty(n.fingerprint) ? "chrome" : n.fingerprint;
            p["reality-opts"] = new JObject { ["public-key"] = n.publicKey, ["short-id"] = n.shortId ?? "" };
        }
        string network = string.IsNullOrEmpty(n.network) ? "tcp" : n.network;
        switch (network) {
            case "tcp":
                if (n.headerType == "http") throw new Exception("暂不支持 TCP HTTP 伪装，请改用节点的 WS/gRPC 传输");
                break;
            case "ws":
                p["network"] = "ws";
                var ws = new JObject { ["path"] = string.IsNullOrEmpty(n.path) ? "/" : n.path };
                if (!string.IsNullOrEmpty(n.requestHost)) ws["headers"] = new JObject { ["Host"] = n.requestHost };
                p["ws-opts"] = ws; break;
            case "grpc": p["network"] = "grpc"; p["grpc-opts"] = new JObject { ["grpc-service-name"] = n.path ?? "" }; break;
            case "http": case "h2":
                p["network"] = "h2";
                p["h2-opts"] = new JObject { ["host"] = new JArray((n.requestHost ?? "").Split(',')), ["path"] = string.IsNullOrEmpty(n.path) ? "/" : n.path }; break;
            default: throw new Exception("当前内核配置不支持传输方式: " + network);
        }
        return p;
    }

    private static void Tls(JObject p, VmessItem n)
    {
        p["skip-cert-verify"] = string.Equals(n.allowInsecure, "true", StringComparison.OrdinalIgnoreCase) || n.allowInsecure == "1";
        if (!string.IsNullOrEmpty(n.sni)) p[n.configType == 1 || n.configType == 5 ? "servername" : "sni"] = n.sni;
        if (!string.IsNullOrEmpty(n.fingerprint) && n.configType != 11) p["client-fingerprint"] = n.fingerprint;
    }
}
