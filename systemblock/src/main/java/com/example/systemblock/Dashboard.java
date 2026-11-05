package com.example.systemblock;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Compact overview, query activity and settings share one native shell. */
public final class Dashboard extends Activity {
 private static final int BG=0xff0d1016,CARD=0xff151a23,BORDER=0xff252c38;
 private static final int INK=0xfff3f5f9,MUTED=0xff919bad,GREEN=0xff77d9ad,BLUE=0xff91adff,ORANGE=0xffeab080;
 private LinearLayout root,body,nav,recent;
 private TextView vpnState;
 private final java.util.concurrent.ExecutorService profileWork=java.util.concurrent.Executors.newSingleThreadExecutor();
 private TextView heading,title,state,blocked,allowed,failed,rate;
 private Button toggle;
 private Chart chart;
 private int mode=1,page=0,filter=2;
 private String search="";
 private final Button[] tabs=new Button[4];
 private final Button[] navigation=new Button[3];
 private ActivityAdapter adapter;
 private ListView activityList;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1500);}};
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);if(saved!=null){mode=saved.getInt("chartMode",1);page=saved.getInt("page",0);filter=saved.getInt("filter",2);search=saved.getString("search","");}
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);shell();showPage(page);
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 float sp(float n){return n*getResources().getDisplayMetrics().scaledDensity;}
 private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),BORDER);return d;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return t;}
 private void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
 private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(surface(CARD,14));return v;}
 private Button action(String text,boolean primary,Runnable run){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setTextColor(primary?BG:INK);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x228ba7ff),surface(primary?BLUE:CARD,10),null));b.setOnClickListener(v->run.run());return b;}
 private void shell(){
  LinearLayout frame=column();frame.setBackgroundColor(BG);frame.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  LinearLayout toolbar=row();toolbar.setPadding(dp(12),dp(9),dp(18),dp(9));Button menu=action("☰",false,this::openVpnMenu);menu.setTag("hamburger-menu");menu.setContentDescription("Open navigation and VPN options");toolbar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));heading=label("AdBlock",22,INK,true);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));toolbar.addView(label("● ADBLOCK",10,MUTED,true));frame.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
  body=column();frame.addView(body,new LinearLayout.LayoutParams(-1,0,1));nav=row();nav.setPadding(dp(12),dp(6),dp(12),dp(6));nav.setBackgroundColor(CARD);
  String[] names={"Overview","Activity","Settings"};for(int i=0;i<3;i++){final int destination=i;navigation[i]=action(names[i],false,()->showPage(destination));navigation[i].setTag("nav-"+i);nav.addView(navigation[i],new LinearLayout.LayoutParams(0,dp(48),1));}frame.addView(nav);setContentView(frame);
 }
 void showPage(int destination){
  page=destination;body.removeAllViews();chart=null;state=null;adapter=null;activityList=null;recent=null;Arrays.fill(tabs,null);
  for(int i=0;i<3;i++){navigation[i].setSelected(i==page);navigation[i].setTextColor(i==page?BLUE:MUTED);navigation[i].setBackground(surface(i==page?0xff222c40:CARD,10));}
  heading.setText(page==0?"AdBlock":page==1?"Activity":page==3?"VPN Connections":"Settings");
  if(page==1){activity();}else{ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);root=column();root.setPadding(dp(16),dp(8),dp(16),dp(12));scroll.addView(root);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));if(page==0)overview();else if(page==3)vpnPage();else settingsPage();}update();
 }
 private void overview(){
  LinearLayout protection=card(),statusRow=row(),copy=column();title=label("Protection is paused",18,INK,true);copy.addView(title);gap(copy,5);state=label("Ready to start",11,MUTED,false);state.setTag("protection-state");state.setMaxLines(2);state.setEllipsize(TextUtils.TruncateAt.END);copy.addView(state);statusRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1));toggle=action("Start",true,this::toggleProtection);toggle.setTag("protection-toggle");LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(dp(82),dp(44));buttonParams.leftMargin=dp(10);statusRow.addView(toggle,buttonParams);protection.addView(statusRow);root.addView(protection);gap(root,10);
  LinearLayout counters=row();blocked=metric(counters,"Blocked",GREEN);allowed=metric(counters,"Allowed",BLUE);failed=metric(counters,"Failed",ORANGE);root.addView(counters);gap(root,10);
  LinearLayout panel=card();LinearLayout top=row();top.addView(label("DNS activity",15,INK,true),new LinearLayout.LayoutParams(0,-2,1));rate=label("0 queries",11,MUTED,false);top.addView(rate);panel.addView(top);gap(panel,8);
  LinearLayout modes=row();String[] names={"Pie","Ring","Bars","Week"};for(int i=0;i<4;i++){final int value=i;Button b=action(names[i],false,()->selectMode(value));b.setTextSize(11);b.setPadding(dp(2),0,dp(2),0);b.setTag("chart-mode-"+i);tabs[i]=b;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);if(i>0)lp.leftMargin=dp(4);modes.addView(b,lp);}panel.addView(modes);
  chart=new Chart();chart.setTag("activity-chart");panel.addView(chart,new LinearLayout.LayoutParams(-1,dp(getResources().getConfiguration().screenHeightDp<650?100:130)));root.addView(panel);gap(root,8);
  Button all=action("View allowed & blocked domains  →",false,()->showPage(1));all.setTag("view-activity");root.addView(all,new LinearLayout.LayoutParams(-1,dp(44)));selectMode(mode);
 }
 private TextView metric(LinearLayout parent,String name,int color){LinearLayout box=column();box.setPadding(dp(10),dp(4),dp(8),dp(4));TextView count=label("0",22,color,true);count.setSingleLine();count.setAutoSizeTextTypeUniformWithConfiguration(12,22,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(count);gap(box,3);box.addView(label(name,11,MUTED,false));parent.addView(box,new LinearLayout.LayoutParams(0,dp(55),1));return count;}
 private void activity(){
  LinearLayout container=column();container.setPadding(dp(16),dp(8),dp(16),0);body.addView(container,new LinearLayout.LayoutParams(-1,-1));
  EditText input=new EditText(this);input.setSingleLine();input.setTextSize(14);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint("Search domains");input.setPadding(dp(14),0,dp(14),0);input.setBackground(surface(CARD,12));input.setTag("domain-search");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setText(search);container.addView(input,new LinearLayout.LayoutParams(-1,dp(48)));gap(container,8);
  LinearLayout filters=row();String[] names={"All","Blocked","Allowed","Failed"};int[] outcomes={2,1,0,-1};for(int i=0;i<4;i++){final int outcome=outcomes[i];Button b=action(names[i],false,()->{filter=outcome;showPage(1);});b.setTextSize(11);b.setPadding(0,0,0,0);b.setTag("filter-"+outcome);b.setSelected(filter==outcome);b.setTextColor(filter==outcome?BLUE:MUTED);b.setBackground(surface(filter==outcome?0xff222c40:CARD,10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);if(i>0)lp.leftMargin=dp(4);filters.addView(b,lp);}container.addView(filters);gap(container,10);
  TextView note=label("Last 200 DNS queries · saved only on this device",11,MUTED,false);container.addView(note);gap(container,8);
  FrameLayout listFrame=new FrameLayout(this);activityList=new ListView(this);activityList.setTag("domain-list");activityList.setDivider(new android.graphics.drawable.ColorDrawable(BORDER));activityList.setDividerHeight(dp(1));activityList.setCacheColorHint(BG);adapter=new ActivityAdapter();activityList.setAdapter(adapter);listFrame.addView(activityList,new FrameLayout.LayoutParams(-1,-1));
  TextView empty=label("No matching queries yet.\nUse an app while protection is on.",14,MUTED,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(24),dp(12),dp(24));listFrame.addView(empty,new FrameLayout.LayoutParams(-1,-1));activityList.setEmptyView(empty);container.addView(listFrame,new LinearLayout.LayoutParams(-1,0,1));
  activityList.setOnItemClickListener((p,v,position,id)->domainDetails(adapter.entries.get(position)));
  input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){search=s.toString();refreshActivity();}public void afterTextChanged(Editable e){}});refreshActivity();
 }
 private final class ActivityAdapter extends BaseAdapter {
  List<QueryLog.Entry> entries=Collections.emptyList();
  public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
  public View getView(int position,View old,android.view.ViewGroup parent){return queryRow(entries.get(position));}
 }
 private LinearLayout queryRow(QueryLog.Entry e){
  LinearLayout row=row();row.setPadding(dp(4),dp(11),dp(4),dp(11));LinearLayout copy=column();TextView host=label(e.host,13,INK,true);host.setSingleLine();host.setEllipsize(TextUtils.TruncateAt.MIDDLE);copy.addView(host);gap(copy,4);copy.addView(label(android.text.format.DateFormat.format("HH:mm:ss",e.time).toString()+" · DNS query",10,MUTED,false));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  String outcome=e.outcome==1?"Blocked":e.outcome==0?"Allowed":"Failed";TextView result=label(outcome,11,e.outcome==1?GREEN:e.outcome==0?BLUE:ORANGE,true);result.setPadding(dp(8),0,0,0);row.addView(result);row.setContentDescription(e.host+", "+outcome+", "+android.text.format.DateFormat.format("HH:mm:ss",e.time));return row;
 }
 private void refreshActivity(){if(adapter==null)return;adapter.entries=Stats.get(this).activity(filter,search);adapter.notifyDataSetChanged();}
 private void domainDetails(QueryLog.Entry e){
  AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(e.host).setMessage((e.outcome==1?"Blocked by domain rules":e.outcome==0?"Allowed DNS lookup":"DNS lookup failed")+"\n\n"+android.text.format.DateFormat.format("dd MMM yyyy · HH:mm:ss",e.time)+"\n\nThis is a DNS outcome, not an individual ad or an app identity.").setNegativeButton("Close",null);
  if(e.outcome==1)builder.setPositiveButton("Allow domain",(d,n)->allowDomain(e.host));builder.show();
 }
 private void allowDomain(String host){String saved=getSharedPreferences("settings",0).getString("exceptions","");String value=saved.isEmpty()?host:saved+"\n"+host;getSharedPreferences("settings",0).edit().putString("exceptions",value).apply();Toast.makeText(this,"Allowed · restart protection to apply",Toast.LENGTH_LONG).show();}
 private void settingsPage(){
  section("Filtering");Switch blocking=new Switch(this);blocking.setText("Block domains");blocking.setTextColor(INK);blocking.setChecked(getSharedPreferences("settings",0).getBoolean("block_domains",true));blocking.setOnCheckedChangeListener((b,on)->getSharedPreferences("settings",0).edit().putBoolean("block_domains",on).apply());root.addView(blocking);setting("Allowed domains","Manage exceptions and subdomains",this::exceptions,"allowed-domains");setting("Private DNS","Open Android network settings",()->openSettings(android.provider.Settings.ACTION_WIRELESS_SETTINGS),null);setting("VPN settings","Manage Android VPN connections",()->openSettings("android.settings.VPN_SETTINGS"),null);gap(root,18);
  section("Activity & privacy");Switch recording=new Switch(this);recording.setText("Record recent domains");recording.setTextSize(14);recording.setTextColor(INK);recording.setPadding(dp(4),dp(14),dp(4),dp(14));recording.setChecked(Stats.get(this).isRecording());recording.setTag("record-activity");recording.setOnCheckedChangeListener((b,on)->Stats.get(this).setRecording(on));root.addView(recording);TextView disclosure=label("Stores the last 200 allowed, blocked and failed DNS queries on this device. Turn off to stop recording new entries.",12,MUTED,false);disclosure.setLineSpacing(dp(2),1);root.addView(disclosure);gap(root,8);
  setting("Clear recent activity","Delete query history; keep totals",()->new AlertDialog.Builder(this).setTitle("Clear activity?").setMessage("Deletes the saved recent-domain list from this device. Totals and exceptions stay saved.").setPositiveButton("Clear",(d,n)->{Stats.get(this).clearActivity();Toast.makeText(this,"Activity cleared",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show(),null);
  setting("Reset all statistics","Clear totals, charts and recent queries",this::reset,null);gap(root,18);section("About");setting("How protection works","Privacy, DNS and filtering limits",this::about,"about");gap(root,12);root.addView(label("AdBlock 0.6.0 experimental\nOn-device filtering by Obskyra",11,MUTED,false));
 }
 private void section(String text){TextView t=label(text.toUpperCase(Locale.UK),11,BLUE,true);t.setLetterSpacing(.08f);root.addView(t);gap(root,4);}
 private void setting(String name,String description,Runnable run,String tag){LinearLayout item=row();item.setPadding(dp(4),dp(13),dp(4),dp(13));LinearLayout copy=column();copy.addView(label(name,14,INK,true));gap(copy,4);copy.addView(label(description,11,MUTED,false));item.addView(copy,new LinearLayout.LayoutParams(0,-2,1));item.addView(label("›",22,MUTED,false));item.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x228ba7ff),null,null));item.setOnClickListener(v->run.run());item.setFocusable(true);item.setTag(tag);root.addView(item);View divider=new View(this);divider.setBackgroundColor(BORDER);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}
 private void openVpnMenu(){
  String[] options={"Dashboard","VPN Connections","Import VPN Profile","VPN Settings","AdBlock Settings","Activity & Statistics","Privacy & Diagnostics"};
  new AlertDialog.Builder(this).setTitle("AdBlock").setItems(options,(dialog,which)->{
   switch(which){case 0:showPage(0);break;case 1:showPage(3);break;case 2:importProfile();break;case 3:vpnSettings();break;case 4:showPage(2);break;case 5:showPage(1);break;default:diagnostics();}
  }).setNegativeButton("Close",null).show();
 }
 private void profileFailure(String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("VPN profile").setMessage(message).setPositiveButton("OK",null).show();}
 private void importProfile(){
  Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
  try{startActivityForResult(picker,3);}catch(ActivityNotFoundException e){profileFailure("No document picker is available on this device.");}
 }
 private void imported(android.net.Uri uri){
  profileWork.execute(()->{try{
   String name="VPN";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
   String text;boolean zip=name.toLowerCase(Locale.ROOT).endsWith(".zip");try(java.io.InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException();text=zip?OpenProfile.fromZip(in):new String(OpenProfile.read(in,OpenProfile.LIMIT),java.nio.charset.StandardCharsets.UTF_8);}
   boolean wg=!zip&&java.util.regex.Pattern.compile("(?im)^\\s*\\[Interface\\]\\s*$").matcher(text).find();if(wg)NativeSession.wireguard(text);else text=OpenProfile.validate(text);
   VpnProfiles store=new VpnProfiles(this);java.util.List<VpnProfiles.Profile> profiles=store.load();VpnProfiles.Profile p=new VpnProfiles.Profile(wg?"WireGuard":"OpenVPN",text);p.name=name.replaceFirst("(?i)\\.(conf|ovpn|zip)$","");profiles.add(p);store.save(profiles);
   runOnUiThread(()->{showPage(3);Toast.makeText(this,"Profile imported securely",Toast.LENGTH_SHORT).show();});
  }catch(Exception e){runOnUiThread(()->profileFailure(e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Import failed. Check profile format, certificate bundle and secure storage."));}});
 }
 private void vpnPage(){
  LinearLayout panel=card();vpnState=label("",13,INK,true);panel.addView(vpnState);gap(panel,8);
  panel.addView(action("Disconnect",false,()->startService(new Intent(this,BlockVpn.class).setAction("stop"))));root.addView(panel);gap(root,8);
  String selected=getSharedPreferences("settings",0).getString("vpn_profile","");
  setting("AdBlock only"+(selected.isEmpty()?" · Selected":""),"DNS filtering without a remote VPN",()->chooseProfile(""),null);
  profileWork.execute(()->{try{java.util.List<VpnProfiles.Profile> profiles=new VpnProfiles(this).load();runOnUiThread(()->{if(page!=3||isDestroyed())return;for(VpnProfiles.Profile p:profiles)setting(p.name+(p.id.equals(selected)?" · Selected":""),p.protocol+" · tap for connect, edit or delete",()->profileActions(p),null);setting("Import VPN profile","WireGuard .conf, OpenVPN .ovpn or certificate ZIP",this::importProfile,null);});}catch(Exception e){runOnUiThread(()->profileFailure("Encrypted profiles could not be opened. Existing data has been preserved."));}});
  gap(root,12);root.addView(label("Split tunnels send traffic outside included routes directly. Unexpected failures keep included routes blocked. Disconnect releases the VPN routes.",11,MUTED,false));updateVpn();
 }
 private void chooseProfile(String id){
  if(!BlockVpn.status.startsWith("Stopped")){Toast.makeText(this,"Disconnect before selecting a different mode or profile",Toast.LENGTH_LONG).show();return;}
  getSharedPreferences("settings",0).edit().putString("vpn_profile",id).apply();showPage(3);
 }
 private void profileActions(VpnProfiles.Profile p){
  new AlertDialog.Builder(this).setTitle(p.name+" · "+p.protocol).setItems(new String[]{"Select","Connect","Edit name & credentials","Edit configuration","Delete"},(d,n)->{
   if(n==0){chooseProfile(p.id);}else if(n==1){if(!BlockVpn.status.startsWith("Stopped")){Toast.makeText(this,"Disconnect the active connection first",Toast.LENGTH_LONG).show();return;}chooseProfile(p.id);Intent consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,1);else start();}
   else if(n==2)editProfile(p,false);else if(n==3)editProfile(p,true);else new AlertDialog.Builder(this).setTitle("Delete "+p.name+"?").setMessage("Removes this profile and its stored credentials.").setPositiveButton("Delete",(x,y)->saveProfile(p,true)).setNegativeButton("Cancel",null).show();
  }).show();
 }
 private void editProfile(VpnProfiles.Profile p,boolean config){
  if(BlockVpn.running){Toast.makeText(this,"Disconnect before editing profiles",Toast.LENGTH_LONG).show();return;}
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),0);
  EditText name=new EditText(this);name.setSingleLine();name.setHint("Profile name");name.setText(p.name);
  EditText user=new EditText(this);user.setSingleLine();user.setHint("Username");user.setText(p.user);
  EditText password=new EditText(this);password.setSingleLine();password.setInputType(129);password.setHint("Password");password.setText(p.password);
  EditText keyPassword=new EditText(this);keyPassword.setSingleLine();keyPassword.setInputType(129);keyPassword.setHint("Private-key password (optional)");keyPassword.setText(p.keyPassword);
  EditText content=new EditText(this);content.setTextSize(12);content.setGravity(Gravity.TOP);content.setInputType(0x80001|0x20000);content.setText(p.config);
  if(config)form.addView(content,new LinearLayout.LayoutParams(-1,dp(260)));else{form.addView(name);if(p.protocol.equals("OpenVPN")){form.addView(user);form.addView(password);form.addView(keyPassword);}}
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(config?"Configuration":"Profile settings").setView(form).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
  dialog.setOnDismissListener(x->getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE));dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String value=content.getText().toString(),profileName=name.getText().toString().trim(),username=user.getText().toString(),pass=password.getText().toString(),keyPass=keyPassword.getText().toString();profileWork.execute(()->{try{if(config){if(p.protocol.equals("WireGuard"))NativeSession.wireguard(value);else valueValidation(value);}if(config)p.config=p.protocol.equals("OpenVPN")?OpenProfile.validate(value):value;else{p.name=profileName;p.user=username;p.password=pass;p.keyPassword=keyPass;}runOnUiThread(()->{dialog.dismiss();saveProfile(p,false);});}catch(Exception error){runOnUiThread(()->profileFailure("Configuration rejected. Check keys, addresses, DNS, certificates and supported directives."));}});
  }));dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();
 }
 private void valueValidation(String value)throws java.io.IOException{OpenProfile.validate(value);}
 private void saveProfile(VpnProfiles.Profile p,boolean delete){
  if(!BlockVpn.status.startsWith("Stopped")){Toast.makeText(this,"Disconnect before changing saved profiles",Toast.LENGTH_LONG).show();return;}
  profileWork.execute(()->{try{VpnProfiles store=new VpnProfiles(this);java.util.List<VpnProfiles.Profile> profiles=store.load();for(int i=0;i<profiles.size();i++)if(profiles.get(i).id.equals(p.id)){if(delete)profiles.remove(i);else profiles.set(i,p);break;}store.save(profiles);if(delete&&p.id.equals(getSharedPreferences("settings",0).getString("vpn_profile","")))getSharedPreferences("settings",0).edit().putString("vpn_profile","").apply();runOnUiThread(()->showPage(3));}catch(Exception e){runOnUiThread(()->profileFailure("Profile could not be saved. Existing encrypted data has been preserved."));}});
 }
 private void vpnSettings(){
  new AlertDialog.Builder(this).setTitle("VPN Settings").setMessage("Failure policy: fail closed. Remote failures retain captured routes until you disconnect. Split-tunnel routes remain a deliberate bypass. For protection after process death, enable Android Always-on VPN and Block connections without VPN while a remote profile is selected. Keep those settings off in AdBlock-only mode.\n\nDNS policy: the profile/server DNS resolvers are used through the encrypted tunnel. DNS host routes are included even for split tunnels. No public resolver fallback. Profiles without DNS are rejected. VPN server hostnames are bootstrapped through the underlying network.\n\nDisable Android Private DNS and app-specific encrypted DNS if you want domain filtering. Those encrypted lookups cannot be inspected. A VPN provider/server is required.").setPositiveButton("VPN connections",(d,n)->showPage(3)).setNeutralButton("Android VPN settings",(d,n)->openSettings("android.settings.VPN_SETTINGS")).setNegativeButton("Close",null).show();
 }
 private void diagnostics(){
  NativeSession session=BlockVpn.activeSession;
  new AlertDialog.Builder(this).setTitle("Privacy & Diagnostics").setMessage("State: "+BlockVpn.status+"\nProtocol: "+(session==null?"AdBlock only":session.protocol())+"\n\nProfiles use app-private, Keystore-backed AES-GCM encryption. DNS history remains local. No telemetry or query uploads. No TLS interception.\n\nRemote VPN mode is experimental. Engine and device verification are tracked separately; an interface alone does not prove a working connection.").setPositiveButton("Close",null).show();
 }
 private void updateVpn(){
  if(page!=3||vpnState==null)return;NativeSession s=BlockVpn.activeSession;String info=BlockVpn.status;
  if(s!=null){long duration=s.connectedSince==0?0:(SystemClock.elapsedRealtime()-s.connectedSince)/1000;info=s.protocol()+" · "+info+"\n"+s.server+" · "+duration+"s\nTunnel IP bytes ↓ "+s.bytes(true)+"  ↑ "+s.bytes(false);}
  vpnState.setText(info);
 }
 private void toggleProtection(){
  if(BlockVpn.running){startService(new Intent(this,BlockVpn.class).setAction("stop"));update();return;}
  String status=BlockVpn.status;
  if(!status.startsWith("Stopped"))return;
  Intent consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,1);else start();
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
  TextView body=label("DNS filtering and optional VPN\n\nAd and tracker domains are checked against bundled rules. In AdBlock-only mode, normal traffic goes directly to its destination and allowed DNS uses certificate-verified Cloudflare HTTPS with no plaintext fallback. In remote mode, captured traffic and allowed DNS use your selected VPN server. The server/resolver can observe the corresponding traffic.\n\nYour controls\n\nOnly one VPN can run per Android profile. AdBlock-only mode cannot carry ordinary traffic under Android lockdown. With a remote profile selected, Always-on VPN and Block connections without VPN can enforce protection after process death.\n\nWhat can bypass it\n\nSame-domain ads cannot be removed by domain filtering. Android Private DNS, an app’s encrypted DNS, hard-coded IPs, cached answers and explicitly selecting another network can bypass this filter. In AdBlock-only mode, client TCP DNS is not supported; large UDP answers are truncated and clients needing TCP may fail.\n\nYour statistics\n\nCounts represent DNS queries, not individual ads or bytes saved. Statistics stay on this device, with up to 200 blocked-domain counters, 90 daily entries and 200 recent queries (domain, result and time). Recent queries include allowed domains; recording can be disabled and the history cleared in Settings. Recent increments may be lost after abrupt process death.",15,INK,false);
  body.setPadding(dp(24),dp(12),dp(24),dp(24));body.setLineSpacing(dp(4),1);ScrollView scroll=new ScrollView(this);scroll.addView(body);
  new AlertDialog.Builder(this).setTitle("How protection works").setView(scroll).setPositiveButton("Got it",null).show();
 }
 void start(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},2);startForegroundService(new Intent(this,BlockVpn.class));handler.postDelayed(this::update,100);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK)start();if(request==3&&result==RESULT_OK&&data!=null&&data.getData()!=null)imported(data.getData());}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);Stats.get(this).save();super.onPause();}
 @Override protected void onSaveInstanceState(Bundle out){out.putInt("chartMode",mode);out.putInt("page",page);out.putInt("filter",filter);out.putString("search",search);super.onSaveInstanceState(out);}
 void selectMode(int selected){
  mode=selected;
  for(int i=0;i<tabs.length;i++)if(tabs[i]!=null){boolean chosen=i==mode;tabs[i].setSelected(chosen);tabs[i].setTextColor(chosen?BG:MUTED);tabs[i].setBackground(surface(chosen?BLUE:CARD,8));}
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
  if(page==3){updateVpn();return;}if(page==1){refreshActivity();return;}if(page!=0||state==null)return;
  String status=BlockVpn.status;boolean active=BlockVpn.running,stopped=status.startsWith("Stopped"),stopping=status.startsWith("Stopping")||status.startsWith("Restarting"),error=status.startsWith("DNS upstream unavailable");
  title.setText(error?"DNS needs attention":stopping?"Pausing…":active?"Protection is on":stopped?"Protection is paused":"Starting…");
  state.setText(status.equals("Stopped")?"Filter ad and tracker domains":status.replace("Filtering · ",""));
  toggle.setText(stopping?"Wait…":active?"Pause":stopped?"Start":"Wait…");toggle.setEnabled(!stopping&&(active||stopped));toggle.setAlpha(toggle.isEnabled()?1f:.5f);
  Stats s=Stats.get(this);synchronized(s){blocked.setText(String.format(Locale.UK,"%,d",s.blocked));allowed.setText(String.format(Locale.UK,"%,d",s.allowed));failed.setText(String.format(Locale.UK,"%,d",s.failed));rate.setText(String.format(Locale.UK,"%,d queries",s.blocked+s.allowed+s.failed));}
  chart.setContentDescription(chartDescription());chart.invalidate();
 }
 private final class Chart extends View {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int[] colors={GREEN,BLUE,ORANGE};
  Chart(){super(Dashboard.this);setFocusable(true);}
  private void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(sp(size));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(align);c.drawText(value,x,y,p);}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);Stats s=Stats.get(Dashboard.this);synchronized(s){float w=getWidth(),h=getHeight();long total=s.blocked+s.allowed+s.failed;
    if(mode<2){
     float radius=Math.min(w/2-dp(14),h/2-dp(6));float cx=w/2,cy=h/2;
     RectF ring=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);p.setStyle(Paint.Style.FILL);
     if(total==0){p.setColor(BORDER);c.drawOval(ring,p);}else{long[] values={s.blocked,s.allowed,s.failed};float angle=-90;for(int i=0;i<3;i++){p.setColor(colors[i]);float sweep=360f*values[i]/total;c.drawArc(ring,angle,sweep,true,p);angle+=sweep;}}
     if(mode==1||total==0){p.setColor(CARD);c.drawCircle(cx,cy,radius*.75f,p);text(c,total==0?"—":String.format(Locale.UK,"%.0f%%",100d*s.blocked/total),cx,cy+dp(3),22,INK,Paint.Align.CENTER);text(c,total==0?"No queries yet":"blocked",cx,cy+dp(20),10,MUTED,Paint.Align.CENTER);}return;
    }
    if(mode==2){
     java.util.List<Map.Entry<String,Long>> entries=new ArrayList<>(s.domains.entrySet());entries.sort((a,b)->Long.compare(b.getValue(),a.getValue()));
     if(entries.isEmpty()){text(c,"No blocked domains yet",w/2,h/2,13,MUTED,Paint.Align.CENTER);return;}
     long max=Math.max(1,entries.get(0).getValue());
     for(int i=0;i<Math.min(h<dp(130)?3:5,entries.size());i++){Map.Entry<String,Long> e=entries.get(i);float y=dp(19+i*26);String count=String.format(Locale.UK,"%,d",e.getValue());p.setTextSize(sp(11));float limit=Math.max(0,w-p.measureText(count)-dp(24));String name=android.text.TextUtils.ellipsize(e.getKey(),new android.text.TextPaint(p),limit,android.text.TextUtils.TruncateAt.END).toString();
      text(c,name,0,y,11,INK,Paint.Align.LEFT);text(c,count,w,y,11,MUTED,Paint.Align.RIGHT);p.setColor(BORDER);c.drawRoundRect(0,y+dp(6),w,y+dp(10),dp(3),dp(3),p);p.setColor(GREEN);c.drawRoundRect(0,y+dp(6),Math.max(dp(6),w*e.getValue()/max),y+dp(10),dp(3),dp(3),p);
     }return;
    }
    long[] values=new long[7];long max=1;for(int i=0;i<7;i++){values[i]=s.days.getOrDefault(java.time.LocalDate.now().minusDays(6-i).toString(),0L);max=Math.max(max,values[i]);}
    text(c,"Blocked queries / day",0,dp(20),10,MUTED,Paint.Align.LEFT);text(c,"Peak "+max,w,dp(20),10,MUTED,Paint.Align.RIGHT);
    float left=dp(15),right=w-dp(15),top=dp(36),bottom=h-dp(28);p.setColor(BORDER);p.setStrokeWidth(dp(1));for(int i=0;i<4;i++){float y=top+(bottom-top)*i/3;c.drawLine(left,y,right,y,p);}
    Path line=new Path();for(int i=0;i<7;i++){float x=left+(right-left)*i/6,y=bottom-(bottom-top)*values[i]/max;if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}
    p.setColor(GREEN);p.setStrokeWidth(dp(2.5f));p.setStyle(Paint.Style.STROKE);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
    for(int i=0;i<7;i++){float x=left+(right-left)*i/6,y=bottom-(bottom-top)*values[i]/max;p.setColor(GREEN);c.drawCircle(x,y,dp(3),p);String day=java.time.LocalDate.now().minusDays(6-i).getDayOfWeek().toString().substring(0,3);if(w<dp(280)&&i%2==1)continue;text(c,day,x,h-dp(10),10,MUTED,Paint.Align.CENTER);}
   }
  }
 }
}
