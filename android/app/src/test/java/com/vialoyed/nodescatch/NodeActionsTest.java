package com.vialoyed.nodescatch;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;

public class NodeActionsTest {
    private List<Nodes.Node> sample(int count)throws Exception{
        List<Nodes.Node> list=new ArrayList<>();for(int i=0;i<count;i++){Nodes.Node n=new Nodes.Node();n.proxy=new JSONObject().put("name","Node "+i);n.selected=false;list.add(n);}return list;
    }
    @Test public void firstAndLastSelectionsFillInclusiveRange()throws Exception{
        List<Nodes.Node> list=sample(19);list.get(1).selected=true;list.get(10).selected=true;
        assertEquals(10,NodeActions.selectRange(list));for(int i=0;i<list.size();i++)assertEquals(i>=1&&i<=10,list.get(i).selected);
    }
    @Test public void filteredRangeDoesNotSelectHiddenItems()throws Exception{
        List<Nodes.Node> list=sample(5);List<Nodes.Node> visible=Arrays.asList(list.get(0),list.get(2),list.get(4));visible.get(0).selected=true;visible.get(2).selected=true;
        NodeActions.selectRange(visible);assertTrue(list.get(2).selected);assertFalse(list.get(1).selected);assertFalse(list.get(3).selected);
    }
    @Test(expected=IllegalArgumentException.class)public void rangeNeedsTwoEndpoints()throws Exception{List<Nodes.Node> list=sample(3);list.get(0).selected=true;NodeActions.selectRange(list);}
    @Test public void inversionOnlyTouchesVisibleItems()throws Exception{List<Nodes.Node> list=sample(3);list.get(0).selected=true;NodeActions.invert(list.subList(0,2));assertFalse(list.get(0).selected);assertTrue(list.get(1).selected);assertFalse(list.get(2).selected);}
    @Test public void sortingKeepsFailuresLastAndSelectionOnSameNode()throws Exception{
        List<Nodes.Node> list=sample(3);Nodes.Node fast=list.get(0),slow=list.get(1),failed=list.get(2);fast.delay=20;fast.average=5;fast.selected=true;slow.delay=100;slow.average=1;
        NodeActions.sort(list,"delay",true);assertSame(fast,list.get(0));NodeActions.sort(list,"delay",false);assertSame(slow,list.get(0));assertSame(failed,list.get(2));
        NodeActions.sort(list,"speed",false);assertSame(fast,list.get(0));NodeActions.sort(list,"speed",true);assertSame(slow,list.get(0));assertSame(failed,list.get(2));assertTrue(fast.selected);assertFalse(slow.selected);
    }
}
