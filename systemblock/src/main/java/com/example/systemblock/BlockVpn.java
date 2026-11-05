package com.example.systemblock;

import android.app.*;
import android.content.*;
import android.net.*;
import java.net.*;
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
 static volatile NativeSession activeSession;
 private volatile NativeSession remote;
 private ParcelFileDescriptor guard;
 private final Map<ConnectivityManager.NetworkCallback,ParcelFileDescriptor> retiringTunnels=new HashMap<>();
 private ConnectivityManager.NetworkCallback networks;
 private volatile Network network,lastNetwork;
 private final ThreadPoolExecutor networkChanges=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
 private final Object lifecycle=new Object();
 private volatile boolean stopping;
 private boolean restartRequested;
 private ParcelFileDescriptor tunnel;
 private Thread reader;
 private ThreadPoolExecutor workers;
 private FileOutputStream output;
 private DomainRules rules;
 private final Set<TlsDns> resolvers=new HashSet<>();
 private final ThreadLocal<TlsDns> localResolver=new ThreadLocal<>();

 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null && "stop".equals(intent.getAction())){restartRequested=false;shutdown();return START_NOT_STICKY;}
  if(stopping){restartRequested=true;status="Restarting…";return START_NOT_STICKY;}
  if(reader!=null)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("blocking","System-wide blocking",NotificationManager.IMPORTANCE_LOW));
  startForeground(1,notification("Starting protection…"));
  status="Loading blocklist…";
  reader=new Thread(this::readTunnel,"dns-reader");reader.start();return getSharedPreferences("settings",MODE_PRIVATE).getString("vpn_profile","").isEmpty()?START_NOT_STICKY:START_STICKY;
 }
 private InputStream openRules()throws IOException{
  // Android's asset merger expands .gz files and strips the extension.
  try{return getAssets().open("blocklist.txt");}catch(FileNotFoundException absent){return new java.util.zip.GZIPInputStream(getAssets().open("blocklist.txt.gz"));}
 }
 private void readTunnel(){
  try{
   DomainRules loaded=new DomainRules(openRules(),getSharedPreferences("settings",MODE_PRIVATE).getString("exceptions",""));
   String selected=getSharedPreferences("settings",MODE_PRIVATE).getString("vpn_profile","");
   if(!selected.isEmpty()){runRemote(loaded,selected);return;}
   FileInputStream input;
   synchronized(lifecycle){
    if(stopping)return;
    // Publish all resources under the same lock used by shutdown. Stop cannot miss a new tunnel.
    rules=loaded;
    tunnel=new Builder().setSession("AdBlock").setMtu(1500)
      .addAddress("10.111.0.1",32).addDnsServer("10.111.0.2").addRoute("10.111.0.2",32)
      .allowBypass().allowFamily(OsConstants.AF_INET6).setBlocking(false).establish();
    if(tunnel==null)throw new IOException("VPN permission was revoked");
    workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
    output=new ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(tunnel.getFileDescriptor()));
    input=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(tunnel.getFileDescriptor()));
    running=true;status="Filtering · "+rules.blocked.size()+" domains · encrypted DNS";
   }
   try(FileInputStream owned=input){
   byte[] buffer=new byte[32767];
   StructPollfd descriptor=new StructPollfd();descriptor.fd=owned.getFD();descriptor.events=(short)OsConstants.POLLIN;
   while(!stopping){
    // Finite poll + nonblocking TUN: closing a descriptor cannot strand a blocking read.
    if(Os.poll(new StructPollfd[]{descriptor},250)==0)continue;
    if(stopping)break;
    if((descriptor.revents & OsConstants.POLLIN)==0)throw new IOException("Tunnel closed");
    int n=owned.read(buffer);if(n<=0)break;
    byte[] packet=Arrays.copyOf(buffer,n),query=DnsPacket.query(packet,n);
    if(query==null)continue;
    if(getSharedPreferences("settings",MODE_PRIVATE).getBoolean("block_domains",true)&&rules.blocks(DnsPacket.host(query))){complete(packet,query,DnsPacket.error(query,3),1);continue;}
    try{workers.execute(()->resolve(packet,query));}
    catch(RejectedExecutionException busy){complete(packet,query,DnsPacket.error(query,2),-1);}
   }
   }
  }catch(Exception e){synchronized(lifecycle){if(!stopping)status="Stopped: "+e.getClass().getSimpleName();}}
  finally{shutdown();}
 }
 Network underlying(){
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);
  Network active=cm.getActiveNetwork();NetworkCapabilities cap=cm.getNetworkCapabilities(active);
  if(cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))return active;
  for(Network n:cm.getAllNetworks()){cap=cm.getNetworkCapabilities(n);if(cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}
  if(network!=null)return network;
  for(Network n:cm.getAllNetworks()){cap=cm.getNetworkCapabilities(n);if(cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))return n;}
  return null;
 }
 private void networkChanged(){
  Network selected=underlying();if(Objects.equals(selected,lastNetwork))return;lastNetwork=selected;setUnderlyingNetworks(selected==null?new Network[0]:new Network[]{selected});NativeSession session=remote;
  if(session!=null&&!stopping)try{networkChanges.execute(session::networkChanged);}catch(RejectedExecutionException ignored){}
 }
 boolean protectRemote(int fd){
  try{if(!protect(fd))return false;Network n=underlying();if(n==null)return false;try(ParcelFileDescriptor owned=ParcelFileDescriptor.fromFd(fd)){n.bindSocket(owned.getFileDescriptor());}return true;}catch(IOException e){return false;}
 }
 private void runRemote(DomainRules loaded,String selected)throws Exception{

  synchronized(lifecycle){
   if(stopping)return;
   // Hold both families before handshaking. Never release routes on engine failure.
   guard=new Builder().setSession("AdBlock · connecting").setMtu(1280).addAddress("10.111.0.1",32).addAddress("fd11:111::1",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("10.111.0.2").setBlocking(false).establish();
   if(guard==null)throw new IOException("VPN permission revoked");
   rules=loaded;running=true;status="Connecting VPN — traffic blocked";
  }
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);
  networks=new ConnectivityManager.NetworkCallback(){
   @Override public void onAvailable(Network n){network=n;networkChanged();}
   @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities caps){if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))networkChanged();}
   @Override public void onLost(Network n){if(n.equals(network))network=null;networkChanged();}
  };
  cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networks);
  try{VpnProfiles.Profile profile=new VpnProfiles(this).find(selected);synchronized(lifecycle){if(stopping)return;remote=new NativeSession(this,loaded,profile);activeSession=remote;}remote.start();}catch(Exception|LinkageError failure){String message="VPN setup failed ("+failure.getClass().getSimpleName()+") — traffic blocked. Check profile, DNS and network.";NativeSession session=remote;if(session!=null)session.fail(message);else remoteStatus(message);}
  while(!stopping){NativeSession session=remote;if(session!=null)session.poll();Thread.sleep(1500);}
 }
 private Notification notification(String content){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Dashboard.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BlockVpn.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  NativeSession session=activeSession;String name=session==null?"AdBlock":"AdBlock · "+session.protocol();
  return new Notification.Builder(this,"blocking").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle(name).setContentText(content).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build();
 }
 void remoteStatus(String value){synchronized(lifecycle){if(!stopping&&!status.equals(value)){status=value;getSystemService(NotificationManager.class).notify(1,notification(value));}}}
 void finishHandover(ParcelFileDescriptor previous,String iface){
  ConnectivityManager cm=getSystemService(ConnectivityManager.class);
  ConnectivityManager.NetworkCallback observer=new ConnectivityManager.NetworkCallback(){
   @Override public void onAvailable(Network n){LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)onLinkPropertiesChanged(n,lp);}
   @Override public void onLinkPropertiesChanged(Network n,LinkProperties lp){
    if(!iface.equals(lp.getInterfaceName()))return;
    synchronized(lifecycle){if(!retiringTunnels.containsKey(this))return;ParcelFileDescriptor old=retiringTunnels.remove(this);if(old!=null)try{old.close();}catch(IOException ignored){}releaseGuard();try{cm.unregisterNetworkCallback(this);}catch(IllegalArgumentException ignored){}}
   }
  };
  synchronized(lifecycle){
   if(stopping){if(previous!=null)try{previous.close();}catch(IOException ignored){}return;}
   retiringTunnels.put(observer,previous);
   // Closing a former TUN before ConnectivityService applies the new interface can
   // remove the active resolver's network on newer Android releases.
   try{cm.registerNetworkCallback(new NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(),observer);}catch(RuntimeException unavailable){/* Retain the old descriptor fail-closed until shutdown. */}
  }
 }
 void releaseGuard(){synchronized(lifecycle){if(guard!=null)try{guard.close();}catch(IOException ignored){}guard=null;}}
 private void resolve(byte[] packet,byte[] query){
  if(stopping)return;
  try{
   TlsDns resolver=localResolver.get();
   if(resolver==null){
    resolver=new TlsDns(socket->{synchronized(lifecycle){return !stopping && protect(socket);}});
    synchronized(lifecycle){if(stopping)return;resolvers.add(resolver);localResolver.set(resolver);}
   }
   byte[] answer=resolver.resolve(query);
   setUpstreamStatus("Filtering · "+rules.blocked.size()+" domains · encrypted DNS");
   complete(packet,query,DnsPacket.fitUdp(query,answer),(answer[3]&15)==2?-1:0);
  }catch(IOException e){android.util.Log.w("SystemBlock","Encrypted resolver connection failed: "+e);setUpstreamStatus("DNS upstream unavailable: "+e.getClass().getSimpleName());complete(packet,query,DnsPacket.error(query,2),-1);}
 }
 private void setUpstreamStatus(String value){synchronized(lifecycle){if(!stopping)status=value;}}
 private void complete(byte[] packet,byte[] query,byte[] answer,int outcome){
  synchronized(lifecycle){
   if(stopping || output==null)return;
   try{output.write(DnsPacket.reply(packet,answer));Stats.get(this).record(DnsPacket.host(query),outcome);}
   catch(IOException e){status="Stopped: tunnel write failed";shutdown();}
  }
 }
 private void shutdown(){
  synchronized(lifecycle){
   if(stopping)return;
   stopping=true;running=false;
   if(!status.startsWith("Stopped:"))status="Stopping…";
   java.util.List<TlsDns> closing=new ArrayList<>(resolvers);resolvers.clear();
   if(output!=null)try{output.close();}catch(IOException ignored){}output=null;
   if(tunnel!=null){try{tunnel.close();}catch(IOException ignored){}tunnel=null;}
   NativeSession session=remote;remote=null;activeSession=null;
   if(networks!=null){getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networks);networks=null;}
   for(Map.Entry<ConnectivityManager.NetworkCallback,ParcelFileDescriptor> old:retiringTunnels.entrySet()){try{getSystemService(ConnectivityManager.class).unregisterNetworkCallback(old.getKey());}catch(IllegalArgumentException ignored){}if(old.getValue()!=null)try{old.getValue().close();}catch(IOException ignored){}}retiringTunnels.clear();
   ThreadPoolExecutor executor=workers;Thread oldReader=reader;
   if(oldReader!=null && oldReader!=Thread.currentThread())oldReader.interrupt();
   // TLS close_notify may write to the network on Android 8. Never close TLS on main.
   new Thread(()->{
    try{
     networkChanges.shutdownNow();
     if(session!=null)session.close();
     releaseGuard();
     if(executor!=null)executor.shutdownNow();
     for(TlsDns resolver:closing)try{resolver.close();}catch(RuntimeException e){android.util.Log.w("SystemBlock","Resolver shutdown failed: "+e);}
     if(oldReader!=null && oldReader!=Thread.currentThread())try{oldReader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
     Stats.get(this).save();
    }finally{stopSelf();}
   },"dns-shutdown").start();
  }
 }
 @Override public void onRevoke(){restartRequested=false;shutdown();}
 @Override public void onDestroy(){
  boolean restart=restartRequested;shutdown();
  if(!status.startsWith("Stopped:"))status="Stopped";
  stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
  if(restart && VpnService.prepare(this)==null)getApplicationContext().startForegroundService(new Intent(this,BlockVpn.class));
 }
}
