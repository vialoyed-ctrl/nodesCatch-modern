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
    public interface Progress { void changed(Nodes.Node node); void message(String message); default void completed(int done,int total){} }
    public static final int DEFAULT_DELAY_CONCURRENCY=8,DEFAULT_DOWNLOAD_CONCURRENCY=4;
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
        try(FileOutputStream out=new FileOutputStream(new File(home,"config.yaml"))){out.write(Nodes.yaml(Nodes.configuration(selected,false)).getBytes(StandardCharsets.UTF_8));}
        CompletableFuture<String> ready=new CompletableFuture<>();
        JSONObject init=new JSONObject().put("home-dir",home.getAbsolutePath()).put("version",Build.VERSION.SDK_INT);
        Clash.INSTANCE.quickSetup(init.toString(),"{}",(InvokeInterface)value->ready.complete(value==null?"":value));
        String error=ready.get(30,TimeUnit.SECONDS);if(!error.isEmpty())throw new IOException("内核配置无效："+error);
    }
    public void run(List<Nodes.Node> selected,boolean download,String url,int seconds,int limitMB,int delayTimeout,Progress progress)throws Exception{
        run(selected,download,url,seconds,limitMB,delayTimeout,download?DEFAULT_DOWNLOAD_CONCURRENCY:DEFAULT_DELAY_CONCURRENCY,progress);
    }
    public void run(List<Nodes.Node> selected,boolean download,String url,int seconds,int limitMB,int delayTimeout,int concurrency,Progress progress)throws Exception{
        int maximum=download?16:64;
        if(concurrency<1||concurrency>maximum)throw new IllegalArgumentException("并发数必须为 1—"+maximum);
        cancelled=false;
        for(Nodes.Node n:selected){if(download){n.average=-1;n.peak=-1;n.speedState=TestState.QUEUED;}else{n.delay=-1;n.delayState=TestState.QUEUED;}n.status="等待测速";}
        progress.completed(0,selected.size());progress.message("正在准备 "+selected.size()+" 个节点…");
        ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(Math.min(concurrency,4));deadlines.setRemoveOnCancelPolicy(true);
        java.util.concurrent.atomic.AtomicInteger completed=new java.util.concurrent.atomic.AtomicInteger();
        try{
            prepare(selected);
            BatchRunner.run(selected.size(),concurrency,()->cancelled,index->{
                Nodes.Node n=selected.get(index);if(cancelled)return;
                if(download){n.speedState=TestState.RUNNING;n.status="正在下载…";}else{n.delayState=TestState.RUNNING;n.status="正在测延迟…";}progress.changed(n);
                try{
                    if(download)download(n,30000+index,url,seconds,limitMB,deadlines,progress);
                    else{
                        String data=new JSONObject().put("proxy-name","node-"+n.id).put("test-url",url).put("timeout",delayTimeout).toString();
                        long delay=Long.parseLong(invoke("testDelay",data,delayTimeout+3000));
                        n.delay=delay>=0?delay:-1;n.delayState=cancelled?TestState.CANCELLED:delay>=0?TestState.SUCCESS:TestState.FAILED;n.status=cancelled?"已停止":delay>=0?"延迟完成":"无法连接";
                    }
                }catch(Exception e){
                    if(download){n.speedState=cancelled?TestState.CANCELLED:TestState.FAILED;n.status=cancelled?"已停止":"下载失败";}
                    else{n.delay=-1;n.delayState=cancelled?TestState.CANCELLED:TestState.FAILED;n.status=cancelled?"已停止":"延迟失败 / 超时";}
                }
                synchronized(progress){int done=completed.incrementAndGet();progress.changed(n);progress.completed(done,selected.size());progress.message((download?"下载":"延迟")+" "+done+"/"+selected.size()+" · 并发 "+concurrency);}
            });
        }finally{
            deadlines.shutdownNow();
            for(Nodes.Node n:selected){int state=download?n.speedState:n.delayState;if(state==TestState.QUEUED||state==TestState.RUNNING){if(download)n.speedState=cancelled?TestState.CANCELLED:TestState.FAILED;else n.delayState=cancelled?TestState.CANCELLED:TestState.FAILED;n.status=cancelled?"已停止":"测速失败";progress.changed(n);}}
            if(Clash.INSTANCE.isLoaded())try{invoke("stopListener",null,5000);}catch(Exception ignored){}
        }
        progress.message(cancelled?"已停止，已完成的结果已保留":"测速完成 · "+completed.get()+"/"+selected.size());
    }
    private void download(Nodes.Node n,int port,String url,int seconds,int limitMB,ScheduledExecutorService deadlines,Progress progress){
        HttpURLConnection c=null;ScheduledFuture<?> deadline=null;
        java.util.concurrent.atomic.AtomicBoolean expired=new java.util.concurrent.atomic.AtomicBoolean();
        long bytes=0,start=SystemClock.elapsedRealtime(),last=start,lastBytes=0;double peak=0;
        try{
            c=(HttpURLConnection)new URL(url).openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",port)));
            connections.add(c);final HttpURLConnection connection=c;
            deadline=deadlines.schedule(()->{expired.set(true);connection.disconnect();},seconds,TimeUnit.SECONDS);
            c.setConnectTimeout(Math.min(10000,seconds*1000));c.setReadTimeout(Math.max(3000,Math.min(15000,seconds*1000)));c.setUseCaches(false);c.setRequestProperty("User-Agent","NodesCatchAndroid/0.1");c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();if(status!=200&&status!=206)throw new IOException("HTTP "+status+"（测速源拒绝或不可用）");
            c.setReadTimeout(1500);last=SystemClock.elapsedRealtime();
            try(InputStream in=c.getInputStream()){
                byte[] buffer=new byte[32768];long cap=limitMB*1048576L;
                while(!cancelled&&!expired.get()&&bytes<cap&&SystemClock.elapsedRealtime()-start<seconds*1000L){
                    int read=in.read(buffer,0,(int)Math.min(buffer.length,cap-bytes));if(read<0)break;bytes+=read;
                    long now=SystemClock.elapsedRealtime();if(now-last>=500){peak=Math.max(peak,(bytes-lastBytes)*1000.0/Math.max(1,now-last));last=now;lastBytes=bytes;n.average=bytes*1000.0/Math.max(1,now-start);n.peak=peak;progress.changed(n);}
                }
            }
        }catch(Exception e){
            if(bytes==0){n.speedState=cancelled?TestState.CANCELLED:TestState.FAILED;n.status=cancelled?"已停止":expired.get()||e instanceof SocketTimeoutException?"下载超时":e.getMessage()==null?"下载失败":e.getMessage();return;}
        }finally{if(deadline!=null)deadline.cancel(false);if(c!=null){connections.remove(c);c.disconnect();}}
        long end=SystemClock.elapsedRealtime();if(bytes>lastBytes)peak=Math.max(peak,(bytes-lastBytes)*1000.0/Math.max(1,end-last));
        if(bytes>0){n.average=bytes*1000.0/Math.max(1,end-start);n.peak=peak;n.speedState=cancelled?TestState.CANCELLED:TestState.SUCCESS;n.status=cancelled?"已停止（部分结果）":"下载完成 · "+String.format(Locale.US,"%.1fMB",bytes/1048576.0);}
        else{n.speedState=cancelled?TestState.CANCELLED:TestState.FAILED;n.status=cancelled?"已停止":"无下载数据";}
    }
    public static String readLimited(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("文件过大，最多支持 5MB");out.write(buffer,0,count);}
        byte[] bytes=out.toByteArray();
        if(bytes.length>=2&&(bytes[0]&255)==255&&(bytes[1]&255)==254)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2&&(bytes[0]&255)==254&&(bytes[1]&255)==255)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        return new String(bytes,StandardCharsets.UTF_8);
    }
    public static String subscription(String url) throws Exception {
        URI uri=new URI(url);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))throw new IllegalArgumentException("订阅地址必须为 HTTP 或 HTTPS");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(Proxy.NO_PROXY);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","clash.meta NodesCatchAndroid/0.1");
        try {int status=c.getResponseCode();if(status!=200)throw new IOException("订阅请求失败：HTTP "+status);try(InputStream in=c.getInputStream()){return readLimited(in,5*1024*1024);}}finally{c.disconnect();}
    }
}
