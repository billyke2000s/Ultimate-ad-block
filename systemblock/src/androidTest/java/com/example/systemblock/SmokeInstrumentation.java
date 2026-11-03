package com.example.systemblock;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.net.*;

/** Emulator integration test, run only after the workflow grants VPN consent via appops. */
public final class SmokeInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();Context context=getTargetContext();
  try{
   android.net.ConnectivityManager connectivity=context.getSystemService(android.net.ConnectivityManager.class);
   long networkDeadline=SystemClock.elapsedRealtime()+60000;boolean online=false;
   while(!online && SystemClock.elapsedRealtime()<networkDeadline){
    android.net.Network net=connectivity.getActiveNetwork();
    android.net.NetworkCapabilities caps=net==null?null:connectivity.getNetworkCapabilities(net);
    online=caps!=null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
    if(!online)Thread.sleep(250);
   }
   if(!online)throw new AssertionError("Emulator internet connection never validated before VPN startup");
   if(VpnService.prepare(context)!=null)throw new AssertionError("VPN consent not granted");
   context.startActivity(new Intent(context,Dashboard.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   getUiAutomation().waitForIdle(1000,10000);
   for(int cycle=0;cycle<3;cycle++){
    context.startForegroundService(new Intent(context,BlockVpn.class));
    await(true);
    if(cycle==0){
     Stats before=Stats.get(context);long blockedBefore,allowedBefore;
     synchronized(before){blockedBefore=before.blocked;allowedBefore=before.allowed;}
     // A different app UID exercises the VPN, avoiding the provider UID's special routing.
     shell("rm -f /sdcard/Android/data/com.obskyra.dnsprobe/files/result.txt");
     shell("am start -W -n com.obskyra.dnsprobe/com.example.dnsprobe.DnsProbe");
     String report="";long probeDeadline=SystemClock.elapsedRealtime()+60000;
     while(SystemClock.elapsedRealtime()<probeDeadline){
      report=shell("cat /sdcard/Android/data/com.obskyra.dnsprobe/files/result.txt");
      if(report.contains("PASS:") || report.contains("FAIL:"))break;Thread.sleep(100);
     }
     if(!report.contains("PASS: other-app") || report.contains("FAIL:"))throw new AssertionError(report);
     synchronized(before){if(before.blocked<=blockedBefore || before.allowed<=allowedBefore)throw new AssertionError("Queries did not traverse the local filter");}
     getUiAutomation().waitForIdle(1000,10000);
    }
    context.startService(new Intent(context,BlockVpn.class).setAction("stop"));await(false);
   }
   // Stop during startup repeatedly, then ensure service can start cleanly afterwards.
   for(int cycle=0;cycle<3;cycle++){
    context.startForegroundService(new Intent(context,BlockVpn.class));
    context.startService(new Intent(context,BlockVpn.class).setAction("stop"));
    SystemClock.sleep(250);await(false);
   }
   context.startForegroundService(new Intent(context,BlockVpn.class));await(true);
   dashboardEvidence(true);
   context.startService(new Intent(context,BlockVpn.class).setAction("stop"));await(false);
   dashboardEvidence(false);
   result.putString("stream","PASS: blocked DNS, encrypted allowed DNS, repeated start/stop, stop during startup, notification stop action.\n");
   capture();finish(Activity.RESULT_OK,result);
  }catch(Throwable error){
   String failureState=BlockVpn.status;
   context.startService(new Intent(context,BlockVpn.class).setAction("stop"));
   Stats stats=Stats.get(context);
   result.putString("stream","FAIL: "+error+"; state="+failureState+"; blocked="+stats.blocked+"; allowed="+stats.allowed+"; failed="+stats.failed+"\n");capture();finish(Activity.RESULT_CANCELED,result);
  }
 }
 private void dashboardEvidence(boolean active)throws Exception{
  Dashboard dashboard=(Dashboard)startActivitySync(new Intent(getTargetContext(),Dashboard.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  getUiAutomation().waitForIdle(500,5000);
  runOnMainSync(()->{
   android.view.View toggle=dashboard.getWindow().getDecorView().findViewWithTag("protection-toggle");
   if(!(toggle instanceof android.widget.Button))throw new AssertionError("Missing protection action");
   String expected=active?"Pause protection":"Start protection";
   if(!expected.contentEquals(((android.widget.Button)toggle).getText()))throw new AssertionError("Protection action disagrees with service");
  });
  capture(active?"dashboard-active.png":"dashboard-stopped.png");
  if(!active){
   for(int mode=0;mode<4;mode++){
    final int selected=mode;
    runOnMainSync(()->{
     android.view.View tab=dashboard.getWindow().getDecorView().findViewWithTag("chart-mode-"+selected);
     if(tab==null || !tab.performClick() || !tab.isSelected())throw new AssertionError("Chart selector failed");
     android.view.View chart=dashboard.getWindow().getDecorView().findViewWithTag("activity-chart");
     if(chart==null || chart.getContentDescription()==null || !chart.getContentDescription().toString().contains("DNS queries"))throw new AssertionError("Missing accessible chart values");
     chart.requestRectangleOnScreen(new android.graphics.Rect(0,0,chart.getWidth(),chart.getHeight()),true);
    });
    getUiAutomation().waitForIdle(500,5000);
    capture("chart-"+mode+".png");
   }
  }
 }
 private String shell(String command)throws Exception{
  try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))){
   java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
   while((n=in.read(buf))!=-1){if(out.size()+n>65536)throw new AssertionError("Shell output too large");out.write(buf,0,n);}
   return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
  }
 }
 private void capture(){capture("dashboard.png");}
 private void capture(String name){
  try{
   android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
   if(image!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
  }catch(Exception ignored){}
 }
 private static void await(boolean expected)throws Exception{
  long until=SystemClock.elapsedRealtime()+15000;
  while(SystemClock.elapsedRealtime()<until){if(BlockVpn.running==expected && (expected || BlockVpn.status.startsWith("Stopped")))return;Thread.sleep(50);}
  throw new AssertionError("Service state timeout: "+BlockVpn.status);
 }
}
