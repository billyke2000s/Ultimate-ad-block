package com.example.dnsprobe;

import android.app.*;
import android.net.*;
import android.os.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Installed only by CI. Exercises Android's real resolver as a separate app UID. */
public final class DnsProbe extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   ConnectivityManager cm=getTargetContext().getSystemService(ConnectivityManager.class);
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
   result.putString("stream","PASS: other-app blocked/allowed Android DNS resolution\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable error){result.putString("stream","FAIL: other-app probe: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
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
