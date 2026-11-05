package com.example.systemblock;

import android.net.*;
import android.os.ParcelFileDescriptor;
import android.system.*;
import com.wireguard.config.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** In-process protocol engines; BlockVpn is the only VpnService. */
final class NativeSession implements Closeable {
 static {System.loadLibrary("ubgo");System.loadLibrary("ubvpn");}
 private final BlockVpn service;
 private final DomainRules rules;
 private final VpnProfiles.Profile profile;
 private volatile boolean closing,initializationFailed;
 private long context,wireguard,bridge,open;
 private Thread openThread;
 private final List<String> addresses=new ArrayList<>(),routes=new ArrayList<>(),dns=new ArrayList<>(),search=new ArrayList<>();
 private int mtu=1280;
 private ParcelFileDescriptor tun;
 volatile long connectedSince;
 volatile String connectionState="Connecting",server="";
 NativeSession(BlockVpn service,DomainRules rules,VpnProfiles.Profile profile){this.service=service;this.rules=rules;this.profile=profile;context=create();}
 static Config wireguard(String text)throws Exception{
  Config c=Config.parse(new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  if(c.getInterface().getAddresses().isEmpty()||c.getPeers().isEmpty())throw new IOException("WireGuard needs addresses and peers");
  if(!c.getInterface().getIncludedApplications().isEmpty()||!c.getInterface().getExcludedApplications().isEmpty())throw new IOException("Per-app bypass rules are unsupported");
  for(Peer p:c.getPeers())if(p.getAllowedIps().isEmpty()||!p.getEndpoint().isPresent())throw new IOException("Each peer needs AllowedIPs and Endpoint");
  if(c.getInterface().getDnsServers().isEmpty())throw new IOException("Set at least one DNS server in the profile");return c;
 }
 private String resolved(String content,boolean wg)throws Exception{
  StringBuilder out=new StringBuilder();Network network=service.underlying();if(network==null)throw new IOException("No underlying network");
  boolean inline=false;
  for(String line:content.split("\\r?\\n")){
   String t=line.trim();if(!wg&&t.startsWith("<")){inline=!t.startsWith("</");out.append(line).append('\n');continue;}
   if(!inline&&wg&&t.toLowerCase(Locale.ROOT).startsWith("endpoint")){
    int equals=t.indexOf('=');if(equals<0)throw new IOException("Invalid Endpoint");String endpoint=t.substring(equals+1).trim().split("#",2)[0].trim();int colon=endpoint.lastIndexOf(':');if(colon<0)throw new IOException("Endpoint port required");String host=endpoint.substring(0,colon).replace("[","").replace("]","");InetAddress ip=network.getAllByName(host)[0];String address=ip.getHostAddress();server=host;line="Endpoint = "+(ip instanceof Inet6Address?"["+address+"]":address)+endpoint.substring(colon);
   }else if(!inline&&!wg){String[] words=OpenProfile.tokens(t);if(words.length>=2&&words[0].equals("remote")){InetAddress ip=network.getAllByName(words[1])[0];server=words[1];line="remote "+ip.getHostAddress();for(int i=2;i<words.length;i++)line+=" "+words[i];}}
   out.append(line).append('\n');
  }return out.toString();
 }
 void start()throws Exception{
  if(profile.protocol.equals("WireGuard")){
   Config c=wireguard(resolved(profile.config,true));
   mtu=c.getInterface().getMtu().orElse(1280);if(mtu<1280||mtu>9000)throw new IOException("MTU must be 1280–9000");
   for(InetNetwork ip:c.getInterface().getAddresses())addresses.add(ip.toString());
   for(InetAddress ip:c.getInterface().getDnsServers())dns.add(ip.getHostAddress());search.addAll(c.getInterface().getDnsSearchDomains());
   for(Peer peer:c.getPeers())for(InetNetwork ip:peer.getAllowedIps())routes.add(ip.toString());
   String config=c.toWgUserspaceString();
   if(establishWireguard(config)<0)throw new IOException("WireGuard engine or tunnel initialization failed");
   connectionState="Waiting for WireGuard handshake";service.remoteStatus(connectionState);
  }else{
   String config=resolved(OpenProfile.validate(profile.config),false);
   synchronized(this){if(closing)return;open=openCreate();
   openThread=new Thread(()->{String error=openRun(open,config,profile.user,profile.password,profile.keyPassword);if(!closing){connectionState=error.isEmpty()?"Disconnected — traffic blocked":"OpenVPN: "+error+" — traffic blocked";connectedSince=0;service.remoteStatus(connectionState);}},"openvpn-core");openThread.start();}
  }
 }
 private int establishWireguard(String config)throws Exception{
  ParcelFileDescriptor[] pair=pair();
  try{
   wireguard=wgStart(context,pair[1].detachFd(),mtu,config);if(wireguard<0)return -1;
   establishBridge(pair[0]);return 1;
  }finally{pair[0].close();pair[1].close();}
 }
 private ParcelFileDescriptor[] pair()throws Exception{
  int[] fds=socketPair();if(fds.length!=2)throw new IOException("Socket pair failed");return new ParcelFileDescriptor[]{ParcelFileDescriptor.adoptFd(fds[0]),ParcelFileDescriptor.adoptFd(fds[1])};
 }
 private synchronized void establishBridge(ParcelFileDescriptor engine)throws Exception{
  if(closing)throw new IOException("Session stopped");if(dns.isEmpty()||addresses.isEmpty())throw new IOException("VPN server must provide addresses and DNS");
  BlockVpn.Builder builder=service.new Builder().setSession("AdBlock · "+profile.protocol).setMtu(mtu).setBlocking(true);
  boolean v4=false,v6=false;
  for(String address:addresses){String[] parts=address.split("/");builder.addAddress(parts[0],Integer.parseInt(parts[1]));if(parts[0].contains(":"))v6=true;else v4=true;}
  for(String route:routes){String[] parts=route.split("/");builder.addRoute(parts[0],Integer.parseInt(parts[1]));}
  // Capture virtual resolvers. Never call allowBypass in remote mode.
  if(v4)builder.addDnsServer("10.111.0.2").addRoute("10.111.0.2",32);
  if(v6)builder.addDnsServer("fd11:111::2").addRoute("fd11:111::2",128);
  for(String domain:search)builder.addSearchDomain(domain);
  // DNS upstream must be in an included route; adding host routes is explicit VPN DNS policy.
  for(String address:dns)builder.addRoute(address,address.contains(":")?128:32);
  ParcelFileDescriptor replacement=builder.establish();if(replacement==null)throw new IOException("VPN permission revoked");
  long old=bridge;ParcelFileDescriptor previous=tun;tun=replacement;
  long next=bridgeStart(context,ParcelFileDescriptor.dup(tun.getFileDescriptor()).detachFd(),ParcelFileDescriptor.dup(engine.getFileDescriptor()).detachFd(),mtu,String.join(",",addresses),String.join(",",dns));
  if(old>0)bridgeStop(old);if(previous!=null)previous.close();bridge=next;
  service.releaseGuard();if(next<0)throw new IOException("DNS bridge initialization failed");
 }
 // Native callbacks. Return failure to the engine on every exception.
 int protectSocket(int fd){return !closing&&service.protectRemote(fd)?1:0;}
 int filterHost(String host){return closing||service.getSharedPreferences("settings",0).getBoolean("block_domains",true)&&rules.blocks(host)?1:0;}
 int recordHost(String host,int result){if(!closing)Stats.get(service).record(host,result);return 1;}
 int newBuilder(){addresses.clear();routes.clear();dns.clear();search.clear();mtu=1280;return closing?0:1;}
 int addAddress(String address,int prefix){addresses.add(address+"/"+prefix);return 1;}
 int addRoute(String address,int prefix){routes.add(address+"/"+prefix);return 1;}
 int setServer(String address,int ignored){server=address;return 1;}
 int addDns(String address,int ignored){dns.add(address);return 1;}
 int addSearch(String domain,int ignored){search.add(domain);return 1;}
 int setMtu(int value){if(value<1280||value>9000)return 0;mtu=value;return 1;}
 int establish(){try{ParcelFileDescriptor[] pair=pair();try{establishBridge(pair[0]);return pair[1].detachFd();}finally{pair[0].close();pair[1].close();}}catch(Exception e){service.remoteStatus("VPN setup failed — traffic blocked");return -1;}}
 int onEvent(String name){
  if(closing)return 0;
  if(name.startsWith("ERROR:")&&name.substring(6).matches("[A-Z_]{1,64}")){connectionState=name.substring(6).replace('_',' ')+" — traffic blocked";connectedSince=0;}
  else if(name.equals("CONNECTED")){connectionState="Connected";connectedSince=android.os.SystemClock.elapsedRealtime();}
  else if(Arrays.asList("RECONNECTING","DISCONNECTED","AUTH_FAILED","CERT_VERIFY_FAIL","TLS_ERROR","CONNECTION_TIMEOUT","RESOLVE","CONNECTING","WAIT","TCP_CONNECT").contains(name)){connectionState=name.replace('_',' ');connectedSince=0;}
  service.remoteStatus(connectionState);return 1;
 }
 void fail(String message){initializationFailed=true;connectedSince=0;connectionState=message;service.remoteStatus(message);}
 synchronized void poll(){
  if(closing||initializationFailed)return;
  if(bridge>0&&!bridgeAlive(bridge)){connectedSince=0;connectionState="Packet bridge stopped — traffic blocked";service.remoteStatus(connectionState);return;}
  if(wireguard>0){String config=wgConfig(wireguard);long newest=0;for(String line:config.split("\n"))if(line.startsWith("last_handshake_time_sec="))try{newest=Math.max(newest,Long.parseLong(line.substring(line.indexOf('=')+1)));}catch(NumberFormatException ignored){}
   if(newest>0&&System.currentTimeMillis()/1000-newest<180){if(connectedSince==0)connectedSince=android.os.SystemClock.elapsedRealtime();connectionState="Connected";}
   else{connectedSince=0;connectionState="Waiting for WireGuard handshake — traffic blocked until peer responds";}service.remoteStatus(connectionState);
  }
 }
 String protocol(){return profile.protocol;}
 synchronized long bytes(boolean rx){return bridge>0?bridgeBytes(bridge,rx):0;}
 synchronized void networkChanged(){if(closing||initializationFailed)return;connectedSince=0;connectionState="Reconnecting — traffic blocked";if(wireguard>0)wgRebind(wireguard);if(open>0&&openThread!=null&&openThread.isAlive())openReconnect(open);service.remoteStatus(connectionState);}
 @Override public void close(){
  closing=true;
  long client;Thread thread;synchronized(this){client=open;thread=openThread;if(client>0)openStop(client);}
  if(thread!=null)try{thread.join();}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
  synchronized(this){if(client>0){openDestroy(client);open=0;}if(wireguard>0){wgStop(wireguard);wireguard=0;}if(bridge>0){bridgeStop(bridge);bridge=0;}if(tun!=null)try{tun.close();}catch(IOException ignored){}tun=null;if(context>0){destroy(context);context=0;}}
 }
 private native int[] socketPair();
 private native long create();private native void destroy(long ptr);
 private native long wgStart(long ptr,int fd,int mtu,String config);private native void wgStop(long handle);private native boolean wgRebind(long handle);private native String wgConfig(long handle);
 private native long bridgeStart(long ptr,int tun,int engine,int mtu,String addresses,String dns);private native void bridgeStop(long handle);private native boolean bridgeAlive(long handle);private native long bridgeBytes(long handle,boolean receive);
 private native long openCreate();private native String openRun(long ptr,String config,String user,String password,String keyPassword);private native void openStop(long ptr);private native void openReconnect(long ptr);private native void openDestroy(long ptr);
}
