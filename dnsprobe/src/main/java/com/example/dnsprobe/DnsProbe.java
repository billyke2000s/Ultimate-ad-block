package com.example.dnsprobe;

import android.app.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Installed only by CI. Runs as another app, never as the VPN's own UID. */
public final class DnsProbe extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   byte[] blocked=lookup("doubleclick.net");
   if((blocked[3]&15)!=3)throw new AssertionError("Blocked domain returned rcode "+(blocked[3]&15));
   byte[] allowed=lookup("example.com");
   if((allowed[3]&15)!=0 || u16(allowed,6)==0)throw new AssertionError("Allowed domain returned rcode "+(allowed[3]&15));
   if(InetAddress.getAllByName("example.com").length==0)throw new AssertionError("System DNS failed");
   result.putString("stream","PASS: other-app blocked/allowed DNS and system resolution\n");finish(Activity.RESULT_OK,result);
  }catch(Throwable error){result.putString("stream","FAIL: other-app probe: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
 }
 private byte[] lookup(String domain)throws Exception{
  ConnectivityManager cm=getTargetContext().getSystemService(ConnectivityManager.class);
  Network vpn=null;long until=SystemClock.elapsedRealtime()+10000;
  while(vpn==null && SystemClock.elapsedRealtime()<until){
   Network network=cm.getActiveNetwork();
   NetworkCapabilities caps=network==null?null:cm.getNetworkCapabilities(network);
   LinkProperties link=network==null?null:cm.getLinkProperties(network);
   if(caps!=null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && link!=null && !link.getDnsServers().isEmpty())vpn=network;
   if(vpn==null)Thread.sleep(50);
  }
  if(vpn==null)throw new AssertionError("VPN not available to other app");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write(new byte[]{1,2,1,0,0,1,0,0,0,0,0,0});
  for(String label:domain.split("\\.")){bytes.write(label.length());bytes.write(label.getBytes(StandardCharsets.US_ASCII));}
  bytes.write(new byte[]{0,0,1,0,1});byte[] query=bytes.toByteArray();
  try(DatagramSocket socket=new DatagramSocket()){
   socket.setSoTimeout(12000);socket.connect(InetAddress.getByName("10.111.0.2"),53);
   socket.send(new DatagramPacket(query,query.length));byte[] buffer=new byte[4096];DatagramPacket reply=new DatagramPacket(buffer,buffer.length);socket.receive(reply);
   byte[] answer=Arrays.copyOf(buffer,reply.getLength());
   if(answer.length<query.length || answer[0]!=query[0] || answer[1]!=query[1] || (answer[2]&0x80)==0 || !Arrays.equals(Arrays.copyOfRange(query,12,query.length),Arrays.copyOfRange(answer,12,query.length)))throw new AssertionError("Invalid DNS reply");
   return answer;
  }
 }
 private static int u16(byte[] b,int p){return (b[p]&255)*256+(b[p+1]&255);}
}
