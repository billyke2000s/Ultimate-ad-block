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
   if(getIntent().getBooleanExtra("expect_closed",false)){verifyClosed();writeResult("PASS: other-app fail-closed TCP checks\n");return;}
   String blocked="probe-"+System.nanoTime()+".doubleclick.net";
   boolean blocking=getIntent().getBooleanExtra("blocking",true);
   if(lookup(blocked)==blocking)throw new AssertionError("Blocking policy disagrees with DNS outcome");
   if(!lookup("example.com"))throw new AssertionError("Allowed domain did not resolve");
   InetAddress[] normal=InetAddress.getAllByName("example.com");
   if(normal.length==0)throw new AssertionError("Ordinary app resolution failed");
   InetAddress address=normal[0];for(InetAddress candidate:normal)if(candidate instanceof Inet4Address){address=candidate;break;}
   try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(address,443),8000);}
   String server=getIntent().getStringExtra("remote_ip");
   if(server!=null){
    // This address is reachable only through the controlled VPN server's interface.
    try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(server,8080),8000);socket.setSoTimeout(8000);socket.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));java.io.ByteArrayOutputStream response=new java.io.ByteArrayOutputStream();byte[] chunk=new byte[512];int n;while((n=socket.getInputStream().read(chunk))!=-1){if(response.size()+n>4096)throw new AssertionError("Oversized endpoint response");response.write(chunk,0,n);}String body=response.toString("US-ASCII"),prefix=server.substring(0,server.lastIndexOf('.')+1);if(!body.contains("VPN-ENDPOINT "+prefix))throw new AssertionError("Endpoint did not observe a VPN client source address");}
    // Ordinary Internet TCP must also work while captured by the remote VPN.
    try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("1.1.1.1",443),8000);}
    String subnet=server.split("\\.")[1];try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("fd"+subnet+"::1",8080),8000);socket.setSoTimeout(8000);socket.getOutputStream().write("GET / HTTP/1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();byte[] b=new byte[512];int n;while((n=socket.getInputStream().read(b))!=-1){if(data.size()+n>4096)throw new AssertionError("Oversized IPv6 reply");data.write(b,0,n);}if(!data.toString("US-ASCII").contains("VPN-ENDPOINT fd"+subnet+":"))throw new AssertionError("IPv6 traffic escaped the VPN source address");}
    tcpDns(blocking,"10.111.0.2");tcpDns(blocking,"fd11:111::2");
   }
   result="PASS: other-app blocked/allowed Android DNS, ordinary resolution and TCP connectivity\n";
  }catch(Throwable error){result="FAIL: other-app probe: "+error+"\n";}
  writeResult(result);
 }
 private void writeResult(String result){
  try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"result.txt"))){out.write(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  catch(java.io.IOException error){android.util.Log.e("SystemBlock","Probe result write failed",error);}
  runOnUiThread(this::finish);
 }
 private void verifyClosed()throws Exception{
  for(String address:new String[]{"1.1.1.1","2606:4700:4700::1111"}){
   boolean escaped=false;try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(address,443),2000);escaped=true;}catch(java.io.IOException expected){}
   if(escaped)throw new AssertionError("Traffic escaped fail-closed routes");
  }
 }
 private void tcpDns(boolean blocking,String resolver)throws Exception{
  String host="tcp-"+System.nanoTime()+".doubleclick.net";java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.io.DataOutputStream query=new java.io.DataOutputStream(bytes);query.writeShort(123);query.writeShort(0x100);query.writeShort(1);query.write(new byte[6]);for(String label:host.split("\\.")){query.writeByte(label.length());query.writeBytes(label);}query.writeByte(0);query.writeShort(1);query.writeShort(1);byte[] data=bytes.toByteArray();
  try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(resolver,53),8000);socket.setSoTimeout(8000);java.io.DataOutputStream out=new java.io.DataOutputStream(socket.getOutputStream());out.writeShort(data.length);out.write(data);java.io.DataInputStream in=new java.io.DataInputStream(socket.getInputStream());int n=in.readUnsignedShort();if(n<12||n>65535)throw new AssertionError("Invalid DNS TCP size");byte[] answer=new byte[n];in.readFully(answer);if(((answer[3]&15)==3)!=blocking)throw new AssertionError("TCP DNS blocking policy failed");}
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
