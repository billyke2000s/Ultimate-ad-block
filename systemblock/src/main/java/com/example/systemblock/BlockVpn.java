package com.example.systemblock;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.system.OsConstants;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public final class BlockVpn extends VpnService {
 static volatile boolean running;
 static volatile String status="Stopped";
 private volatile boolean stopping;
 private ParcelFileDescriptor tunnel;
 private Thread reader;
 private ThreadPoolExecutor workers;
 private final Set<DatagramSocket> sockets=ConcurrentHashMap.newKeySet();
 private FileOutputStream output;
 private DomainRules rules;
 public int onStartCommand(Intent i,int flags,int id){
  if(i!=null&&"stop".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
  if(reader!=null)return START_NOT_STICKY;
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("blocking","System-wide blocking",NotificationManager.IMPORTANCE_LOW));
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Dashboard.class),PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,BlockVpn.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
  startForeground(1,new Notification.Builder(this,"blocking").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("System Wide Block Browser").setContentText("Local DNS filtering is active").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build());
  status="Loading blocklist…";
  reader=new Thread(()->{try{
   rules=new DomainRules(getAssets().open("blocklist.txt"),getSharedPreferences("settings",0).getString("exceptions",""));
   if(stopping)return;
   tunnel=new Builder().setSession("System Wide Block Browser").setMtu(1500).addAddress("10.111.0.1",32).addDnsServer("10.111.0.2").addRoute("10.111.0.2",32).allowFamily(OsConstants.AF_INET6).setBlocking(true).establish();
   if(tunnel==null)throw new IOException("VPN permission was revoked");
   workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.AbortPolicy());
   output=new FileOutputStream(tunnel.getFileDescriptor());running=true;status="Protecting · "+rules.blocked.size()+" domains";
   FileInputStream input=new FileInputStream(tunnel.getFileDescriptor());byte[] buf=new byte[32767];int n;
   while(!stopping&&(n=input.read(buf))>0){byte[] packet=Arrays.copyOf(buf,n),q=DnsPacket.query(packet,n);if(q==null)continue;
    try{workers.execute(()->resolve(packet,q));}catch(RejectedExecutionException busy){Stats.get(this).record(DnsPacket.host(q),-1);send(packet,DnsPacket.error(q,2));}
   }
  }catch(Exception e){if(!stopping)status="Stopped: "+e.getClass().getSimpleName();}finally{stopSelf();}},"dns-reader");reader.start();return START_NOT_STICKY;
 }
 private void resolve(byte[] p,byte[] q){String host=DnsPacket.host(q);Stats stats=Stats.get(this);
  if(rules.blocks(host)){stats.record(host,1);send(p,DnsPacket.error(q,3));return;}
  try(DatagramSocket socket=new DatagramSocket()){
   sockets.add(socket);if(stopping||!protect(socket))throw new IOException("Cannot protect upstream socket");
   socket.connect(InetAddress.getByAddress(new byte[]{1,1,1,1}),53);socket.setSoTimeout(4000);
   socket.send(new DatagramPacket(q,q.length));byte[] buf=new byte[4096];DatagramPacket answer=new DatagramPacket(buf,buf.length);socket.receive(answer);
   byte[] a=Arrays.copyOf(buf,answer.getLength());if(!DnsPacket.validAnswer(q,a))throw new IOException("Invalid DNS answer");
   stats.record(host,0);send(p,a);
  }catch(Exception e){stats.record(host,-1);send(p,DnsPacket.error(q,2));}
  finally{sockets.removeIf(DatagramSocket::isClosed);}
 }
 private synchronized void send(byte[] p,byte[] q){if(!stopping&&output!=null)try{output.write(DnsPacket.reply(p,q));}catch(IOException ignored){}}
 public void onRevoke(){stopSelf();}
 public void onDestroy(){stopping=true;running=false;if(!status.startsWith("Stopped:"))status="Stopped";for(DatagramSocket s:sockets)s.close();if(tunnel!=null)try{tunnel.close();}catch(IOException ignored){}if(workers!=null)workers.shutdownNow();if(reader!=null)reader.interrupt();Stats.get(this).save();stopForeground(true);super.onDestroy();}
}
