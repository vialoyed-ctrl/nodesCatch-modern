package com.vialoyed.nodescatch;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
import android.widget.TextView;
@RunWith(AndroidJUnit4.class) public class UiPresentationTest {
 @Test public void lightAndDarkMetricsAndLayout() throws Exception {
  android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();android.content.Context context=ins.getTargetContext();
  for(boolean dark:new boolean[]{false,true}){
   context.getSharedPreferences("settings",0).edit().putString("theme",dark?"dark":"light").commit();
   MainActivity activity=(MainActivity)ins.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
   try{
    ins.runOnMainSync(()->{try{
     java.lang.reflect.Field f=MainActivity.class.getDeclaredField("nodes");f.setAccessible(true);List<Nodes.Node> list=(List<Nodes.Node>)f.get(activity);list.clear();
     for(int i=0;i<12;i++){Nodes.Node n=Nodes.parse("http://example.com:80#"+new String[]{"日本 · Tokyo 01","新加坡 · Singapore 02","美国 · Los Angeles 03"}[i%3]).get(0);n.selected=i<2;n.delay=83+i*17;n.average=5.4*1048576;n.peak=8.2*1048576;n.delayState=TestState.SUCCESS;n.speedState=i%3==0?TestState.SUCCESS:i%3==1?TestState.RUNNING:TestState.FAILED;n.status=i%3==0?"下载完成":i%3==1?"正在下载…":"HTTP 403";list.add(n);}
     java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("refresh");refresh.setAccessible(true);refresh.invoke(activity);
    }catch(Exception e){throw new RuntimeException(e);}});
    ins.waitForIdleSync();
    ins.runOnMainSync(()->{try{
     java.lang.reflect.Field f=MainActivity.class.getDeclaredField("nodes");f.setAccessible(true);List<Nodes.Node> list=(List<Nodes.Node>)f.get(activity);AppPalette p=new AppPalette(dark);
     for(int i=0;i<3;i++){Nodes.Node n=list.get(i);TextView delay=activity.getWindow().getDecorView().findViewWithTag("metric.delay."+n.id);TextView avg=activity.getWindow().getDecorView().findViewWithTag("metric.average."+n.id);assertNotNull(delay);assertNotNull(avg);assertEquals(p.success,delay.getCurrentTextColor());assertEquals(p.stateColor(n.speedState),avg.getCurrentTextColor());assertTrue(avg.getWidth()>0);}
    }catch(Exception e){throw new RuntimeException(e);}});
    File file=new File(context.getExternalFilesDir(null),dark?"dark.png":"light.png");assertTrue(ins.getUiAutomation().takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,new FileOutputStream(file)));
   }finally{ins.runOnMainSync(activity::finish);}
  }
  context.getSharedPreferences("settings",0).edit().putString("theme","system").commit();
 }
}
