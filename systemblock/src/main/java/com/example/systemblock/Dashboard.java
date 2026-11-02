package com.example.systemblock;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
public final class Dashboard extends Activity {
 private LinearLayout root;private TextView state,totals;private View chart;private int mode;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1500);}};
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(0xff101a29);getWindow().setNavigationBarColor(0xff101a29);show();}
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xffe0eaff);t.setPadding(0,dp(10),0,dp(10));root.addView(t);return t;}
 Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
 void show(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setFitsSystemWindows(true);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(24),dp(22),dp(24));root.setBackgroundColor(0xff101a29);scroll.addView(root);setContentView(scroll);
  text("SYSTEM WIDE BLOCK BROWSER",12).setTextColor(0xff7bcfff);text("Your device. Fewer ads.",28);state=text("Stopped",17);
  button("Start protection",()->{Intent consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,1);else start();});
  button("Stop protection",()->stopService(new Intent(this,BlockVpn.class)));
  totals=text("",19);
  Spinner types=new Spinner(this);types.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Pie · DNS outcomes","Doughnut · DNS outcomes","Bars · top blocked domains","Line · last 7 days"}));root.addView(types);
  chart=new Chart();root.addView(chart,new LinearLayout.LayoutParams(-1,dp(260)));
  types.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int n,long id){mode=n;chart.invalidate();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
  text("Blue: allowed DNS queries   Green: blocked   Orange: failed",13);
  button("Allow domains / fix a broken app",()->{EditText input=new EditText(this);input.setHint("example.com, another.example");input.setText(getSharedPreferences("settings",0).getString("exceptions",""));new AlertDialog.Builder(this).setTitle("Allowed domains").setMessage("Separate domains with spaces, commas or new lines. Includes subdomains. Restart protection to apply.").setView(input).setPositiveButton("Save",(d,n)->getSharedPreferences("settings",0).edit().putString("exceptions",input.getText().toString()).apply()).setNegativeButton("Cancel",null).show();});
  button("Android VPN settings",()->startActivity(new Intent("android.settings.VPN_SETTINGS")));
  button("Reset statistics",()->new AlertDialog.Builder(this).setTitle("Clear saved DNS statistics?").setPositiveButton("Clear",(d,n)->{Stats.get(this).clear();update();}).setNegativeButton("Cancel",null).show());
  text("How protection works",21);text("Filters ad and tracker domains across apps using a local VPN. Your normal internet traffic goes directly to its destination. Allowed DNS queries go to Cloudflare (1.1.1.1) using standard DNS; this is not an encrypted privacy VPN.",14);
  text("Some ads cannot be blocked by domain, including ads delivered from the same servers as content. Apps using their own encrypted DNS or hard-coded IPs can bypass this filter. Android Private DNS may also bypass it. Only one VPN can run per profile. Do not enable ‘Block connections without VPN’: this DNS-only tunnel does not carry all traffic.",14);
  text("Statistics count DNS queries, not individual ads or bytes saved. Domain history is capped at 200 entries and daily history at 90 days. Everything is stored on this device; reset it above. TCP DNS and encrypted-DNS interception are not supported in this first version.",13);update();
 }
 void start(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);startForegroundService(new Intent(this,BlockVpn.class));}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK)start();}
 protected void onResume(){super.onResume();handler.post(refresh);}
 protected void onPause(){handler.removeCallbacks(refresh);Stats.get(this).save();super.onPause();}
 void update(){if(state==null)return;state.setText(BlockVpn.status);Stats s=Stats.get(this);synchronized(s){totals.setText(String.format(Locale.UK,"%,d blocked · %,d allowed · %,d failed",s.blocked,s.allowed,s.failed));}chart.invalidate();}
 final class Chart extends View {
  Paint p=new Paint(3);int[] colors={0xff7bcfff,0xff65dfb0,0xffffb978};Chart(){super(Dashboard.this);setContentDescription("DNS query statistics chart");}
  protected void onDraw(Canvas c){super.onDraw(c);Stats s=Stats.get(Dashboard.this);synchronized(s){float width=getWidth(),height=getHeight();p.setTextSize(dp(12));p.setStyle(Paint.Style.FILL);
   if(mode<2){long[] values={s.allowed,s.blocked,s.failed};long total=s.allowed+s.blocked+s.failed;if(total==0){label(c,"No DNS queries recorded yet",12,height/2);return;}float side=Math.min(width,height)-dp(45),left=(width-side)/2,top=(height-side)/2;RectF bounds=new RectF(left,top,left+side,top+side);float angle=-90;for(int i=0;i<3;i++){p.setColor(colors[i]);float sweep=360f*values[i]/total;c.drawArc(bounds,angle,sweep,true,p);angle+=sweep;}if(mode==1){p.setColor(0xff101a29);c.drawCircle(width/2,height/2,side*.30f,p);}return;}
   if(mode==2){java.util.List<Map.Entry<String,Long>> list=new ArrayList<>(s.domains.entrySet());list.sort((a,b)->Long.compare(b.getValue(),a.getValue()));if(list.isEmpty()){label(c,"No blocked domains yet",12,height/2);return;}long max=list.get(0).getValue();for(int i=0;i<Math.min(5,list.size());i++){Map.Entry<String,Long> e=list.get(i);float y=dp(18+i*45);String name=e.getKey();if(name.length()>30)name=name.substring(0,27)+"…";label(c,name+" · "+e.getValue(),8,y);p.setColor(colors[1]);c.drawRect(8,y+dp(6),8+(width-20)*e.getValue()/max,y+dp(17),p);}return;}
   long[] values=new long[7];long max=1;for(int i=0;i<7;i++){values[i]=s.days.getOrDefault(java.time.LocalDate.now().minusDays(6-i).toString(),0L);max=Math.max(max,values[i]);}label(c,"Blocked queries / day · peak "+max,8,dp(20));float priorX=0,priorY=0;for(int i=0;i<7;i++){float x=dp(12)+(width-dp(24))*i/6,y=height-dp(45)-(height-dp(85))*values[i]/max;p.setColor(colors[1]);p.setStrokeWidth(dp(3));if(i>0)c.drawLine(priorX,priorY,x,y,p);c.drawCircle(x,y,dp(4),p);label(c,java.time.LocalDate.now().minusDays(6-i).getDayOfWeek().toString().substring(0,1),x-dp(4),height-dp(15));priorX=x;priorY=y;}
  }}
  void label(Canvas c,String s,float x,float y){p.setColor(0xffe0eaff);p.setStyle(Paint.Style.FILL);c.drawText(s,x,y,p);}
 }
}
