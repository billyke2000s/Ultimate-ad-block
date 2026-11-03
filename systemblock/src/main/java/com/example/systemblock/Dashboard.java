package com.example.systemblock;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native dashboard. The service remains the sole owner of protection state. */
public final class Dashboard extends Activity {
 private static final int BG=0xff0b1210, CARD=0xff141f1a, BORDER=0xff29392f;
 private static final int INK=0xffeff5ec, MUTED=0xff9eafa2, GREEN=0xffb2e584, BLUE=0xff83c9dc, ORANGE=0xffedb47b;
 private LinearLayout root;
 private TextView title, state, blocked, allowed, failed, rate;
 private Button toggle;
 private Chart chart;
 private Shield shield;
 private int mode=1;
 private final Button[] tabs=new Button[4];
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1500);}};

 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  if(saved!=null)mode=saved.getInt("chartMode",1);
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  getWindow().getDecorView().setSystemUiVisibility(0);
  show();
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 float sp(float n){return n*getResources().getDisplayMetrics().scaledDensity;}
 private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),BORDER);return d;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView label(String value,int size,int color,boolean bold){
  TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
  t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);
  t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
  return t;
 }
 private void gap(LinearLayout parent,int size){View space=new View(this);parent.addView(space,new LinearLayout.LayoutParams(1,dp(size)));}
 private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(surface(CARD,24));return v;}
 private Button action(String name,boolean primary,Runnable runnable){
  Button b=new Button(this);b.setText(name);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setMinWidth(0);
  b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setTextColor(primary?BG:INK);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x3369a658),surface(primary?GREEN:BG,16),null));
  b.setOnClickListener(v->runnable.run());return b;
 }
 private void show(){
  FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(BG);
  frame.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
  int contentWidth=Math.min(getResources().getDisplayMetrics().widthPixels,dp(660));
  FrameLayout.LayoutParams scrollParams=new FrameLayout.LayoutParams(contentWidth,-1,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
  frame.addView(scroll,scrollParams);root=column();root.setPadding(dp(20),dp(24),dp(20),dp(28));scroll.addView(root);setContentView(frame);

  LinearLayout masthead=row();LinearLayout brand=column();
  TextView eyebrow=label("OBSKYRA / NETWORK TOOLS",10,MUTED,true);eyebrow.setLetterSpacing(.12f);brand.addView(eyebrow);gap(brand,8);
  brand.addView(label("Ad Block",30,INK,true));masthead.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  Button settings=action("Settings",false,this::settings);settings.setTag("settings");masthead.addView(settings,new LinearLayout.LayoutParams(-2,-2));root.addView(masthead);gap(root,24);

  LinearLayout hero=card();
  GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff203528,0xff14231b});heroBg.setCornerRadius(dp(24));heroBg.setStroke(dp(1),0xff3c573d);hero.setBackground(heroBg);
  LinearLayout heroTop=row();LinearLayout copy=column();copy.addView(label("DEVICE PROTECTION",10,GREEN,true));gap(copy,10);
  title=label("Ready when you are",25,INK,true);copy.addView(title);heroTop.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  shield=new Shield();heroTop.addView(shield,new LinearLayout.LayoutParams(dp(60),dp(66)));hero.addView(heroTop);gap(hero,12);
  state=label("Protection is paused",13,MUTED,false);state.setTag("protection-state");state.setLineSpacing(dp(3),1);hero.addView(state);gap(hero,20);
  toggle=action("Start protection",true,this::toggleProtection);toggle.setTag("protection-toggle");hero.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
  gap(hero,12);TextView privacy=label("Local filtering  ·  Encrypted DNS",11,MUTED,false);privacy.setGravity(Gravity.CENTER);hero.addView(privacy);root.addView(hero);gap(root,24);

  root.addView(label("At a glance",19,INK,true));gap(root,5);root.addView(label("DNS queries · since your last reset",12,MUTED,false));gap(root,12);
  LinearLayout counters=row();blocked=metric(counters,"Blocked",GREEN,0);allowed=metric(counters,"Allowed",BLUE,8);failed=metric(counters,"Failed",ORANGE,8);root.addView(counters);gap(root,20);

  LinearLayout activity=card();LinearLayout activityTitle=row();activityTitle.addView(label("Network activity",18,INK,true),new LinearLayout.LayoutParams(0,-2,1));activity.addView(activityTitle);gap(activity,6);
  rate=label("No queries yet",12,MUTED,false);activity.addView(rate);gap(activity,16);
  HorizontalScrollView tabScroll=new HorizontalScrollView(this);tabScroll.setHorizontalScrollBarEnabled(false);LinearLayout tabRow=row();
  String[] names={"Pie","Ring","Domains","Week"};
  for(int i=0;i<4;i++){final int selected=i;Button tab=action(names[i],false,()->selectMode(selected));tab.setTextSize(12);tab.setTag("chart-mode-"+i);tabs[i]=tab;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));if(i>0)lp.leftMargin=dp(5);tabRow.addView(tab,lp);}
  tabScroll.addView(tabRow);activity.addView(tabScroll);gap(activity,8);
  chart=new Chart();chart.setTag("activity-chart");activity.addView(chart,new LinearLayout.LayoutParams(-1,dp(240)));
  LinearLayout legend=row();legend.setGravity(Gravity.CENTER);legend.addView(label("● Blocked   ",11,GREEN,true));legend.addView(label("● Allowed   ",11,BLUE,true));legend.addView(label("● Failed",11,ORANGE,true));activity.addView(legend);root.addView(activity);gap(root,20);

  LinearLayout controls=card();controls.addView(label("Make it yours",18,INK,true));gap(controls,6);controls.addView(label("Keep the domains you need within reach.",12,MUTED,false));gap(controls,14);
  Button exceptions=action("Manage allowed domains  →",false,this::exceptions);exceptions.setTag("allowed-domains");controls.addView(exceptions,new LinearLayout.LayoutParams(-1,-2));root.addView(controls);gap(root,16);
  Button about=action("How protection works  →",false,this::about);about.setTag("about");root.addView(about,new LinearLayout.LayoutParams(-1,-2));gap(root,16);
  TextView footer=label("YOUR DEVICE. FEWER DISTRACTIONS.",10,MUTED,true);footer.setGravity(Gravity.CENTER);footer.setLetterSpacing(.1f);root.addView(footer);
  selectMode(mode);update();
 }
 private TextView metric(LinearLayout row,String name,int color,int margin){
  LinearLayout box=column();box.setPadding(dp(12),dp(17),dp(8),dp(17));box.setBackground(surface(CARD,18));
  TextView count=label("0",24,color,true);count.setSingleLine(true);count.setHorizontallyScrolling(false);count.setAutoSizeTextTypeUniformWithConfiguration(12,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);
  box.addView(count);gap(box,7);box.addView(label(name,11,MUTED,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(margin);row.addView(box,lp);return count;
 }
 private void toggleProtection(){
  if(BlockVpn.running){startService(new Intent(this,BlockVpn.class).setAction("stop"));update();return;}
  String status=BlockVpn.status;
  if(!status.startsWith("Stopped"))return;
  Intent consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,1);else start();
 }
 private void settings(){
  new AlertDialog.Builder(this).setTitle("Settings").setItems(new String[]{"Allowed domains","Android Private DNS","Android VPN settings","Reset statistics","About protection"},(d,n)->{
   if(n==0)exceptions();else if(n==1)openSettings(android.provider.Settings.ACTION_WIRELESS_SETTINGS);else if(n==2)openSettings("android.settings.VPN_SETTINGS");else if(n==3)reset();else about();
  }).setNegativeButton("Close",null).show();
 }
 private void openSettings(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){new AlertDialog.Builder(this).setMessage("This device does not expose that settings page. Open Android Settings to change it.").setPositiveButton("OK",null).show();}}
 private void exceptions(){
  EditText input=new EditText(this);input.setHint("example.com, another.example");input.setTextSize(16);input.setMinLines(3);input.setGravity(Gravity.TOP);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
  input.setText(getSharedPreferences("settings",0).getString("exceptions",""));
  FrameLayout wrapper=new FrameLayout(this);wrapper.setPadding(dp(22),dp(10),dp(22),0);wrapper.addView(input);
  new AlertDialog.Builder(this).setTitle("Allowed domains").setMessage("Use spaces, commas or new lines. Subdomains are included. Restart protection after saving to apply changes.").setView(wrapper)
   .setPositiveButton("Save",(d,n)->{getSharedPreferences("settings",0).edit().putString("exceptions",input.getText().toString()).apply();Toast.makeText(this,"Saved · restart protection to apply",Toast.LENGTH_LONG).show();}).setNegativeButton("Cancel",null).show();
 }
 private void reset(){new AlertDialog.Builder(this).setTitle("Reset statistics?").setMessage("Clears saved query totals and domain history on this device. Your allowed domains stay saved.").setPositiveButton("Reset",(d,n)->{Stats.get(this).clear();update();}).setNegativeButton("Cancel",null).show();}
 private void about(){
  TextView body=label("A local DNS filter\n\nAd and tracker domains are checked against bundled rules. Normal internet traffic goes directly to its destination. Allowed DNS queries go to Cloudflare using certificate-verified HTTPS, with no plaintext fallback. The resolver can see those queries.\n\nYour controls\n\nOnly one VPN can run per Android profile. Do not enable ‘Block connections without VPN’: this DNS-only service does not carry all traffic.\n\nWhat can bypass it\n\nSame-domain ads cannot be removed by domain filtering. Android Private DNS, an app’s encrypted DNS, hard-coded IPs, cached answers and explicitly selecting another network can bypass this filter. Client TCP DNS is not supported; large UDP answers are truncated and clients needing TCP may fail.\n\nYour statistics\n\nCounts represent DNS queries, not individual ads or bytes saved. Statistics stay on this device, with up to 200 domain entries and 90 daily entries. Recent increments may be lost after abrupt process death.",15,INK,false);
  body.setPadding(dp(24),dp(12),dp(24),dp(24));body.setLineSpacing(dp(4),1);ScrollView scroll=new ScrollView(this);scroll.addView(body);
  new AlertDialog.Builder(this).setTitle("How protection works").setView(scroll).setPositiveButton("Got it",null).show();
 }
 void start(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);startForegroundService(new Intent(this,BlockVpn.class));handler.postDelayed(this::update,100);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK)start();}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);Stats.get(this).save();super.onPause();}
 @Override protected void onSaveInstanceState(Bundle out){out.putInt("chartMode",mode);super.onSaveInstanceState(out);}
 void selectMode(int selected){
  mode=selected;
  for(int i=0;i<tabs.length;i++)if(tabs[i]!=null){boolean chosen=i==mode;tabs[i].setSelected(chosen);tabs[i].setTextColor(chosen?BG:MUTED);tabs[i].setBackground(surface(chosen?GREEN:BG,12));}
  if(chart!=null){chart.invalidate();chart.setContentDescription(chartDescription());}
 }
 private String chartDescription(){
  Stats s=Stats.get(this);synchronized(s){String summary=String.format(Locale.UK,"%,d blocked, %,d allowed, %,d failed DNS queries.",s.blocked,s.allowed,s.failed);
   if(mode==2){java.util.List<Map.Entry<String,Long>> entries=new ArrayList<>(s.domains.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));StringBuilder text=new StringBuilder("Top blocked domains. ");for(int i=0;i<Math.min(5,entries.size());i++)text.append(entries.get(i).getKey()).append(": ").append(entries.get(i).getValue()).append(". ");return text+summary;}
   if(mode==3){StringBuilder text=new StringBuilder("Blocked DNS queries over seven days. ");for(int i=6;i>=0;i--){String day=java.time.LocalDate.now().minusDays(i).toString();text.append(day).append(": ").append(s.days.getOrDefault(day,0L)).append(". ");}return text+summary;}
   return (mode==0?"Pie chart. ":"Ring chart. ")+summary;
  }
 }
 void update(){
  if(state==null)return;String status=BlockVpn.status;boolean active=BlockVpn.running;
  boolean stopped=status.startsWith("Stopped");boolean stopping=status.startsWith("Stopping")||status.startsWith("Restarting");
  boolean upstreamError=status.startsWith("DNS upstream unavailable");
  title.setText(upstreamError?"DNS needs attention":stopping?"Pausing protection":active?"Protection is on":stopped?"Ready when you are":"Starting protection");
  state.setText(stopped?(status.equals("Stopped")?"Start to filter ad and tracker domains.":status):status);
  toggle.setText(stopping?"Please wait…":active?"Pause protection":stopped?"Start protection":"Starting…");toggle.setEnabled(!stopping&&(active||stopped));toggle.setAlpha(toggle.isEnabled()?1f:.55f);shield.active=active&&!upstreamError&&!stopping;shield.invalidate();
  Stats s=Stats.get(this);synchronized(s){blocked.setText(String.format(Locale.UK,"%,d",s.blocked));allowed.setText(String.format(Locale.UK,"%,d",s.allowed));failed.setText(String.format(Locale.UK,"%,d",s.failed));long total=s.blocked+s.allowed+s.failed;rate.setText(total==0?"Your activity will appear here":String.format(Locale.UK,"%.1f%% blocked · %,d total queries",100d*s.blocked/total,total));}
  chart.setContentDescription(chartDescription());chart.invalidate();
 }
 private final class Shield extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean active;
  Shield(){super(Dashboard.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/60f,getHeight()/66f);paint.setColor(0xff263e2b);paint.setStyle(Paint.Style.FILL);c.drawCircle(30,33,29,paint);
   Path path=new Path();path.moveTo(30,13);path.lineTo(46,20);path.lineTo(44,37);path.quadTo(42,46,30,53);path.quadTo(18,46,16,37);path.lineTo(14,20);path.close();paint.setColor(active?GREEN:MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(path,paint);
   if(active){path.reset();path.moveTo(22,32);path.lineTo(28,38);path.lineTo(39,26);c.drawPath(path,paint);}else{c.drawLine(25,33,35,33,paint);}c.restore();
  }
 }
 private final class Chart extends View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] colors={GREEN,BLUE,ORANGE};
  Chart(){super(Dashboard.this);setFocusable(true);}
  private void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(sp(size));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(align);c.drawText(value,x,y,p);}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);Stats s=Stats.get(Dashboard.this);synchronized(s){float w=getWidth(),h=getHeight();long total=s.blocked+s.allowed+s.failed;
    if(mode<2){
     float radius=Math.min(w/2-dp(14),h/2-dp(20));float cx=w/2,cy=h/2;
     RectF ring=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);p.setStyle(Paint.Style.FILL);
     if(total==0){p.setColor(BORDER);c.drawOval(ring,p);}else{long[] values={s.blocked,s.allowed,s.failed};float angle=-90;for(int i=0;i<3;i++){p.setColor(colors[i]);float sweep=360f*values[i]/total;c.drawArc(ring,angle,sweep,true,p);angle+=sweep;}}
     if(mode==1||total==0){p.setColor(CARD);c.drawCircle(cx,cy,radius*.75f,p);text(c,total==0?"—":String.format(Locale.UK,"%.0f%%",100d*s.blocked/total),cx,cy+dp(5),30,INK,Paint.Align.CENTER);text(c,total==0?"No queries yet":"blocked",cx,cy+dp(28),11,MUTED,Paint.Align.CENTER);}return;
    }
    if(mode==2){
     java.util.List<Map.Entry<String,Long>> entries=new ArrayList<>(s.domains.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
     if(entries.isEmpty()){text(c,"No blocked domains yet",w/2,h/2,13,MUTED,Paint.Align.CENTER);return;}
     long max=Math.max(1,entries.get(0).getValue());
     for(int i=0;i<Math.min(5,entries.size());i++){Map.Entry<String,Long> e=entries.get(i);float y=dp(26+i*44);String count=String.format(Locale.UK,"%,d",e.getValue());p.setTextSize(sp(11));float limit=Math.max(0,w-p.measureText(count)-dp(24));String name=android.text.TextUtils.ellipsize(e.getKey(),new android.text.TextPaint(p),limit,android.text.TextUtils.TruncateAt.END).toString();
      text(c,name,0,y,11,INK,Paint.Align.LEFT);text(c,count,w,y,11,MUTED,Paint.Align.RIGHT);p.setColor(BORDER);c.drawRoundRect(0,y+dp(9),w,y+dp(15),dp(3),dp(3),p);p.setColor(GREEN);c.drawRoundRect(0,y+dp(9),Math.max(dp(6),w*e.getValue()/max),y+dp(15),dp(3),dp(3),p);
     }return;
    }
    long[] values=new long[7];long max=1;for(int i=0;i<7;i++){values[i]=s.days.getOrDefault(java.time.LocalDate.now().minusDays(6-i).toString(),0L);max=Math.max(max,values[i]);}
    text(c,"Blocked queries / day",0,dp(22),11,MUTED,Paint.Align.LEFT);text(c,"Peak "+max,w,dp(22),11,MUTED,Paint.Align.RIGHT);
    float left=dp(15),right=w-dp(15),top=dp(48),bottom=h-dp(38);p.setColor(BORDER);p.setStrokeWidth(dp(1));for(int i=0;i<4;i++){float y=top+(bottom-top)*i/3;c.drawLine(left,y,right,y,p);}
    Path line=new Path();for(int i=0;i<7;i++){float x=left+(right-left)*i/6,y=bottom-(bottom-top)*values[i]/max;if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
    p.setColor(GREEN);p.setStrokeWidth(dp(2.5f));p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
    for(int i=0;i<7;i++){float x=left+(right-left)*i/6,y=bottom-(bottom-top)*values[i]/max;p.setColor(GREEN);c.drawCircle(x,y,dp(3),p);String day=java.time.LocalDate.now().minusDays(6-i).getDayOfWeek().toString().substring(0,3);if(w<dp(280)&&i%2==1)continue;text(c,day,x,h-dp(10),10,MUTED,Paint.Align.CENTER);}
   }
  }
 }
}
