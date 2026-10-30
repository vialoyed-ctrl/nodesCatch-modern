package com.vialoyed.nodescatch;

import java.util.*;

public final class NodeActions {
    private NodeActions() {}
    public static int selectRange(List<Nodes.Node> visible) {
        int first=-1,last=-1;
        for(int i=0;i<visible.size();i++)if(visible.get(i).selected){if(first<0)first=i;last=i;}
        if(first<0||first==last)throw new IllegalArgumentException("请先勾选范围的起点和终点，再点「范围」");
        for(int i=first;i<=last;i++)visible.get(i).selected=true;
        return last-first+1;
    }
    public static void invert(List<Nodes.Node> visible){for(Nodes.Node n:visible)n.selected=!n.selected;}
    public static void sort(List<Nodes.Node> nodes,String metric,boolean ascending){
        // Freeze measurements for this comparison: workers may publish new
        // results while the UI thread is refreshing a sorted list.
        Map<Nodes.Node,Double> values=new IdentityHashMap<>();
        for(Nodes.Node n:nodes)values.put(n,metric.equals("delay")?(double)n.delay:n.average);
        nodes.sort((a,b)->{
            double av=values.get(a),bv=values.get(b);
            boolean validA=av>=0&&Double.isFinite(av),validB=bv>=0&&Double.isFinite(bv);
            if(validA!=validB)return validA?-1:1;
            if(!validA)return 0;
            int comparison=Double.compare(av,bv);return ascending?comparison:-comparison;
        });
    }
}
