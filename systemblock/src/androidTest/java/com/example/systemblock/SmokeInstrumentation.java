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
   if(VpnService.prepare(context)!=null)throw new AssertionError("VPN consent not granted");
   context.startActivity(new Intent(context,Dashboard.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   getUiAutomation().waitForIdle(1000,10000);
   for(int cycle=0;cycle<3;cycle++){
    context.startForegroundService(new Intent(context,BlockVpn.class));
    await(true);
    if(cycle==0){
     // A different app UID exercises the VPN, avoiding the provider UID's special routing.
     try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand("am instrument -w com.obskyra.dnsprobe/com.example.dnsprobe.DnsProbe"))){
      java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
      while((n=in.read(buf))!=-1){if(out.size()+n>65536)throw new AssertionError("Probe output too large");out.write(buf,0,n);}
      String report=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
      if(!report.contains("PASS: other-app") || report.contains("FAIL:"))throw new AssertionError(report);
     }
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
   context.startService(new Intent(context,BlockVpn.class).setAction("stop"));await(false);
   result.putString("stream","PASS: blocked DNS, encrypted allowed DNS, repeated start/stop, stop during startup, notification stop action.\n");
   capture();finish(Activity.RESULT_OK,result);
  }catch(Throwable error){
   context.startService(new Intent(context,BlockVpn.class).setAction("stop"));
   Stats stats=Stats.get(context);
   result.putString("stream","FAIL: "+error+"; state="+BlockVpn.status+"; blocked="+stats.blocked+"; allowed="+stats.allowed+"; failed="+stats.failed+"\n");capture();finish(Activity.RESULT_CANCELED,result);
  }
 }
 private void capture(){
  try{
   android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
   if(image!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),"dashboard.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
  }catch(Exception ignored){}
 }
 private static void await(boolean expected)throws Exception{
  long until=SystemClock.elapsedRealtime()+15000;
  while(SystemClock.elapsedRealtime()<until){if(BlockVpn.running==expected && (expected || BlockVpn.status.startsWith("Stopped")))return;Thread.sleep(50);}
  throw new AssertionError("Service state timeout: "+BlockVpn.status);
 }
}
