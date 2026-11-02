package com.example.systemblock;

import java.io.*;
import java.net.*;
import java.util.Collections;
import javax.net.ssl.*;

/** One reusable TLS connection per bounded resolver worker. No plaintext fallback. */
final class TlsDns implements Closeable {
 interface Protector { boolean protect(Socket socket); }
 private final Protector protector;
 private volatile Socket transport;
 private SSLSocket tls;
 TlsDns(Protector protector) { this.protector=protector; }
 byte[] resolve(byte[] query) throws IOException {
  try {
   if(tls==null || tls.isClosed())connect();
   DataOutputStream out=new DataOutputStream(tls.getOutputStream());
   out.writeShort(query.length);out.write(query);out.flush();
   byte[] answer=readFrame(new DataInputStream(tls.getInputStream()));
   if(!DnsPacket.validAnswer(query,answer))throw new IOException("DNS answer mismatch");
   return answer;
  } catch(IOException e) { close();throw e; }
 }
 private void connect() throws IOException {
  Socket raw=new Socket();transport=raw;
  if(!protector.protect(raw)){close();throw new IOException("VPN stopped or socket protection failed");}
  raw.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{1,1,1,1}),853),4000);
  raw.setSoTimeout(4000);
  SSLSocket secure=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
    .createSocket(raw,"cloudflare-dns.com",853,true);
  tls=secure;
  SSLParameters params=secure.getSSLParameters();
  params.setEndpointIdentificationAlgorithm("HTTPS");
  params.setServerNames(Collections.singletonList(new SNIHostName("cloudflare-dns.com")));
  secure.setSSLParameters(params);secure.setSoTimeout(4000);secure.startHandshake();
 }
 static byte[] readFrame(DataInputStream in) throws IOException {
  int length=in.readUnsignedShort();
  if(length<12)throw new IOException("Invalid DNS frame length");
  byte[] answer=new byte[length];in.readFully(answer);return answer;
 }
 @Override public void close(){Socket raw=transport;transport=null;if(raw!=null)try{raw.close();}catch(IOException ignored){} }
}
