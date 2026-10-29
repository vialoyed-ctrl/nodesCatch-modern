package com.vialoyed.nodescatch;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class) public class NativeCoreTest {
    @Test public void nativeDelayAndDownloadThroughNode() throws Exception {
        ServerSocket proxy=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));ExecutorService server=Executors.newCachedThreadPool();
        server.execute(()->{while(!proxy.isClosed()){try{Socket socket=proxy.accept();server.execute(()->serve(socket));}catch(IOException ignored){break;}}});
        CoreEngine engine=new CoreEngine(InstrumentationRegistry.getInstrumentation().getTargetContext());
        List<Nodes.Node> nodes=Nodes.parse("http://127.0.0.1:"+proxy.getLocalPort()+"#Loopback");
        CoreEngine.Progress progress=new CoreEngine.Progress(){public void changed(Nodes.Node n){}public void message(String m){}};
        try {
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
