package com.vialoyed.nodescatch;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public class NodesTest {
    private static final String UUID="00000000-0000-4000-8000-000000000001";
    @Test public void modernLinksAndRoundTrip() throws Exception {
        String ss=Base64.getEncoder().encodeToString("2022-blake3-aes-128-gcm:AAAAAAAAAAAAAAAAAAAAAA==".getBytes(StandardCharsets.UTF_8));
        String[] links={"vless://"+UUID+"@example.com:443?security=reality&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=&fp=chrome&flow=xtls-rprx-vision#%E6%97%A5%E6%9C%AC",
            "hy2://pass%22word@example.com:443?sni=example.com&obfs=salamander&obfs-password=test#HY2",
            "anytls://password@example.com:443?sni=example.com#AnyTLS","ss://"+ss+"@example.com:443#SS2022",
            "trojan://password@example.com:443?sni=example.com&type=ws&path=%2Fproxy&host=example.com#Trojan"};
        for(String link:links){List<Nodes.Node> imported=Nodes.parse(link);assertEquals(1,imported.size());Nodes.Node n=imported.get(0);String before=n.name();n.average=5.23*1048576;String exported=Nodes.links(imported,false);Nodes.Node round=Nodes.parse(exported).get(0);assertEquals(n.proxy.getString("type"),round.proxy.getString("type"));assertEquals("5.23MB/s "+before,round.name());assertEquals(before,n.name());}
        assertEquals("",Nodes.parse(links[0]).get(0).proxy.getJSONObject("reality-opts").getString("short-id"));
    }
    @Test public void yamlPreservesAdvancedFieldsAndExportRules() throws Exception {
        List<Nodes.Node> list=Nodes.parse("proxies:\n - name: '节点 #1'\n   type: hysteria2\n   server: example.com\n   port: 443\n   password: 'p:a#s'\n   alpn: [h3]\n   obfs: salamander\n   obfs-password: hello\n");
        assertEquals("p:a#s",list.get(0).proxy.getString("password"));assertEquals("h3",list.get(0).proxy.getJSONArray("alpn").getString(0));
        list.add(list.get(0));JSONObject out=Nodes.configuration(list,true);JSONArray rules=out.getJSONArray("rules");assertEquals("DOMAIN-SUFFIX,qq.com,DIRECT",rules.getString(0));assertEquals("MATCH,PROXY",rules.getString(rules.length()-1));assertEquals("rule",out.getString("mode"));assertNotEquals(out.getJSONArray("proxies").getJSONObject(0).getString("name"),out.getJSONArray("proxies").getJSONObject(1).getString("name"));
        assertEquals(1,Nodes.configuration(list,false).getJSONArray("rules").length());
    }
    @Test public void speedPrefixAndSavedResults() throws Exception {
        Nodes.Node n=Nodes.parse("anytls://p@example.com:443#%E8%8A%82%E7%82%B9").get(0);assertEquals("节点",Nodes.exportName(n));n.average=2*1048576;n.proxy.put("name","5.23MB/s 节点");assertEquals("2.00MB/s 节点",Nodes.exportName(n));n.source="https://example.com/sub";Nodes.Node loaded=Nodes.Node.load(n.save());assertEquals(n.average,loaded.average,0);assertEquals(n.source,loaded.source);
        assertEquals(1,Nodes.parse(Nodes.links(Collections.singletonList(n),true)).size());
    }
    @Test public void ipv6AndVmess() throws Exception {
        Nodes.Node ipv6=Nodes.parse("vless://"+UUID+"@[2001:db8::1]:443?type=grpc&security=tls&serviceName=hello#IPv6").get(0);assertEquals("2001:db8::1",ipv6.proxy.getString("server"));assertEquals("hello",ipv6.proxy.getJSONObject("grpc-opts").getString("grpc-service-name"));
        JSONObject v=new JSONObject().put("add","example.com").put("port",443).put("id",UUID).put("ps","测试").put("net","ws").put("path","/hello").put("host","example.com").put("tls","tls");Nodes.Node vm=Nodes.parse("vmess://"+Base64.getEncoder().encodeToString(v.toString().getBytes(StandardCharsets.UTF_8))).get(0);assertEquals("测试",vm.name());assertTrue(vm.proxy.getBoolean("tls"));
    }
    @Test public void emptyNamesAndHttpCredentials() throws Exception {
        Nodes.Node n=Nodes.parse("http://user:pass%3Aword@example.com:8080#").get(0);assertFalse(n.name().isEmpty());
        String exported=Nodes.links(Collections.singletonList(n),false);assertTrue(exported.contains("user:pass%3Aword@"));Nodes.Node round=Nodes.parse(exported).get(0);assertEquals("pass:word",round.proxy.getString("password"));
    }
}
