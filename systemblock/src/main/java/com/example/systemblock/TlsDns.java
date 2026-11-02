package com.example.systemblock;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import okhttp3.*;

/** Certificate-verified DNS over HTTPS, with protected sockets and bounded replies. */
final class TlsDns implements Closeable {
 interface Protector { boolean protect(Socket socket); }
 private final OkHttpClient client;
 private volatile Call active;
 private volatile boolean closed;
 TlsDns(Protector protector){
  client=new OkHttpClient.Builder()
   .socketFactory(new ProtectedSockets(protector))
   .dns(host->Collections.singletonList(InetAddress.getByAddress(new byte[]{1,1,1,1})))
   .connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(8,TimeUnit.SECONDS)
   .followRedirects(false).followSslRedirects(false)
   .build();
 }
 byte[] resolve(byte[] query)throws IOException{
  if(closed)throw new IOException("Resolver stopped");
  Request request=new Request.Builder().url("https://cloudflare-dns.com/dns-query")
   .header("Accept","application/dns-message")
   .post(RequestBody.create(query,MediaType.get("application/dns-message"))).build();
  Call call=client.newCall(request);active=call;
  if(closed){call.cancel();throw new IOException("Resolver stopped");}
  try(Response response=call.execute()){
   ResponseBody body=response.body();
   if(response.code()!=200 || body==null || body.contentType()==null ||
     !"application".equals(body.contentType().type()) || !"dns-message".equals(body.contentType().subtype()))throw new IOException("Invalid DNS HTTPS response");
   byte[] answer=readBody(body.byteStream());
   if(!DnsPacket.validAnswer(query,answer))throw new IOException("DNS answer mismatch");
   return answer;
  }finally{active=null;}
 }
 static byte[] readBody(InputStream in)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
  while((n=in.read(buffer))!=-1){if(out.size()+n>65535)throw new IOException("Oversized DNS answer");out.write(buffer,0,n);}
  if(out.size()<12)throw new IOException("Short DNS answer");return out.toByteArray();
 }
 @Override public void close(){closed=true;Call call=active;if(call!=null)call.cancel();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
 private static final class ProtectedSockets extends SocketFactory {
  private final Protector protector;
  ProtectedSockets(Protector protector){this.protector=protector;}
  @Override public Socket createSocket()throws IOException{
   Socket socket=new Socket();if(!protector.protect(socket)){socket.close();throw new IOException("Socket protection failed");}return socket;
  }
  private Socket connect(InetAddress host,int port,InetAddress local,int localPort)throws IOException{
   Socket socket=createSocket();try{if(local!=null)socket.bind(new InetSocketAddress(local,localPort));socket.connect(new InetSocketAddress(host,port),4000);return socket;}
   catch(IOException e){socket.close();throw e;}
  }
  @Override public Socket createSocket(String host,int port)throws IOException{return connect(InetAddress.getByName(host),port,null,0);}
  @Override public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return connect(InetAddress.getByName(host),port,local,localPort);}
  @Override public Socket createSocket(InetAddress host,int port)throws IOException{return connect(host,port,null,0);}
  @Override public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return connect(host,port,local,localPort);}
 }
}
