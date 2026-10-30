package com.vialoyed.nodescatch;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final List<Nodes.Node> nodes=new ArrayList<>(),visible=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CoreEngine engine;
    private android.content.SharedPreferences prefs;
    private Adapter adapter;
    private TextView summary,message;
    private EditText search;
    private boolean busy=false,onlySuccess=false;
    private String pendingExport;
    private LinearLayout root;
    private final int NAVY=Color.rgb(16,43,56),TEAL=Color.rgb(8,127,140),MUTED=Color.rgb(98,116,126);
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=getSharedPreferences("settings",MODE_PRIVATE);engine=new CoreEngine(this);load();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(244,247,249));setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout head=new LinearLayout(this);head.setPadding(dp(18),dp(18),dp(12),dp(16));head.setGravity(Gravity.CENTER_VERTICAL);head.setBackgroundColor(NAVY);
        LinearLayout title=new LinearLayout(this);title.setOrientation(1);TextView brand=label("节点测速",25,Color.WHITE);brand.setTypeface(null,Typeface.BOLD);title.addView(brand);title.addView(label("NODES CATCH · ANDROID",11,Color.rgb(95,222,200)));head.addView(title,new LinearLayout.LayoutParams(0,-2,1));head.addView(button("＋ 导入",this::importMenu));head.addView(button("设置",this::settings));root.addView(head);
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER_VERTICAL);stats.setPadding(dp(18),dp(10),dp(12),0);summary=label("",13,MUTED);stats.addView(summary,new LinearLayout.LayoutParams(0,-2,1));stats.addView(button("更多",this::more));root.addView(stats);
        search=new EditText(this);search.setSingleLine();search.setTextSize(14);search.setHint("搜索节点名称 / 协议 / 服务器");search.setPadding(dp(12),0,dp(12),0);search.setBackground(shape(Color.WHITE,10));LinearLayout.LayoutParams searchLP=new LinearLayout.LayoutParams(-1,dp(44));searchLP.setMargins(dp(16),dp(10),dp(16),dp(8));root.addView(search,searchLP);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}public void afterTextChanged(Editable s){}});
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);adapter=new Adapter();list.setAdapter(adapter);FrameLayout space=new FrameLayout(this);space.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=label("还没有节点\n点击右上角 ＋ 导入，添加自己的节点或订阅",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));space.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);root.addView(space,new LinearLayout.LayoutParams(-1,0,1));
        message=label("导入节点后，先测延迟，再按需测下载。",12,MUTED);message.setPadding(dp(18),dp(8),dp(18),dp(8));root.addView(message);
        LinearLayout actions=new LinearLayout(this);actions.setPadding(dp(8),0,dp(8),dp(10));for(String text:new String[]{"测延迟","测下载","停止","导出"}){Button b=button(text,()->{if(text.equals("停止")){engine.cancel();message.setText("正在停止…");}else if(text.equals("导出"))exportMenu();else test(text.equals("测下载"));});actions.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(actions);refresh();
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String text,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setTextColor(TEAL);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);b.setAllCaps(false);b.setBackground(shape(Color.rgb(225,244,242),8));b.setStateListAnimator(null);b.setOnClickListener(v->run.run());return b;}
    private boolean idle(){if(busy){toast("请先停止当前测速");return false;}return true;}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("知道了",null).show();}
    private void load(){try{File file=new File(getFilesDir(),"nodes.json");if(file.exists())try(InputStream in=new FileInputStream(file)){JSONArray a=new JSONArray(CoreEngine.readLimited(in,5*1024*1024));for(int i=0;i<a.length();i++)nodes.add(Nodes.Node.load(a.getJSONObject(i)));}}catch(Exception e){toast("本地记录读取失败，原文件已保留");}}
    private void save(){try{JSONArray a=new JSONArray();for(Nodes.Node n:nodes)a.put(n.save());android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"nodes.json"));FileOutputStream out=file.startWrite();try{out.write(a.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}catch(Exception e){toast("保存失败："+e.getMessage());}}
    private List<Nodes.Node> selected(){List<Nodes.Node> out=new ArrayList<>();for(Nodes.Node n:nodes)if(n.selected)out.add(n);return out;}
    private void refresh(){if(adapter==null)return;String filter=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();for(Nodes.Node n:nodes){String hay=n.name()+" "+n.type()+" "+n.proxy.optString("server");if(hay.toLowerCase(Locale.ROOT).contains(filter)&&(!onlySuccess||n.average>=0))visible.add(n);}summary.setText(nodes.size()+" 个节点 · 已选 "+selected().size()+" · 显示 "+visible.size());adapter.notifyDataSetChanged();}
    private void importMenu(){if(!idle())return;new AlertDialog.Builder(this).setTitle("导入节点").setItems(new String[]{"粘贴分享链接 / Base64 / YAML","从文件导入","添加或更新订阅"},(d,index)->{if(index==0){EditText input=new EditText(this);input.setMinLines(5);input.setHint("粘贴节点或完整配置");input.setGravity(Gravity.TOP);new AlertDialog.Builder(this).setTitle("粘贴导入").setView(input).setPositiveButton("导入",(dialog,which)->parseInput(input.getText().toString(),"")).setNegativeButton("取消",null).show();}else if(index==1){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,101);}else subscriptionDialog();}).show();}
    private void parseInput(String text,String source){if(!idle())return;busy=true;message.setText("正在解析…");worker.execute(()->{try{List<Nodes.Node> imported=Nodes.parse(text);for(Nodes.Node n:imported)n.source=source;runOnUiThread(()->{if(!source.isEmpty())nodes.removeIf(n->n.source.equals(source));nodes.addAll(imported);busy=false;save();refresh();message.setText("已导入 "+imported.size()+" 个节点");});}catch(Exception e){runOnUiThread(()->{busy=false;error(e);message.setText("导入失败，已有节点未改动");});}});}
    private void subscriptionDialog(){EditText url=new EditText(this);url.setSingleLine();url.setHint("https://…");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);new AlertDialog.Builder(this).setTitle("订阅地址（仅保存在本机）").setView(url).setPositiveButton("获取",(d,w)->fetchSubscription(url.getText().toString().trim())).setNegativeButton("取消",null).show();}
    private void fetchSubscription(String url){if(!idle())return;busy=true;message.setText("正在获取订阅…");worker.execute(()->{try{List<Nodes.Node> imported=Nodes.parse(CoreEngine.subscription(url));for(Nodes.Node n:imported)n.source=url;runOnUiThread(()->{nodes.removeIf(n->n.source.equals(url));nodes.addAll(imported);busy=false;save();refresh();message.setText("订阅更新完成 · "+imported.size()+" 个节点");});}catch(Exception e){runOnUiThread(()->{busy=false;error(e);message.setText("订阅更新失败，旧节点已保留");});}});}
    private void subscriptions(){if(!idle())return;Set<String> urls=new LinkedHashSet<>();for(Nodes.Node n:nodes)if(!n.source.isEmpty())urls.add(n.source);if(urls.isEmpty()){subscriptionDialog();return;}String[] a=urls.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("选择订阅以更新").setItems(a,(d,w)->fetchSubscription(a[w])).setPositiveButton("新增订阅",(d,w)->subscriptionDialog()).setNegativeButton("关闭",null).show();}
    private void test(boolean download){if(!idle())return;List<Nodes.Node> chosen=selected();if(chosen.isEmpty()){toast("请先选择节点");return;}if(chosen.size()>2000){toast("每次最多测试 2000 个节点");return;}Runnable start=()->{busy=true;getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);String url=prefs.getString(download?"download":"delay",download?"https://proof.ovh.net/files/100Mb.dat":"https://www.gstatic.com/generate_204");worker.execute(()->{try{engine.run(chosen,download,url,prefs.getInt("seconds",10),prefs.getInt("limitMB",10),prefs.getInt("timeout",5)*1000,new CoreEngine.Progress(){public void changed(Nodes.Node n){runOnUiThread(()->refresh());}public void message(String s){runOnUiThread(()->message.setText(s));}});}catch(Throwable e){runOnUiThread(()->{message.setText("测速未完成");error(e instanceof Exception?(Exception)e:new Exception(e));});}finally{runOnUiThread(()->{busy=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);save();refresh();});}});};if(download)new AlertDialog.Builder(this).setTitle("开始下载测速？").setMessage("已选 "+chosen.size()+" 个节点。每个节点最多 "+prefs.getInt("seconds",10)+" 秒、"+prefs.getInt("limitMB",10)+"MB 下载数据（实际流量含协议开销）；逐个测试，流量会累加。离开应用会停止测速。").setPositiveButton("开始",(d,w)->start.run()).setNegativeButton("取消",null).show();else start.run();}
    private void more(){
        if(!idle())return;
        new AlertDialog.Builder(this).setTitle("节点管理").setItems(new String[]{"全选当前显示","取消全部选择","按平均速度排序","按延迟排序","按名称排序",onlySuccess?"显示所有节点":"只看下载成功","更新订阅","删除选中节点"},(d,w)->{
            switch(w){
                case 0: for(Nodes.Node n:visible){n.selected=true;} break;
                case 1: for(Nodes.Node n:nodes){n.selected=false;} break;
                case 2: nodes.sort((a,b)->Double.compare(b.average,a.average)); break;
                case 3: nodes.sort(Comparator.comparingLong(n->n.delay<0?Long.MAX_VALUE:n.delay)); break;
                case 4: nodes.sort(Comparator.comparing(Nodes.Node::name)); break;
                case 5: onlySuccess=!onlySuccess; break;
                case 6: subscriptions();return;
                case 7: deleteNodes(selected());return;
                default:return;
            }
            save();refresh();
        }).show();
    }
    private void deleteNodes(List<Nodes.Node> chosen){
        if(!idle())return;
        if(chosen.isEmpty()){toast("请先勾选要删除的节点");return;}
        List<Nodes.Node> snapshot=new ArrayList<>(chosen);
        new AlertDialog.Builder(this).setTitle("删除节点？").setMessage("将删除 "+snapshot.size()+" 个节点及其测速结果。").setPositiveButton("删除",(dialog,which)->{
            nodes.removeAll(snapshot);save();refresh();message.setText("已删除 "+snapshot.size()+" 个节点");
        }).setNegativeButton("取消",null).show();
    }
    private void settings(){if(!idle())return;LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),0,dp(20),0);String[] keys={"delay","download","timeout","seconds","limitMB"};String[] titles={"延迟测试地址","下载测试地址","延迟超时（1—30 秒）","下载时长（1—60 秒）","每节点流量上限（1—100MB）"};String[] defaults={"https://www.gstatic.com/generate_204","https://proof.ovh.net/files/100Mb.dat","5","10","10"};EditText[] inputs=new EditText[5];for(int i=0;i<5;i++){box.addView(label(titles[i],12,MUTED));EditText input=new EditText(this);input.setSingleLine();input.setText(i<2?prefs.getString(keys[i],defaults[i]):String.valueOf(prefs.getInt(keys[i],Integer.parseInt(defaults[i]))));if(i>1)input.setInputType(2);box.addView(input);inputs[i]=input;}ScrollView scroll=new ScrollView(this);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("测速设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{for(int i=0;i<2;i++){String url=inputs[i].getText().toString().trim();if(!url.startsWith("https://")&&!url.startsWith("http://"))throw new Exception("请输入 HTTP/HTTPS 地址");}int timeout=Integer.parseInt(inputs[2].getText().toString()),seconds=Integer.parseInt(inputs[3].getText().toString()),limit=Integer.parseInt(inputs[4].getText().toString());if(timeout<1||timeout>30||seconds<1||seconds>60||limit<1||limit>100)throw new Exception("数值超出允许范围");prefs.edit().putString("delay",inputs[0].getText().toString().trim()).putString("download",inputs[1].getText().toString().trim()).putInt("timeout",timeout).putInt("seconds",seconds).putInt("limitMB",limit).apply();dialog.dismiss();toast("设置已保存");}catch(Exception e){toast(e.getMessage());}}));dialog.show();}
    private void exportMenu(){if(!idle())return;List<Nodes.Node> chosen=selected();if(chosen.isEmpty()){toast("请先选择节点");return;}new AlertDialog.Builder(this).setTitle("导出选中节点").setItems(new String[]{"Mihomo YAML（速度名称 + QQ 直连）","分享链接 TXT","Base64 订阅 TXT"},(d,w)->{try{pendingExport=w==0?Nodes.yaml(Nodes.configuration(chosen,true)):Nodes.links(chosen,w==2);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(w==0?"application/octet-stream":"text/plain");intent.putExtra(Intent.EXTRA_TITLE,w==0?"nodescatch.yaml":w==1?"nodescatch-links.txt":"nodescatch-base64.txt");startActivityForResult(intent,102);}catch(Exception e){error(e);}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{if(request==101){try(InputStream in=getContentResolver().openInputStream(uri)){parseInput(CoreEngine.readLimited(in,5*1024*1024),"");}}else if(request==102&&pendingExport!=null){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法打开保存位置");out.write(pendingExport.getBytes(StandardCharsets.UTF_8));}pendingExport=null;toast("导出完成");}}catch(Exception e){error(e);}}
    @Override protected void onStop(){if(busy)engine.cancel();super.onStop();}
    @Override protected void onDestroy(){engine.cancel();worker.shutdown();super.onDestroy();}
    private final class Adapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int i){return visible.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int i,View old,android.view.ViewGroup parent){
            Nodes.Node n=visible.get(i);
            LinearLayout outer=new LinearLayout(MainActivity.this);outer.setPadding(dp(10),dp(2),dp(10),dp(2));
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(1);card.setPadding(dp(6),dp(3),dp(6),dp(5));card.setBackground(shape(Color.WHITE,6));
            outer.addView(card,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox check=new CheckBox(MainActivity.this);check.setMinHeight(0);check.setMinimumHeight(0);check.setPadding(0,0,0,0);check.setChecked(n.selected);check.setEnabled(!busy);
            check.setOnCheckedChangeListener((b,v)->{n.selected=v;summary.setText(nodes.size()+" 个节点 · 已选 "+selected().size()+" · 显示 "+visible.size());});
            row.addView(check,new LinearLayout.LayoutParams(dp(30),dp(30)));
            TextView name=label(n.name(),11,NAVY);name.setTypeface(null,Typeface.BOLD);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));name.setGravity(Gravity.CENTER_VERTICAL);
            TextView remove=label("×",20,MUTED);remove.setGravity(Gravity.CENTER);remove.setContentDescription("删除此节点");remove.setOnClickListener(v->deleteNodes(Collections.singletonList(n)));
            row.addView(remove,new LinearLayout.LayoutParams(dp(32),dp(30)));card.addView(row);
            TextView metrics=label(n.type()+"  延迟 "+(n.delay<0?"—":n.delay+"ms")+"  均速 "+Nodes.speed(n.average)+"  峰值 "+Nodes.speed(n.peak),9,TEAL);
            metrics.setSingleLine();metrics.setEllipsize(android.text.TextUtils.TruncateAt.END);metrics.setPadding(dp(30),0,0,0);card.addView(metrics);
            TextView detail=label(n.status+" · "+n.proxy.optString("server")+":"+n.proxy.optInt("port"),8,MUTED);
            detail.setSingleLine();detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setPadding(dp(30),dp(2),0,0);card.addView(detail);
            card.setOnLongClickListener(v->{deleteNodes(Collections.singletonList(n));return true;});
            return outer;
        }
    }
}