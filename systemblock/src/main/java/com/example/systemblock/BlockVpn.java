package com.example.systemblock;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.system.OsConstants;
import android.system.Os;
import android.system.StructPollfd;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class BlockVpn extends VpnService {
 static volatile boolean running;
 static volatile String status="Stopped";
 private final Object lifecycle=new Object();
 private volatile boolean stopping;
 private ParcelFileDescriptor tunnel;
 private Thread reader;
 private ThreadPoolExecutor workers;
 private FileOutputStream output;
 private DomainRules rules;
 private final Set<TlsDns> resolvers=new HashSet<>();
 private final ThreadLocal<TlsDns> localResolver=new ThreadLocal<>();

 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null && "stop".equals(intent.getAction())){shutdown();stopSelf();return START_NOT_STICKY;}
  if(reader!=null || stopping)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("blocking","System-wide blocking",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Dashboard.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BlockVpn.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"blocking").setSmallIcon(android.R.drawable.ic_lock_lock)
    .setContentTitle("System Wide Block Browser").setContentText("Local DNS filter — tap for status")
    .setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build());
  status="Loading blocklist…";
  reader=new Thread(this::readTunnel,"dns-reader");reader.start();return START_NOT_STICKY;
 }
 private void readTunnel(){
  try{
   DomainRules loaded=new DomainRules(getAssets().open("blocklist.txt"),getSharedPreferences("settings",MODE_PRIVATE).getString("exceptions",""));
   FileInputStream input;
   synchronized(lifecycle){
    if(stopping)return;
    // Publish all resources under the same lock used by shutdown. Stop cannot miss a new tunnel.
    rules=loaded;
    tunnel=new Builder().setSession("System Wide Block Browser").setMtu(1500)
      .addAddress("10.111.0.1",32).addDnsServer("10.111.0.2").addRoute("10.111.0.2",32)
      .allowFamily(OsConstants.AF_INET6).setBlocking(false).establish();
    if(tunnel==null)throw new IOException("VPN permission was revoked");
    workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
    output=new FileOutputStream(tunnel.getFileDescriptor());
    input=new FileInputStream(tunnel.getFileDescriptor());
    running=true;status="Filtering · "+rules.blocked.size()+" domains · encrypted DNS";
   }
   byte[] buffer=new byte[32767];
   StructPollfd descriptor=new StructPollfd();descriptor.fd=input.getFD();descriptor.events=(short)OsConstants.POLLIN;
   while(!stopping){
    // Finite poll + nonblocking TUN: closing a descriptor cannot strand a blocking read.
    if(Os.poll(new StructPollfd[]{descriptor},250)==0)continue;
    if(stopping)break;
    if((descriptor.revents & OsConstants.POLLIN)==0)throw new IOException("Tunnel closed");
    int n=input.read(buffer);if(n<=0)break;
    byte[] packet=Arrays.copyOf(buffer,n),query=DnsPacket.query(packet,n);
    if(query==null)continue;
    if(rules.blocks(DnsPacket.host(query))){complete(packet,query,DnsPacket.error(query,3),1);continue;}
    try{workers.execute(()->resolve(packet,query));}
    catch(RejectedExecutionException busy){complete(packet,query,DnsPacket.error(query,2),-1);}
   }
  }catch(Exception e){synchronized(lifecycle){if(!stopping)status="Stopped: "+e.getClass().getSimpleName();}}
  finally{shutdown();stopSelf();}
 }
 private void resolve(byte[] packet,byte[] query){
  if(stopping)return;
  try{
   TlsDns resolver=localResolver.get();
   if(resolver==null){
    resolver=new TlsDns(socket->{synchronized(lifecycle){return !stopping && protect(socket);}});
    synchronized(lifecycle){if(stopping)return;resolvers.add(resolver);localResolver.set(resolver);}
   }
   byte[] answer=resolver.resolve(query);
   if(!stopping)status="Filtering · "+rules.blocked.size()+" domains · encrypted DNS";
   complete(packet,query,DnsPacket.fitUdp(query,answer),(answer[3]&15)==2?-1:0);
  }catch(IOException e){if(!stopping)status="DNS upstream unavailable: "+e.getClass().getSimpleName();complete(packet,query,DnsPacket.error(query,2),-1);}
 }
 private void complete(byte[] packet,byte[] query,byte[] answer,int outcome){
  synchronized(lifecycle){
   if(stopping || output==null)return;
   try{output.write(DnsPacket.reply(packet,answer));Stats.get(this).record(DnsPacket.host(query),outcome);}
   catch(IOException e){status="Stopped: tunnel write failed";shutdown();stopSelf();}
  }
 }
 private void shutdown(){
  synchronized(lifecycle){
   if(stopping)return;
   stopping=true;running=false;
   if(!status.startsWith("Stopped:"))status="Stopped";
   for(TlsDns resolver:resolvers)resolver.close();resolvers.clear();
   if(tunnel!=null){try{tunnel.close();}catch(IOException ignored){}tunnel=null;}
   output=null;
   if(workers!=null)workers.shutdownNow();
   if(reader!=null && reader!=Thread.currentThread())reader.interrupt();
   Stats.get(this).save();
  }
 }
 @Override public void onRevoke(){shutdown();stopSelf();}
 @Override public void onDestroy(){shutdown();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
