package com.vialoyed.nodescatch;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import io.github.oviron.libmihomo.Clash;
import io.github.oviron.libmihomo.InvokeInterface;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class CoreEngine {
    public interface Progress { void changed(Nodes.Node node); void message(String message); }
    public volatile boolean cancelled;
    private final Context context;
    private final Set<HttpURLConnection> connections=ConcurrentHashMap.newKeySet();
    public CoreEngine(Context context){this.context=context.getApplicationContext();}
    public void cancel(){cancelled=true;for(HttpURLConnection c:connections)c.disconnect();}
    private String invoke(String method,Object data,int timeout) throws Exception {
        CompletableFuture<String> result=new CompletableFuture<>();
        JSONObject action=new JSONObject().put("id",UUID.randomUUID().toString()).put("method",method).put("data",data);
        Clash.INSTANCE.invokeAction(action.toString(),(InvokeInterface)value->result.complete(value));
        String raw=result.get(timeout,TimeUnit.MILLISECONDS);JSONObject response=new JSONObject(raw);
        if(response.optInt("code",-1)!=0)throw new IOException(response.optString("data","内核操作失败"));
        return String.valueOf(response.opt("data"));
    }
    public void prepare(List<Nodes.Node> selected) throws Exception {
        Clash.INSTANCE.load(context.getApplicationInfo().nativeLibraryDir);Clash.INSTANCE.assertReady();
        File home=new File(context.getFilesDir(),"core");if(!home.exists()&&!home.mkdirs())throw new IOException("无法创建内核目录");
        try(FileOutputStream out=new FileOutputStream(new File(home,"config.yaml"))){out.write(Nodes.configuration(selected,false).toString().getBytes(StandardCharsets.UTF_8));}
        CompletableFuture<String> ready=new CompletableFuture<>();
        JSONObject init=new JSONObject().put("home-dir",home.getAbsolutePath()).put("version",Build.VERSION.SDK_INT);
        Clash.INSTANCE.quickSetup(init.toString(),"{}",(InvokeInterface)value->ready.complete(value==null?"":value));
        String error=ready.get(30,TimeUnit.SECONDS);if(!error.isEmpty())throw new IOException("内核配置无效："+error);
    }
    public void run(List<Nodes.Node> selected,boolean download,String url,int seconds,int limitMB,int delayTimeout,Progress progress) throws Exception {
        cancelled=false;progress.message("正在准备 "+selected.size()+" 个节点…");
        try {
            prepare(selected);
            if(download){for(int i=0;i<selected.size()&&!cancelled;i++){Nodes.Node n=selected.get(i);n.status="正在下载…";n.average=-1;n.peak=-1;progress.changed(n);download(n,30000+i,url,seconds,limitMB);progress.changed(n);}}
            else {
                ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<?>> jobs=new ArrayList<>();
                try {for(Nodes.Node n:selected)jobs.add(pool.submit(()->{
                    if(cancelled)return;n.status="正在测延迟…";progress.changed(n);
                    try {String data=new JSONObject().put("proxy-name","node-"+n.id).put("test-url",url).put("timeout",delayTimeout).toString();long delay=Long.parseLong(invoke("testDelay",data,delayTimeout+3000));n.delay=delay>=0?delay:-1;n.status=delay>=0?"延迟完成":"无法连接";}
                    catch(Exception e){n.delay=-1;n.status=cancelled?"已停止":"延迟失败 / 超时";}
                    progress.changed(n);
                }));for(Future<?> job:jobs)job.get();}finally{pool.shutdownNow();}
            }
        } finally {if(Clash.INSTANCE.isLoaded())try{invoke("stopListener",null,5000);}catch(Exception ignored){} }
        progress.message(cancelled?"已停止，已完成的结果已保留":"测速完成");
    }
    private void download(Nodes.Node n,int port,String url,int seconds,int limitMB){
        HttpURLConnection c=null;long bytes=0,start=SystemClock.elapsedRealtime(),last=start,lastBytes=0;double peak=0;
        try {
            c=(HttpURLConnection)new URL(url).openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",port)));
            connections.add(c);c.setConnectTimeout(Math.min(10000,seconds*1000));c.setReadTimeout(1500);c.setUseCaches(false);c.setRequestProperty("User-Agent","NodesCatchAndroid/0.1");c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();if(status!=200&&status!=206)throw new IOException("HTTP "+status+"（测速源拒绝或不可用）");
            try(InputStream in=c.getInputStream()){
                byte[] buffer=new byte[32768];long cap=limitMB*1048576L;
                while(!cancelled&&bytes<cap&&SystemClock.elapsedRealtime()-start<seconds*1000L){
                    int read=in.read(buffer,0,(int)Math.min(buffer.length,cap-bytes));if(read<0)break;bytes+=read;
                    long now=SystemClock.elapsedRealtime();if(now-last>=500){peak=Math.max(peak,(bytes-lastBytes)*1000.0/(now-last));last=now;lastBytes=bytes;}
                }
            }
        }catch(Exception e){if(bytes==0){n.status=cancelled?"已停止":e instanceof SocketTimeoutException?"下载超时":e.getMessage()==null?"下载失败":e.getMessage();return;}}
        finally{if(c!=null){connections.remove(c);c.disconnect();}}
        long end=SystemClock.elapsedRealtime();if(end>last&&bytes>lastBytes)peak=Math.max(peak,(bytes-lastBytes)*1000.0/(end-last));
        if(bytes>0){n.average=bytes*1000.0/Math.max(1,end-start);n.peak=peak;n.status=cancelled?"已停止（部分结果）":"下载完成 · "+String.format(Locale.US,"%.1fMB",bytes/1048576.0);}
        else n.status=cancelled?"已停止":"无下载数据";
    }
    public static String readLimited(InputStream in,int limit) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("文件过大，最多支持 5MB");out.write(buffer,0,count);}return out.toString("UTF-8");}
    public static String subscription(String url) throws Exception {
        URI uri=new URI(url);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))throw new IllegalArgumentException("订阅地址必须为 HTTP 或 HTTPS");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","clash.meta NodesCatchAndroid/0.1");
        try {int status=c.getResponseCode();if(status!=200)throw new IOException("订阅请求失败：HTTP "+status);try(InputStream in=c.getInputStream()){return readLimited(in,5*1024*1024);}}finally{c.disconnect();}
    }
}
