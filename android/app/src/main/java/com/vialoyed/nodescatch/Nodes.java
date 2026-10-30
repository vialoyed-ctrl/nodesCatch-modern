package com.vialoyed.nodescatch;

import org.json.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class Nodes {
    public static final class Node {
        public String id = UUID.randomUUID().toString();
        public String source = "";
        public JSONObject proxy;
        public boolean selected = true;
        public volatile long delay = -1;
        public volatile double average = -1, peak = -1;
        public volatile int delayState=TestState.IDLE,speedState=TestState.IDLE;
        public volatile String status = "未测速";
        public String name() { return proxy.optString("name", "未命名节点"); }
        public String type() { return proxy.optString("type").toUpperCase(Locale.ROOT); }
        public JSONObject save() throws Exception {
            return new JSONObject().put("id",id).put("source",source).put("proxy",proxy).put("selected",selected).put("delay",delay).put("average",average).put("peak",peak).put("status",status).put("delayState",delayState).put("speedState",speedState);
        }
        public static Node load(JSONObject o) throws Exception {
            Node n=new Node(); n.id=o.getString("id"); n.source=o.optString("source"); n.proxy=o.getJSONObject("proxy"); n.selected=o.optBoolean("selected",true);
            n.delay=o.optLong("delay",-1); n.average=o.optDouble("average",-1); n.peak=o.optDouble("peak",-1); n.status=o.optString("status","未测速");
            int oldDelay=n.delay>=0?TestState.SUCCESS:(n.status.contains("延迟失败")||n.status.contains("无法连接")?TestState.FAILED:TestState.IDLE);
            int oldSpeed=n.average>=0?TestState.SUCCESS:(n.status.contains("下载失败")||n.status.contains("下载超时")||n.status.startsWith("HTTP ")?TestState.FAILED:TestState.IDLE);
            n.delayState=TestState.restored(o.optInt("delayState",oldDelay));n.speedState=TestState.restored(o.optInt("speedState",oldSpeed));return n;
        }
    }
    public static String speed(double bytes) { return bytes < 0 ? "—" : String.format(Locale.US,"%.2fMB/s",bytes/1048576.0); }
    public static String yaml(JSONObject config) throws Exception {
        // Android JSONObject escapes '/' as '\/'. That is valid JSON but
        // rejected by Mihomo's YAML reader. Unescaped '/' preserves the value.
        String raw=config.toString(2);StringBuilder out=new StringBuilder();int backslashes=0;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(c=='/'&&(backslashes&1)==1)out.setLength(out.length()-1);
            out.append(c);backslashes=c=='\\'?backslashes+1:0;
        }
        return out.toString();
    }
    public static String exportName(Node n) {
        if(n.average<0) return n.name();
        return speed(n.average)+" "+n.name().replaceFirst("^\\d+(?:[.,]\\d+)?\\s*(?:GB/s|MB/s|KB/s|B/s)\\s+","");
    }
    public static List<Node> parse(String input) throws Exception {
        String text=cleanText(input); if(text.isEmpty()) throw new IllegalArgumentException("内容为空");
        if(!text.contains("://") && !text.contains("proxies:" ) && !text.startsWith("{")) {
            try {text=cleanText(decode(text));} catch(Exception ignored) {}
        }
        List<Node> result=new ArrayList<>();
        if(text.startsWith("{") || text.contains("proxies:")) {
            LoaderOptions options=new LoaderOptions(); options.setCodePointLimit(5*1024*1024); options.setMaxAliasesForCollections(20);
            Object obj=new Yaml(new SafeConstructor(options)).load(text);
            if(!(obj instanceof Map)) throw new IllegalArgumentException("配置需要包含 proxies 列表");
            Object list=((Map<?,?>)obj).get("proxies"); if(!(list instanceof List)) throw new IllegalArgumentException("未找到 proxies 节点列表（暂不解析远程 proxy-providers）");
            for(Object p:(List<?>)list) {if(p instanceof Map) add(result,new JSONObject((Map<?,?>)p));}
        } else {
            int invalid=0;
            for(String line:text.split("[\\r\\n]+")) {
                line=line.trim(); if(line.isEmpty()||line.startsWith("#")) continue;
                try {add(result,fromLink(line));} catch(Exception e) {invalid++;}
            }
            if(result.isEmpty()) throw new IllegalArgumentException("没有可识别节点，共 "+invalid+" 行失败");
            if(invalid>0) throw new IllegalArgumentException("有 "+invalid+" 行无法解析；请单独导入有效链接或使用完整 Mihomo YAML，避免遗漏节点");
        }
        if(result.size()>2000) throw new IllegalArgumentException("单次最多导入 2000 个节点");
        return result;
    }
    private static String cleanText(String input){String text=input.trim();return text.startsWith("\uFEFF")?text.substring(1).trim():text;}
    private static void add(List<Node> list,JSONObject p) throws Exception {
        if(!p.has("type")||!p.has("server")||p.optInt("port")<1||p.optInt("port")>65535) throw new IllegalArgumentException("节点缺少类型、服务器或有效端口");
        if(p.optString("name").trim().isEmpty()) p.put("name",p.optString("type")+" "+p.optString("server"));
        Node n=new Node(); n.proxy=p; list.add(n);
    }
    private static String decode(String s) {s=s.replaceAll("\\s",""); int remainder=s.length()%4;if(remainder!=0)s+="====".substring(remainder); return new String(Base64.getDecoder().decode(s.replace('-','+').replace('_','/')),StandardCharsets.UTF_8);}
    private static String enc(String s) {try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static String dec(String s) {try{return URLDecoder.decode(s,"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static Map<String,String> query(String s) {
        Map<String,String> q=new LinkedHashMap<>();if(s!=null)for(String item:s.split("&")){String[] kv=item.split("=",2);q.put(dec(kv[0]),kv.length>1?dec(kv[1]):"");}return q;
    }
    private static void put(JSONObject p,String k,String v) throws Exception {if(v!=null&&!v.isEmpty())p.put(k,v);}
    private static JSONObject fromLink(String link) throws Exception {
        if(link.startsWith("vmess://")) {
            JSONObject v=new JSONObject(decode(link.substring(8))); JSONObject p=new JSONObject().put("type","vmess").put("server",v.getString("add")).put("port",Integer.parseInt(v.get("port").toString())).put("name",v.optString("ps","VMess")).put("uuid",v.getString("id")).put("alterId",v.optInt("aid",0)).put("cipher",v.optString("scy","auto"));
            if("tls".equals(v.optString("tls")))p.put("tls",true);put(p,"servername",v.optString("sni"));
            transport(p,v.optString("net","tcp"),v.optString("path"),v.optString("host"),v.optString("type"));return p;
        }
        if(link.startsWith("ssr://")) {
            String raw=decode(link.substring(6));String[] parts=raw.split("/\\?",2);String[] f=parts[0].split(":",6);if(f.length!=6)throw new IllegalArgumentException("SSR 格式无效");Map<String,String> q=query(parts.length>1?parts[1]:null);
            return new JSONObject().put("type","ssr").put("server",f[0]).put("port",Integer.parseInt(f[1])).put("protocol",f[2]).put("cipher",f[3]).put("obfs",f[4]).put("password",decode(f[5])).put("name",q.containsKey("remarks")?decode(q.get("remarks")):"SSR").put("protocol-param",q.containsKey("protoparam")?decode(q.get("protoparam")):"").put("obfs-param",q.containsKey("obfsparam")?decode(q.get("obfsparam")):"");
        }
        if(link.startsWith("ss://")) {
            String rest=link.substring(5);String frag="";int hash=rest.indexOf('#');if(hash>=0){frag=dec(rest.substring(hash+1));rest=rest.substring(0,hash);}
            if(!rest.contains("@")) rest=decode(rest);
            URI uri=new URI("ss://"+rest);String auth=uri.getRawUserInfo();if(auth==null)throw new IllegalArgumentException("SS 缺少凭据");auth=dec(auth);if(!auth.contains(":"))auth=decode(auth);String[] pair=auth.split(":",2);
            JSONObject p=new JSONObject().put("type","ss").put("server",host(uri)).put("port",uri.getPort()).put("cipher",pair[0]).put("password",pair[1]).put("name",frag.isEmpty()?"SS":frag);
            String plugin=query(uri.getRawQuery()).get("plugin");if(plugin!=null){String[] opts=plugin.split(";");p.put("plugin",opts[0].equals("obfs-local")?"obfs":opts[0]);JSONObject values=new JSONObject();for(int i=1;i<opts.length;i++){String[] kv=opts[i].split("=",2);String k=kv[0].equals("obfs")?"mode":kv[0].equals("obfs-host")?"host":kv[0];values.put(k,kv.length>1?kv[1]:true);}p.put("plugin-opts",values);}return p;
        }
        URI u=new URI(link);String scheme=u.getScheme().toLowerCase(Locale.ROOT);if(scheme.equals("hy2"))scheme="hysteria2";if(scheme.equals("socks"))scheme="socks5";
        if(!Arrays.asList("vless","trojan","hysteria2","anytls","http","https","socks5").contains(scheme))throw new IllegalArgumentException("不支持链接协议 "+scheme);
        Map<String,String> q=query(u.getRawQuery());String user=u.getRawUserInfo()==null?"":dec(u.getRawUserInfo());
        JSONObject p=new JSONObject().put("type",scheme.equals("https")?"http":scheme).put("server",host(u)).put("port",u.getPort()>0?u.getPort():443).put("name",u.getRawFragment()==null?scheme:dec(u.getRawFragment()));
        if(scheme.equals("vless")) p.put("uuid",user); else if(scheme.equals("http")||scheme.equals("https")||scheme.equals("socks5")){String[] a=user.split(":",2);if(!user.isEmpty())p.put("username",a[0]);if(a.length>1)p.put("password",a[1]);}else p.put("password",user);
        String security=q.getOrDefault("security",scheme.equals("trojan")?"tls":"");if(scheme.equals("https")||security.equals("tls")||security.equals("reality"))p.put("tls",true);
        String sni=q.getOrDefault("sni",q.getOrDefault("peer",""));put(p,scheme.equals("vless")?"servername":"sni",sni);
        if(q.containsKey("insecure")||q.containsKey("allowInsecure")){String value=q.getOrDefault("insecure",q.get("allowInsecure"));p.put("skip-cert-verify",value.equals("1")||value.equalsIgnoreCase("true"));}
        put(p,"client-fingerprint",q.get("fp"));put(p,"flow",q.get("flow"));if(q.containsKey("alpn"))p.put("alpn",new JSONArray(Arrays.asList(q.get("alpn").split(","))));
        if(security.equals("reality")||q.containsKey("pbk")){p.put("tls",true);p.put("reality-opts",new JSONObject().put("public-key",q.getOrDefault("pbk",q.getOrDefault("publicKey",""))).put("short-id",q.getOrDefault("sid",q.getOrDefault("shortId",""))));if(!p.has("client-fingerprint"))p.put("client-fingerprint","chrome");}
        if(scheme.equals("hysteria2")){put(p,"obfs",q.get("obfs"));put(p,"obfs-password",q.get("obfs-password"));put(p,"fingerprint",q.get("pinSHA256"));}
        if(scheme.equals("vless")||scheme.equals("trojan")) transport(p,q.getOrDefault("type","tcp"),q.getOrDefault("serviceName",q.getOrDefault("path","/")),q.getOrDefault("host",""),q.getOrDefault("headerType","none"));
        return p;
    }
    private static String host(URI uri){String host=uri.getHost();if(host==null)throw new IllegalArgumentException("服务器地址无效");return host.startsWith("[")?host.substring(1,host.length()-1):host;}
    private static void transport(JSONObject p,String type,String path,String host,String header) throws Exception {
        if(type.isEmpty()||type.equals("tcp")){if(header.equals("http"))throw new IllegalArgumentException("暂不支持 TCP HTTP 伪装链接，请导入完整 YAML");return;}
        p.put("network",type);
        if(type.equals("ws")){JSONObject ws=new JSONObject().put("path",path.isEmpty()?"/":path);if(!host.isEmpty())ws.put("headers",new JSONObject().put("Host",host));p.put("ws-opts",ws);}
        else if(type.equals("grpc"))p.put("grpc-opts",new JSONObject().put("grpc-service-name",path));
        else if(type.equals("h2")||type.equals("http")){p.put("network","h2");p.put("h2-opts",new JSONObject().put("path",path).put("host",new JSONArray(Arrays.asList(host.split(",")))));}
        else throw new IllegalArgumentException("不支持链接传输 "+type+"，请导入完整 YAML");
    }
    public static JSONObject configuration(List<Node> nodes,boolean exporting) throws Exception {
        JSONArray proxies=new JSONArray(),names=new JSONArray(),listeners=new JSONArray();Set<String> used=new HashSet<>(Arrays.asList("DIRECT","REJECT","GLOBAL","PROXY"));
        for(int i=0;i<nodes.size();i++){Node n=nodes.get(i);JSONObject p=new JSONObject(n.proxy.toString());String base=exporting?exportName(n):"node-"+n.id;String name=base;for(int suffix=2;!used.add(name);suffix++)name=base+" ("+suffix+")";p.put("name",name);proxies.put(p);names.put(name);if(!exporting)listeners.put(new JSONObject().put("name","test-"+i).put("type","mixed").put("listen","127.0.0.1").put("port",30000+i).put("proxy",name));}
        if(nodes.isEmpty())throw new IllegalArgumentException("请先选择节点");
        JSONArray rules=new JSONArray();if(exporting){rules.put("DOMAIN-SUFFIX,qq.com,DIRECT").put("DOMAIN-SUFFIX,gtimg.com,DIRECT").put("DOMAIN-SUFFIX,qpic.cn,DIRECT").put("PROCESS-NAME,QQ.exe,DIRECT").put("PROCESS-NAME,com.tencent.mobileqq,DIRECT");}rules.put("MATCH,PROXY");
        JSONObject out=new JSONObject().put("mode","rule").put("log-level","warning").put("allow-lan",false).put("bind-address","127.0.0.1").put("proxies",proxies).put("proxy-groups",new JSONArray().put(new JSONObject().put("name","PROXY").put("type","select").put("proxies",names))).put("rules",rules);
        if(exporting)out.put("mixed-port",7890);else out.put("listeners",listeners);
        return out;
    }
    public static String links(List<Node> nodes,boolean base64) throws Exception {
        StringBuilder out=new StringBuilder();for(Node n:nodes)out.append(link(n)).append('\n');String text=out.toString();return base64?Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)):text;
    }
    private static String link(Node n) throws Exception {
        JSONObject p=n.proxy;String type=p.getString("type"),name=exportName(n),server=p.getString("server");if(server.contains(":"))server="["+server+"]";String address=server+":"+p.getInt("port");
        if(type.equals("vmess")){JSONObject v=new JSONObject().put("v","2").put("ps",name).put("add",p.getString("server")).put("port",p.getInt("port")).put("id",p.getString("uuid")).put("aid",p.optInt("alterId")).put("scy",p.optString("cipher","auto")).put("net",p.optString("network","tcp")).put("tls",p.optBoolean("tls")?"tls":"").put("sni",p.optString("servername"));JSONObject ws=p.optJSONObject("ws-opts"),grpc=p.optJSONObject("grpc-opts");if(ws!=null){v.put("path",ws.optString("path"));if(ws.optJSONObject("headers")!=null)v.put("host",ws.getJSONObject("headers").optString("Host"));}if(grpc!=null)v.put("path",grpc.optString("grpc-service-name"));return "vmess://"+Base64.getEncoder().encodeToString(v.toString().getBytes(StandardCharsets.UTF_8));}
        if(type.equals("ss")){if(p.has("plugin"))throw new IllegalArgumentException("带插件的 SS 节点请使用 YAML 导出以保留全部参数");String auth=p.getString("cipher")+":"+p.getString("password");return "ss://"+Base64.getEncoder().withoutPadding().encodeToString(auth.getBytes(StandardCharsets.UTF_8))+"@"+address+"#"+enc(name);}
        if(!Arrays.asList("vless","trojan","hysteria2","anytls","http","socks5").contains(type))throw new IllegalArgumentException(type+" 请使用完整 YAML 导出");
        Map<String,String> q=new LinkedHashMap<>();String auth=type.equals("vless")?p.getString("uuid"):p.optString("password");String network=p.optString("network","tcp");
        boolean separateCredentials=type.equals("http")||type.equals("socks5");
        if(type.equals("vless")||type.equals("trojan")){q.put("type",network);q.put("security",p.has("reality-opts")?"reality":p.optBoolean("tls")||type.equals("trojan")?"tls":"none");}
        for(String[] pair:new String[][]{{"sni","sni"},{"servername","sni"},{"flow","flow"},{"client-fingerprint","fp"},{"obfs","obfs"},{"obfs-password","obfs-password"},{"fingerprint","pinSHA256"}})if(p.has(pair[0]))q.put(pair[1],p.getString(pair[0]));
        if(p.has("skip-cert-verify"))q.put("insecure",p.optBoolean("skip-cert-verify")?"1":"0");JSONArray alpn=p.optJSONArray("alpn");if(alpn!=null){List<String> values=new ArrayList<>();for(int i=0;i<alpn.length();i++)values.add(alpn.getString(i));q.put("alpn",String.join(",",values));}JSONObject reality=p.optJSONObject("reality-opts");if(reality!=null){q.put("pbk",reality.getString("public-key"));q.put("sid",reality.optString("short-id"));}
        JSONObject ws=p.optJSONObject("ws-opts"),grpc=p.optJSONObject("grpc-opts"),h2=p.optJSONObject("h2-opts");if(ws!=null){q.put("path",ws.optString("path","/"));if(ws.optJSONObject("headers")!=null)q.put("host",ws.getJSONObject("headers").optString("Host"));}if(grpc!=null)q.put("serviceName",grpc.optString("grpc-service-name"));if(h2!=null){q.put("path",h2.optString("path","/"));JSONArray hosts=h2.optJSONArray("host");if(hosts!=null){List<String> a=new ArrayList<>();for(int i=0;i<hosts.length();i++)a.add(hosts.getString(i));q.put("host",String.join(",",a));}}
        StringBuilder query=new StringBuilder();for(Map.Entry<String,String> pair:q.entrySet()){if(query.length()>0)query.append('&');query.append(enc(pair.getKey())).append('=').append(enc(pair.getValue()));}
        String credentials=separateCredentials?enc(p.optString("username"))+":"+enc(p.optString("password")):enc(auth);
        return (type.equals("http")&&p.optBoolean("tls")?"https":type)+"://"+credentials+"@"+address+"?"+query+"#"+enc(name);
    }
}
