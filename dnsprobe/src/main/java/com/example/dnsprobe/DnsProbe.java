package com.example.dnsprobe;

import android.app.*;
import android.net.*;
import android.os.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Installed only by CI. Exercises Android's real resolver as a separate app UID. */
public final class DnsProbe extends Activity {
 @Override public void onCreate(Bundle arguments){
  super.onCreate(arguments);android.widget.TextView view=new android.widget.TextView(this);
  view.setText("Checking cross-app DNS filtering…");setContentView(view);
  new Thread(this::check,"dns-probe").start();
 }
 private void check(){
  String result;
  try{
   if(checkSelfPermission(android.Manifest.permission.INTERNET)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new AssertionError("Probe INTERNET permission missing");
   ConnectivityManager cm=getSystemService(ConnectivityManager.class);
   long until=SystemClock.elapsedRealtime()+10000;boolean ready=false;
   while(!ready && SystemClock.elapsedRealtime()<until){
    Network network=cm.getActiveNetwork();NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);
    ready=caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    if(!ready)Thread.sleep(50);
   }
   if(!ready)throw new AssertionError("VPN not assigned to other app");
   String blocked="probe-"+System.nanoTime()+".doubleclick.net";
   if(lookup(blocked))throw new AssertionError("Blocked domain resolved");
   if(!lookup("example.com"))throw new AssertionError("Allowed domain did not resolve");
   result="PASS: other-app blocked/allowed Android DNS resolution\n";
  }catch(Throwable error){result="FAIL: other-app probe: "+error+"\n";}
  try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"result.txt"))){out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  catch(java.io.IOException error){android.util.Log.e("SystemBlock","Probe result write failed",error);}
  runOnUiThread(this::finish);
 }
 private boolean lookup(String host)throws Exception{
  if(Build.VERSION.SDK_INT>=29)return modernLookup(host);
  try{return InetAddress.getAllByName(host).length>0;}catch(UnknownHostException expected){return false;}
 }
 @android.annotation.TargetApi(29)
 private boolean modernLookup(String host)throws Exception{
  CountDownLatch done=new CountDownLatch(1);boolean[] resolved={false};Exception[] failure={null};
  android.os.CancellationSignal cancellation=new android.os.CancellationSignal();
  DnsResolver.getInstance().query(null,host,DnsResolver.FLAG_NO_CACHE_LOOKUP|DnsResolver.FLAG_NO_CACHE_STORE,Runnable::run,cancellation,
   new DnsResolver.Callback<List<InetAddress>>(){
    @Override public void onAnswer(List<InetAddress> addresses,int rcode){resolved[0]=rcode==0 && !addresses.isEmpty();done.countDown();}
    @Override public void onError(DnsResolver.DnsException error){failure[0]=error;done.countDown();}
   });
  if(!done.await(20,TimeUnit.SECONDS)){cancellation.cancel();throw new AssertionError("Android DNS timeout");}
  // A failed blocked query is expected, but a failed allowed lookup must fail the test.
  if(failure[0]!=null && host.equals("example.com"))throw failure[0];
  return resolved[0];
 }
}
