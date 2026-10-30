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
    private Button delaySort,speedSort;
    private String sortMetric="";
    private boolean sortAscending=true;
    private boolean busy=false,onlySuccess=false;
    private String pendingExport;
    private LinearLayout root;
    private AppPalette palette;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean refreshPending=new java.util.concurrent.atomic.AtomicBoolean();
    private final List<Button> idleButtons=new ArrayList<>();
    private Button stopButton;
    private ProgressBar progress;
    private boolean destroyed;
    private int MUTED;
    @Override public void onCreate(Bundle state){
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        String theme=prefs.getString("theme","system");
        boolean dark=theme.equals("dark")||(theme.equals("system")&&(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        palette=new AppPalette(dark);MUTED=palette.muted;
        setTheme(dark?R.style.AppThemeDark:R.style.AppThemeLight);
        super.onCreate(state);
        getWindow().setStatusBarColor(palette.background);getWindow().setNavigationBarColor(palette.background);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        engine=new CoreEngine(this);sortMetric=prefs.getString("sortMetric","");sortAscending=prefs.getBoolean("sortAscending",true);load();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout head=row();head.setPadding(dp(16),dp(8),dp(12),dp(8));
        TextView title=label("节点测速",20,palette.text);title.setTypeface(null,Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));title.setGravity(Gravity.CENTER_VERTICAL);
        addControl(head,"导入",this::importMenu);addControl(head,"设置",this::settings);addControl(head,"更多",this::more);root.addView(head);
        search=new EditText(this);search.setSingleLine();search.setTextSize(12);search.setTextColor(palette.text);search.setHintTextColor(palette.muted);search.setHint("搜索名称、协议或服务器");search.setPadding(dp(12),0,dp(12),0);search.setBackground(shape(palette.surface,10));
        LinearLayout.LayoutParams searchLP=new LinearLayout.LayoutParams(-1,dp(38));searchLP.setMargins(dp(12),0,dp(12),dp(6));root.addView(search,searchLP);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){refresh();}public void afterTextChanged(Editable s){}});
        LinearLayout stats=row();stats.setPadding(dp(16),0,dp(12),0);summary=label("",10,MUTED);stats.addView(summary,new LinearLayout.LayoutParams(0,dp(36),1));summary.setGravity(Gravity.CENTER_VERTICAL);
        addControl(stats,"全选",()->selectionAction(0));addControl(stats,"反选",()->selectionAction(1));addControl(stats,"范围",()->selectionAction(2));addControl(stats,"取消",()->selectionAction(3));root.addView(stats);
        LinearLayout sorts=row();sorts.setPadding(dp(12),dp(4),dp(12),dp(8));sorts.addView(label("排序  ",10,MUTED));delaySort=button("延迟 ↑",()->sortBy("delay"));speedSort=button("均速 ↓",()->sortBy("speed"));
        LinearLayout.LayoutParams sortLP=new LinearLayout.LayoutParams(0,dp(30),1);sortLP.setMargins(dp(4),0,0,0);sorts.addView(delaySort,sortLP);sorts.addView(speedSort,new LinearLayout.LayoutParams(sortLP));root.addView(sorts);
        ListView list=new ListView(this);list.setDivider(null);list.setDividerHeight(0);list.setClipToPadding(false);list.setPadding(0,0,0,dp(6));adapter=new Adapter();list.setAdapter(adapter);
        FrameLayout space=new FrameLayout(this);space.addView(list,new FrameLayout.LayoutParams(-1,-1));TextView empty=label("还没有节点\n点击右上角「导入」添加节点或订阅",13,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));space.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);root.addView(space,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(this);footer.setOrientation(1);footer.setPadding(dp(12),dp(8),dp(12),dp(10));footer.setBackgroundColor(palette.surface);
        message=label("先测延迟，再选择需要测下载的节点",10,MUTED);message.setSingleLine();message.setEllipsize(TextUtils.TruncateAt.END);footer.addView(message);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));LinearLayout.LayoutParams progressLP=new LinearLayout.LayoutParams(-1,dp(3));progressLP.setMargins(0,dp(6),0,dp(8));footer.addView(progress,progressLP);
        LinearLayout actions=row();for(String text:new String[]{"测延迟","测下载","停止","删除","导出"}){
            Button button=button(text,()->{if(text.equals("停止")){engine.cancel();message.setText("正在停止…");}else if(text.equals("导出"))exportMenu();else if(text.equals("删除"))deleteNodes(selected());else test(text.equals("测下载"));});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(text.equals("测延迟")?0:dp(5),0,0,0);actions.addView(button,lp);if(text.equals("停止"))stopButton=button;else idleButtons.add(button);
        }footer.addView(actions);root.addView(footer);refresh();
    }
    private LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private void addControl(LinearLayout parent,String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(32));lp.setMargins(dp(3),0,0,0);parent.addView(b,lp);idleButtons.add(b);}
    private void scheduleRefresh(){if(refreshPending.compareAndSet(false,true))ui.postDelayed(()->{refreshPending.set(false);if(!destroyed)refresh();},100);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private Button button(String text,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(11);b.setTextColor(palette.accent);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(3),0,dp(3),0);b.setAllCaps(false);b.setBackground(shape(palette.button,8));b.setStateListAnimator(null);b.setOnClickListener(v->run.run());return b;}
    private boolean idle(){if(busy){toast("请先停止当前测速");return false;}return true;}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void error(Exception e){if(destroyed||isFinishing())return;new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("知道了",null).show();}
    private void load(){try{File file=new File(getFilesDir(),"nodes.json");if(file.exists())try(InputStream in=new FileInputStream(file)){JSONArray a=new JSONArray(CoreEngine.readLimited(in,5*1024*1024));for(int i=0;i<a.length();i++)nodes.add(Nodes.Node.load(a.getJSONObject(i)));}}catch(Exception e){toast("本地记录读取失败，原文件已保留");}}
    private void save(){try{JSONArray a=new JSONArray();for(Nodes.Node n:nodes)a.put(n.save());android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"nodes.json"));FileOutputStream out=file.startWrite();try{out.write(a.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}catch(Exception e){toast("保存失败："+e.getMessage());}}
    private List<Nodes.Node> selected(){List<Nodes.Node> out=new ArrayList<>();for(Nodes.Node n:nodes)if(n.selected)out.add(n);return out;}
    private void refresh(){if(adapter==null||destroyed)return;for(Button b:idleButtons){b.setEnabled(!busy);b.setAlpha(busy?.4f:1f);}stopButton.setEnabled(busy);stopButton.setAlpha(busy?1f:.35f);if(!sortMetric.isEmpty())NodeActions.sort(nodes,sortMetric,sortAscending);String filter=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT);visible.clear();for(Nodes.Node n:nodes){String hay=n.name()+" "+n.type()+" "+n.proxy.optString("server");if(hay.toLowerCase(Locale.ROOT).contains(filter)&&(!onlySuccess||n.average>=0))visible.add(n);}updateSummary();if(delaySort!=null){delaySort.setText("延迟 "+(sortMetric.equals("delay")&&!sortAscending?"↓":"↑"));speedSort.setText("均速 "+(sortMetric.equals("speed")&&sortAscending?"↑":"↓"));delaySort.setTextColor(sortMetric.equals("delay")?palette.background:palette.accent);speedSort.setTextColor(sortMetric.equals("speed")?palette.background:palette.accent);delaySort.setBackground(shape(sortMetric.equals("delay")?palette.accent:palette.button,6));speedSort.setBackground(shape(sortMetric.equals("speed")?palette.accent:palette.button,6));}adapter.notifyDataSetChanged();}
    private void updateSummary(){summary.setText("已选 "+selected().size()+"/"+nodes.size()+(visible.size()!=nodes.size()?" · 当前 "+visible.size():""));}
    private void selectionAction(int action){
        if(!idle())return;
        try{
            if(action==0){for(Nodes.Node n:visible)n.selected=true;}
            else if(action==1)NodeActions.invert(visible);
            else if(action==2){int count=NodeActions.selectRange(visible);message.setText("已选中范围内 "+count+" 个节点");}
            else {for(Nodes.Node n:nodes)n.selected=false;}
            save();refresh();
        }catch(IllegalArgumentException e){toast(e.getMessage());}
    }
    private void sortBy(String metric){
        sortAscending=sortMetric.equals(metric)?!sortAscending:metric.equals("delay");sortMetric=metric;
        prefs.edit().putString("sortMetric",sortMetric).putBoolean("sortAscending",sortAscending).apply();
        save();refresh();message.setText((metric.equals("delay")?"按延迟":"按平均速度")+(sortAscending?"升序":"降序")+"，失败 / 未测节点置后");
    }
    private void importMenu(){if(!idle())return;new AlertDialog.Builder(this).setTitle("导入节点").setItems(new String[]{"粘贴分享链接 / Base64 / YAML","从文件导入","添加或更新订阅"},(d,index)->{if(index==0){EditText input=new EditText(this);input.setMinLines(5);input.setHint("粘贴节点或完整配置");input.setGravity(Gravity.TOP);new AlertDialog.Builder(this).setTitle("粘贴导入").setView(input).setPositiveButton("导入",(dialog,which)->parseInput(input.getText().toString(),"")).setNegativeButton("取消",null).show();}else if(index==1){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,101);}else subscriptionDialog();}).show();}
    private void parseInput(String text,String source){if(!idle())return;busy=true;refresh();message.setText("正在解析…");worker.execute(()->{try{List<Nodes.Node> imported=Nodes.parse(text);for(Nodes.Node n:imported)n.source=source;runOnUiThread(()->{if(!source.isEmpty())nodes.removeIf(n->n.source.equals(source));nodes.addAll(imported);busy=false;save();refresh();message.setText("已导入 "+imported.size()+" 个节点");});}catch(Exception e){runOnUiThread(()->{busy=false;refresh();error(e);message.setText("导入失败，已有节点未改动");});}});}
    private void subscriptionDialog(){EditText url=new EditText(this);url.setSingleLine();url.setHint("https://…");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);new AlertDialog.Builder(this).setTitle("订阅地址（仅保存在本机）").setView(url).setPositiveButton("获取",(d,w)->fetchSubscription(url.getText().toString().trim())).setNegativeButton("取消",null).show();}
    private void fetchSubscription(String url){if(!idle())return;busy=true;refresh();message.setText("正在获取订阅…");worker.execute(()->{try{List<Nodes.Node> imported=Nodes.parse(CoreEngine.subscription(url));for(Nodes.Node n:imported)n.source=url;runOnUiThread(()->{nodes.removeIf(n->n.source.equals(url));nodes.addAll(imported);busy=false;save();refresh();message.setText("订阅更新完成 · "+imported.size()+" 个节点");});}catch(Exception e){runOnUiThread(()->{busy=false;refresh();error(e);message.setText("订阅更新失败，旧节点已保留");});}});}
    private void subscriptions(){if(!idle())return;Set<String> urls=new LinkedHashSet<>();for(Nodes.Node n:nodes)if(!n.source.isEmpty())urls.add(n.source);if(urls.isEmpty()){subscriptionDialog();return;}String[] a=urls.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("选择订阅以更新").setItems(a,(d,w)->fetchSubscription(a[w])).setPositiveButton("新增订阅",(d,w)->subscriptionDialog()).setNegativeButton("关闭",null).show();}
    private void test(boolean download){if(!idle())return;List<Nodes.Node> chosen=selected();if(chosen.isEmpty()){toast("请先选择节点");return;}if(chosen.size()>2000){toast("每次最多测试 2000 个节点");return;}Runnable start=()->{busy=true;refresh();progress.setProgress(0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);String url=prefs.getString(download?"download":"delay",download?"https://proof.ovh.net/files/100Mb.dat":"https://www.gstatic.com/generate_204");worker.execute(()->{try{engine.run(chosen,download,url,prefs.getInt("seconds",10),prefs.getInt("limitMB",10),prefs.getInt("timeout",5)*1000,prefs.getInt(download?"downloadConcurrency":"delayConcurrency",download?CoreEngine.DEFAULT_DOWNLOAD_CONCURRENCY:CoreEngine.DEFAULT_DELAY_CONCURRENCY),new CoreEngine.Progress(){public void changed(Nodes.Node n){scheduleRefresh();}public void message(String s){runOnUiThread(()->{if(!destroyed)message.setText(s);});}public void completed(int done,int total){runOnUiThread(()->{if(!destroyed){progress.setMax(total);progress.setProgress(done);}});}});}catch(Throwable e){runOnUiThread(()->{message.setText("测速未完成");error(e instanceof Exception?(Exception)e:new Exception(e));});}finally{runOnUiThread(()->{busy=false;if(destroyed)return;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);save();refresh();});}});};if(download)new AlertDialog.Builder(this).setTitle("开始下载测速？").setMessage("已选 "+chosen.size()+" 个节点。每个节点最多 "+prefs.getInt("seconds",10)+" 秒、"+prefs.getInt("limitMB",10)+"MB 下载数据（实际流量含协议开销）；下载并发 "+prefs.getInt("downloadConcurrency",CoreEngine.DEFAULT_DOWNLOAD_CONCURRENCY)+"，共享当前带宽，流量会累加。离开应用会停止测速。").setPositiveButton("开始",(d,w)->start.run()).setNegativeButton("取消",null).show();else start.run();}
    private void more(){
        if(!idle())return;
        new AlertDialog.Builder(this).setTitle("节点管理").setItems(new String[]{"全选当前显示","取消全部选择","按平均速度排序","按延迟排序","按名称排序",onlySuccess?"显示所有节点":"只看下载成功","更新订阅","删除选中节点"},(d,w)->{
            switch(w){
                case 0: for(Nodes.Node n:visible){n.selected=true;} break;
                case 1: for(Nodes.Node n:nodes){n.selected=false;} break;
                case 2: sortMetric="speed";sortAscending=false; break;
                case 3: sortMetric="delay";sortAscending=true; break;
                case 4: sortMetric="";nodes.sort(Comparator.comparing(Nodes.Node::name)); break;
                case 5: onlySuccess=!onlySuccess; break;
                case 6: subscriptions();return;
                case 7: deleteNodes(selected());return;
                default:return;
            }
            prefs.edit().putString("sortMetric",sortMetric).putBoolean("sortAscending",sortAscending).apply();save();refresh();
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
    private void settings(){
        if(!idle())return;
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        box.addView(label("外观",12,MUTED));Spinner theme=new Spinner(this);String[] modes={"跟随系统","浅色","深色"};theme.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));String oldTheme=prefs.getString("theme","system");theme.setSelection(oldTheme.equals("dark")?2:oldTheme.equals("light")?1:0);box.addView(theme);
        String[] keys={"delay","download","timeout","seconds","limitMB","delayConcurrency","downloadConcurrency"};
        String[] titles={"延迟测试地址","下载测试地址","延迟超时（1—30 秒）","下载时长（1—60 秒）","每节点流量上限（1—100MB）","延迟并发（1—64）","下载并发（1—16）"};
        String[] defaults={"https://www.gstatic.com/generate_204","https://proof.ovh.net/files/100Mb.dat","5","10","10","8","4"};EditText[] inputs=new EditText[keys.length];
        for(int i=0;i<keys.length;i++){TextView caption=label(titles[i],11,MUTED);caption.setPadding(0,dp(10),0,0);box.addView(caption);EditText input=new EditText(this);input.setTextSize(13);input.setSingleLine();input.setText(i<2?prefs.getString(keys[i],defaults[i]):String.valueOf(prefs.getInt(keys[i],Integer.parseInt(defaults[i]))));if(i>1)input.setInputType(2);box.addView(input);inputs[i]=input;}
        box.addView(label("多个下载会共享带宽。要测单节点独占速度，请把下载并发设为 1。",11,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{
            for(int i=0;i<2;i++){String url=inputs[i].getText().toString().trim();if(!url.startsWith("https://")&&!url.startsWith("http://"))throw new Exception("请输入 HTTP/HTTPS 地址");}
            int[] maximum={30,60,100,64,16};int[] values=new int[5];for(int i=0;i<5;i++){values[i]=Integer.parseInt(inputs[i+2].getText().toString());if(values[i]<1||values[i]>maximum[i])throw new Exception(titles[i+2]+"：数值超出允许范围");}
            String newTheme=theme.getSelectedItemPosition()==2?"dark":theme.getSelectedItemPosition()==1?"light":"system";
            SharedPreferences.Editor editor=prefs.edit().putString("theme",newTheme);for(int i=0;i<2;i++)editor.putString(keys[i],inputs[i].getText().toString().trim());for(int i=0;i<5;i++)editor.putInt(keys[i+2],values[i]);editor.apply();save();dialog.dismiss();if(!oldTheme.equals(newTheme))recreate();else toast("设置已保存");
        }catch(Exception e){toast(e.getMessage());}}));dialog.show();
    }
    private void exportMenu(){if(!idle())return;List<Nodes.Node> chosen=selected();if(chosen.isEmpty()){toast("请先选择节点");return;}new AlertDialog.Builder(this).setTitle("导出选中节点").setItems(new String[]{"Mihomo YAML（速度名称 + QQ 直连）","分享链接 TXT","Base64 订阅 TXT"},(d,w)->{try{pendingExport=w==0?Nodes.yaml(Nodes.configuration(chosen,true)):Nodes.links(chosen,w==2);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(w==0?"application/octet-stream":"text/plain");intent.putExtra(Intent.EXTRA_TITLE,w==0?"nodescatch.yaml":w==1?"nodescatch-links.txt":"nodescatch-base64.txt");startActivityForResult(intent,102);}catch(Exception e){error(e);}}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();try{if(request==101){try(InputStream in=getContentResolver().openInputStream(uri)){parseInput(CoreEngine.readLimited(in,5*1024*1024),"");}}else if(request==102&&pendingExport!=null){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法打开保存位置");out.write(pendingExport.getBytes(StandardCharsets.UTF_8));}pendingExport=null;toast("导出完成");}}catch(Exception e){error(e);}}
    @Override protected void onStop(){if(busy)engine.cancel();save();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);engine.cancel();worker.shutdown();super.onDestroy();}
    private TextView metric(Nodes.Node n,String kind,String title,int state,String value){
        String content=(state==TestState.SUCCESS||state==TestState.RUNNING)&&!value.equals("—")?value:TestState.label(state);
        TextView view=label(title+"  "+content,9,palette.stateColor(state));view.setTag("metric."+kind+"."+n.id);view.setSingleLine();view.setEllipsize(TextUtils.TruncateAt.END);view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(dp(6),0,dp(4),0);view.setBackground(shape(palette.stateBackground(state),5));return view;
    }
    private final class Adapter extends BaseAdapter {
        public int getCount(){return visible.size();}
        public Object getItem(int i){return visible.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int i,View old,android.view.ViewGroup parent){
            Nodes.Node n=visible.get(i);
            LinearLayout outer=new LinearLayout(MainActivity.this);outer.setPadding(dp(12),dp(2),dp(12),dp(3));
            LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(1);card.setPadding(dp(8),dp(2),dp(6),dp(6));card.setBackground(shape(n.selected?palette.selected:palette.surface,9));outer.addView(card,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout row=row();TextView name=label(n.name(),11,palette.text);name.setTypeface(null,Typeface.BOLD);name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);name.setGravity(Gravity.CENTER_VERTICAL);row.addView(name,new LinearLayout.LayoutParams(0,dp(28),1));
            CheckBox check=new CheckBox(MainActivity.this);check.setMinHeight(0);check.setMinimumHeight(0);check.setPadding(0,0,0,0);check.setButtonTintList(android.content.res.ColorStateList.valueOf(palette.accent));check.setChecked(n.selected);check.setEnabled(!busy);check.setContentDescription("选择 "+n.name());
            check.setOnCheckedChangeListener((b,value)->{n.selected=value;save();updateSummary();card.setBackground(shape(value?palette.selected:palette.surface,9));});row.addView(check,new LinearLayout.LayoutParams(dp(30),dp(28)));
            TextView remove=label("×",19,MUTED);remove.setGravity(Gravity.CENTER);remove.setContentDescription("删除此节点");remove.setOnClickListener(v->deleteNodes(Collections.singletonList(n)));row.addView(remove,new LinearLayout.LayoutParams(dp(28),dp(28)));card.addView(row);
            LinearLayout metrics=row();TextView[] cells={metric(n,"delay","延迟",n.delayState,n.delay<0?"—":n.delay+"ms"),metric(n,"average","均速",n.speedState,Nodes.speed(n.average)),metric(n,"peak","峰值",n.speedState,Nodes.speed(n.peak))};
            for(int j=0;j<cells.length;j++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(23),1);lp.setMargins(j==0?0:dp(4),0,0,0);metrics.addView(cells[j],lp);}card.addView(metrics);
            TextView detail=label(n.type()+" · "+n.status+" · "+n.proxy.optString("server"),8,MUTED);detail.setSingleLine();detail.setEllipsize(TextUtils.TruncateAt.END);detail.setPadding(0,dp(4),0,0);card.addView(detail);
            card.setOnClickListener(v->{if(idle()){n.selected=!n.selected;save();refresh();}});card.setOnLongClickListener(v->{if(idle()){n.selected=!n.selected;save();refresh();}return true;});return outer;
        }
    }
}
