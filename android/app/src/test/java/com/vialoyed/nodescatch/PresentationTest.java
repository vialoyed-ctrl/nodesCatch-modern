package com.vialoyed.nodescatch;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
public class PresentationTest {
 @Test public void restartedRunBecomesStoppedAndMetricsRemainIndependent() throws Exception {
  Nodes.Node node=Nodes.parse("http://example.com:80#Probe").get(0);node.delay=123;node.delayState=TestState.SUCCESS;node.speedState=TestState.RUNNING;
  Nodes.Node loaded=Nodes.Node.load(node.save());assertEquals(TestState.SUCCESS,loaded.delayState);assertEquals(TestState.CANCELLED,loaded.speedState);assertEquals(123,loaded.delay);
 }
 @Test public void legacyResultsRestore() throws Exception {
  Nodes.Node node=Nodes.parse("http://example.com:80#Probe").get(0);node.delay=100;node.average=1048576;JSONObject saved=node.save();saved.remove("delayState");saved.remove("speedState");Nodes.Node loaded=Nodes.Node.load(saved);assertEquals(TestState.SUCCESS,loaded.delayState);assertEquals(TestState.SUCCESS,loaded.speedState);
 }
 @Test public void bothPalettesHaveReadableDistinctStateColors(){for(boolean dark:new boolean[]{false,true}){AppPalette p=new AppPalette(dark);assertNotEquals(p.success,p.warning);assertNotEquals(p.warning,p.failure);for(int state:new int[]{TestState.SUCCESS,TestState.RUNNING,TestState.FAILED}){double a=luminance(p.stateColor(state)),b=luminance(p.stateBackground(state));assertTrue("Unreadable metric in theme dark="+dark,(Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5);}}}
 private double luminance(int color){double[] c={(color>>16&255)/255.0,(color>>8&255)/255.0,(color&255)/255.0};for(int i=0;i<3;i++)c[i]=c[i]<=.04045?c[i]/12.92:Math.pow((c[i]+.055)/1.055,2.4);return .2126*c[0]+.7152*c[1]+.0722*c[2];}
}
