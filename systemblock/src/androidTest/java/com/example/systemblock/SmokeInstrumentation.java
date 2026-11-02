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
     // Blocked and ordinary lookups really traverse the TUN and encrypted upstream.
     byte[] blocked=lookup("doubleclick.net");
     if((blocked[3]&15)!=3)throw new AssertionError("Blocked lookup was not NXDOMAIN");
     byte[] allowed=lookup("example.com");
     if((allowed[3]&15)!=0 || DnsPacket.u16(allowed,6)==0)throw new AssertionError("Allowed lookup failed");
    }
    context.stopService(new Intent(context,BlockVpn.class));await(false);
   }
   // Stop during startup repeatedly, then ensure service can start cleanly afterwards.
   for(int cycle=0;cycle<3;cycle++){
    context.startForegroundService(new Intent(context,BlockVpn.class));
    context.stopService(new Intent(context,BlockVpn.class));
    SystemClock.sleep(250);await(false);
   }
   context.startForegroundService(new Intent(context,BlockVpn.class));await(true);
   context.startService(new Intent(context,BlockVpn.class).setAction("stop"));await(false);
   result.putString("stream","PASS: blocked DNS, encrypted allowed DNS, repeated start/stop, stop during startup, notification stop action.\n");
   finish(Activity.RESULT_OK,result);
  }catch(Throwable error){
   context.stopService(new Intent(context,BlockVpn.class));
   result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);
  }
 }
 private static void await(boolean expected)throws Exception{
  long until=SystemClock.elapsedRealtime()+15000;
  while(SystemClock.elapsedRealtime()<until){if(BlockVpn.running==expected && (expected || BlockVpn.status.startsWith("Stopped")))return;Thread.sleep(50);}
  throw new AssertionError("Service state timeout: "+BlockVpn.status);
 }
 private static byte[] lookup(String domain)throws Exception{
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
  bytes.write(new byte[]{1,2,1,0,0,1,0,0,0,0,0,0});
  for(String label:domain.split("\\.")){bytes.write(label.length());bytes.write(label.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
  bytes.write(new byte[]{0,0,1,0,1});byte[] q=bytes.toByteArray();
  try(DatagramSocket socket=new DatagramSocket()){
   socket.setSoTimeout(12000);socket.connect(InetAddress.getByName("10.111.0.2"),53);
   socket.send(new DatagramPacket(q,q.length));byte[] buf=new byte[4096];DatagramPacket a=new DatagramPacket(buf,buf.length);socket.receive(a);
   byte[] answer=java.util.Arrays.copyOf(buf,a.getLength());
   if(!DnsPacket.validAnswer(q,answer))throw new AssertionError("Invalid DNS answer");return answer;
  }
 }
}
