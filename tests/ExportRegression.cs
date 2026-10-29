using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using Newtonsoft.Json.Linq;
using nodesCatchNext.Mode;
using nodesCatchNext.Handler;
class VerifyExport {
    static void Check(bool value,string label) { if(!value) throw new Exception(label); }
    static int Main(string[] args) {
        try {
            string root=Path.GetFullPath(args[0]);
            string uuid="00000000-0000-4000-8000-000000000001";
            var nodes=new List<VmessItem>();
            foreach(int type in new[]{1,3,5,6,11,12}) nodes.Add(new VmessItem {
                configType=type,address="example.com",port=443,id=type==1||type==5?uuid:"test-password",
                security=type==1?"auto":type==3?"aes-128-gcm":"none",network=type==11?"hysteria2":type==12?"anytls":"tcp",
                streamSecurity=type==3?"":"tls",remarks="日本 \"Tokyo\" #1",testResult="5.23 MB/s"
            });
            var cfg=new Config{localPort=40000,vmess=nodes};
            var parser=typeof(Config).Assembly.GetType("nodesCatchNext.Handler.ShareHandler").GetMethod("ImportFromClipboardConfig",BindingFlags.Public|BindingFlags.Static);
            // Inspect shared export encodings directly; the URL fragment handles Unicode and punctuation.
            for(int i=0;i<nodes.Count;i++) {
                string link=ExportNaming.ShareUrl(cfg,i);
                Check(!string.IsNullOrEmpty(link),"empty share URL");
                string name;
                if(nodes[i].configType==1) {
                    string json=System.Text.Encoding.UTF8.GetString(Convert.FromBase64String(link.Substring(8)));
                    name=(string)JObject.Parse(json)["ps"];
                } else { name=Uri.UnescapeDataString(link.Substring(link.IndexOf('#')+1)).Replace('+',' '); }
                Check(name=="5.23MB/s 日本 \"Tokyo\" #1","wrong exported name: "+name);
                Check(nodes[i].remarks=="日本 \"Tokyo\" #1","source node mutated");
            }
            var n=new VmessItem{remarks="节点",testResult="403 已禁止"};Check(ExportNaming.Name(n)=="节点","error was prefixed");
            n.testResult="正在下载...";Check(ExportNaming.Name(n)=="节点","progress was prefixed");
            n.testResult="512 KB/s";Check(ExportNaming.Name(n)=="512KB/s 节点","KB/s missing");
            n.remarks="5.00MB/s 节点";n.testResult="3,25 MB/s";Check(ExportNaming.Name(n)=="3.25MB/s 节点","duplicate prefix or locale issue");
            n.testResult="";Check(ExportNaming.Name(n)==n.remarks,"missing result changed name");
            var selected=new[]{0,1,2,3,4,5};string export=NativeMihomoConfig.BuildExport(cfg,selected);
            var doc=JObject.Parse(export);var names=new HashSet<string>();
            var rules=(JArray)doc["rules"];
            string[] expectedRules={"DOMAIN-SUFFIX,qq.com,DIRECT","DOMAIN-SUFFIX,gtimg.com,DIRECT","DOMAIN-SUFFIX,qpic.cn,DIRECT","PROCESS-NAME,QQ.exe,DIRECT","MATCH,PROXY"};
            Check((string)doc["mode"]=="rule","export must use rule mode");
            Check(rules.Count==expectedRules.Length,"unexpected exported rule count");
            for(int i=0;i<expectedRules.Length;i++) Check((string)rules[i]==expectedRules[i],"QQ direct rules must precede final proxy fallback");
            var testConfig=JObject.Parse(NativeMihomoConfig.Build(cfg,selected));
            Check(((JArray)testConfig["rules"]).Count==1 && (string)testConfig["rules"][0]=="MATCH,PROXY","export rules must not change the speed-test routing");
            foreach(JObject proxy in doc["proxies"]) Check(names.Add((string)proxy["name"]),"duplicate name");
            foreach(string name in doc["proxy-groups"][0]["proxies"]) Check(names.Contains(name),"broken group reference");
            string temp=Path.Combine(Path.GetTempPath(),"nodesCatch-export-"+Guid.NewGuid().ToString("N")); Directory.CreateDirectory(temp); string path=Path.Combine(temp,"export.yaml"); File.WriteAllText(path,export);
            var p=Process.Start(new ProcessStartInfo(Path.Combine(root,"mihomo-nodes.exe"),"-t -d \""+temp+"\" -f \""+path+"\"") {UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true});
            string output=p.StandardOutput.ReadToEnd()+p.StandardError.ReadToEnd();p.WaitForExit();Check(p.ExitCode==0,output);
            Console.WriteLine("PASS: 6 protocol exports, Unicode and quotes, source names unchanged, invalid/missing speeds, KB/s, repeated prefix, decimal comma, duplicate names, group references, QQ direct rule order, isolated speed-test routing, Mihomo validation");
            return 0;
        } catch(Exception e) {Console.WriteLine(e);return 1;}
    }
}
