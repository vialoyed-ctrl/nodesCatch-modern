package com.vialoyed.nodescatch;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class) public class NativeCoreTest {
    @Test public void selectedNodeCanBeDeletedFromMenu() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new android.content.Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("nodes");field.setAccessible(true);Nodes.Node probe=Nodes.parse("anytls://probe@example.com:443#DeleteProbe").get(0);
        try{
            instrumentation.runOnMainSync(()->{try{List<Nodes.Node> list=(List<Nodes.Node>)field.get(activity);for(Nodes.Node n:list)n.selected=false;list.add(probe);java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});
            onView(withText("更多")).perform(click());onView(withText("删除选中节点")).perform(click());onView(withText("删除")).perform(click());
            instrumentation.runOnMainSync(()->{try{assertFalse(((List<?>)field.get(activity)).contains(probe));}catch(Exception e){throw new RuntimeException(e);}});
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
    @Test public void windowsEncodedFilesImport() throws Exception {
        String json="{\"proxies\":[{\"name\":\"Windows 导出\",\"type\":\"anytls\",\"server\":\"example.com\",\"port\":443,\"password\":\"probe\"}]}";
        for(java.nio.charset.Charset charset:new java.nio.charset.Charset[]{StandardCharsets.UTF_8,StandardCharsets.UTF_16LE,StandardCharsets.UTF_16BE}){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(charset==StandardCharsets.UTF_8)bytes.write(new byte[]{(byte)239,(byte)187,(byte)191});else if(charset==StandardCharsets.UTF_16LE)bytes.write(new byte[]{(byte)255,(byte)254});else bytes.write(new byte[]{(byte)254,(byte)255});bytes.write(json.getBytes(charset));
            String decoded=CoreEngine.readLimited(new ByteArrayInputStream(bytes.toByteArray()),1024*1024);assertEquals("Windows 导出",Nodes.parse(decoded).get(0).name());
        }
    }
    private static String unicodeYaml(){StringBuilder s=new StringBuilder("#");for(int i=0;i<1023;i++)s.append('x');return s+"🇺🇸\nproxies:\n - {name: 'Import 🇯🇵', type: anytls, server: example.com, port: 443, password: probe}\n";}
    @Test public void fileImportCallbackLoadsUnicodeYaml() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();android.content.Context context=instrumentation.getTargetContext();
        File fixture=new File(context.getFilesDir(),"unicode-import-test.yaml");try(FileOutputStream out=new FileOutputStream(fixture)){out.write(unicodeYaml().getBytes(StandardCharsets.UTF_8));}
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(()->activity.onActivityResult(101,android.app.Activity.RESULT_OK,new android.content.Intent().setData(android.net.Uri.fromFile(fixture))));
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("nodes");field.setAccessible(true);long end=System.currentTimeMillis()+5000;int count=0;
            while(System.currentTimeMillis()<end){instrumentation.waitForIdleSync();java.util.concurrent.atomic.AtomicInteger current=new java.util.concurrent.atomic.AtomicInteger();instrumentation.runOnMainSync(()->{try{current.set(((List<?>)field.get(activity)).size());}catch(Exception e){throw new RuntimeException(e);}});count=current.get();if(count>0)break;Thread.sleep(50);}
            assertTrue("File result was not imported into activity",count>0);
        }finally{instrumentation.runOnMainSync(activity::finish);fixture.delete();}
    }
    @Test public void subscriptionFetchAndUnicodeYamlImport() throws Exception {
        ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));ExecutorService executor=Executors.newSingleThreadExecutor();byte[] payload=unicodeYaml().getBytes(StandardCharsets.UTF_8);
        executor.execute(()->{try(Socket socket=server.accept()){InputStream in=socket.getInputStream();while(!line(in).isEmpty()){}OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: "+payload.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.write(payload);}catch(Exception ignored){}});
        try{String result=CoreEngine.subscription("http://127.0.0.1:"+server.getLocalPort()+"/subscription");assertEquals(1,Nodes.parse(result).size());}finally{server.close();executor.shutdownNow();}
    }
    @Test public void activityLaunches() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.content.Intent intent=new android.content.Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=instrumentation.startActivitySync(intent);
        try {instrumentation.waitForIdleSync();android.view.ViewGroup content=activity.findViewById(android.R.id.content);assertTrue(content.getChildCount()>0);}
        finally {instrumentation.runOnMainSync(activity::finish);}
    }
    @Test public void nativeDelayAndDownloadThroughNode() throws Exception {
        ServerSocket proxy=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));ExecutorService server=Executors.newCachedThreadPool();
        server.execute(()->{while(!proxy.isClosed()){try{Socket socket=proxy.accept();server.execute(()->serve(socket));}catch(IOException ignored){break;}}});
        CoreEngine engine=new CoreEngine(InstrumentationRegistry.getInstrumentation().getTargetContext());
        List<Nodes.Node> nodes=Nodes.parse("http://127.0.0.1:"+proxy.getLocalPort()+"#Loopback");
        CoreEngine.Progress progress=new CoreEngine.Progress(){public void changed(Nodes.Node n){}public void message(String m){}};
        try {
            List<Nodes.Node> modern=Nodes.parse("vless://00000000-0000-4000-8000-000000000001@example.com:443?security=reality&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=&fp=chrome#Reality\n"
                +"hy2://password@example.com:443?obfs=salamander&obfs-password=test#HY2\n"
                +"anytls://password@example.com:443?sni=example.com#AnyTLS\n"
                +"ss://MjAyMi1ibGFrZTMtYWVzLTEyOC1nY206QUFBQUFBQUFBQUFBQUFBQUFBQUFBQT09@example.com:443#SS2022\n"
                +"trojan://password@example.com:443?security=tls&type=ws&path=%2Fws%2Fhello#WebSocket");
            engine.prepare(modern);
            engine.run(nodes,false,"http://192.0.2.1/ping?a=1&b=2",3,1,3000,progress);
            assertTrue("Real native delay failed: "+nodes.get(0).status,nodes.get(0).delay>=0);
            engine.run(nodes,true,"http://192.0.2.1/download",3,1,3000,progress);
            assertTrue("Real proxy download failed: "+nodes.get(0).status,nodes.get(0).average>0);
            assertTrue(nodes.get(0).peak>0);assertTrue(nodes.get(0).status.contains("1.0MB"));
            JSONObject export=Nodes.configuration(nodes,true);assertEquals("DOMAIN-SUFFIX,qq.com,DIRECT",export.getJSONArray("rules").getString(0));
        } finally {engine.cancel();proxy.close();server.shutdownNow();}
    }
    private static void serve(Socket socket){try(Socket s=socket){s.setSoTimeout(6000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();String request=line(in);while(!line(in).isEmpty()){}if(request.startsWith("CONNECT ")){out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));out.flush();request=line(in);while(!line(in).isEmpty()){}}
        boolean ping=request.contains("/ping");int count=ping?0:2*1024*1024;out.write(("HTTP/1.1 "+(ping?"204 No Content":"200 OK")+"\r\nContent-Length: "+count+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));byte[] chunk=new byte[16384];for(int n=0;n<count;n+=chunk.length){out.write(chunk);out.flush();Thread.sleep(5);}
    }catch(Exception ignored){}}
    private static String line(InputStream in)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();for(int c;(c=in.read())!=-1;){if(c=='\n')break;if(c!='\r')bytes.write(c);if(bytes.size()>16384)throw new IOException("Header too long");}return bytes.toString("US-ASCII");}
}
