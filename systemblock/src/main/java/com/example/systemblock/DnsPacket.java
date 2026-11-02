package com.example.systemblock;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/** Strict IPv4/UDP DNS framing. Other traffic never enters the /32 DNS route. */
final class DnsPacket {
 static int u16(byte[] b,int p){return ((b[p]&255)<<8)|(b[p+1]&255);}
 static void put(byte[] b,int p,int v){b[p]=(byte)(v>>8);b[p+1]=(byte)v;}
 static byte[] query(byte[] b,int n){
  if(b==null || n<40 || n>b.length || (b[0]&255)!=0x45 || b[9]!=17 || (u16(b,6)&0x3fff)!=0 || u16(b,2)!=n || u16(b,22)!=53 || u16(b,24)!=n-20) return null;
  if(b[16]!=10 || (b[17]&255)!=111 || b[18]!=0 || b[19]!=2) return null;
  byte[] q=Arrays.copyOfRange(b,28,n);
  return host(q)==null?null:q;
 }
 static String host(byte[] q){
  if(q==null || q.length<17 || (q[2]&0xf8)!=0 || u16(q,4)!=1 || u16(q,6)!=0 || u16(q,8)!=0)return null;
  StringBuilder h=new StringBuilder(); int p=12;
  while(p<q.length){int len=q[p++]&255;if(len==0){if(p+4>q.length || h.length()==0 || h.length()>253)return null;return h.toString().toLowerCase(java.util.Locale.ROOT);}
   if(len>63 || p+len>q.length)return null;
   if(h.length()>0)h.append('.');
   String label=new String(q,p,len,StandardCharsets.US_ASCII);
   if(!label.matches("[a-zA-Z0-9_-]+"))return null;
   h.append(label);p+=len;
  }return null;
 }
 static int questionEnd(byte[] q){
  if(host(q)==null)return -1;
  int p=12;while(q[p]!=0)p+=1+(q[p]&255);return p+5;
 }
 static byte[] error(byte[] q,int code){
  int end=questionEnd(q);if(end<0)throw new IllegalArgumentException("Invalid question");
  // Remove EDNS/additional data as its count is zeroed in this synthetic reply.
  byte[] a=Arrays.copyOf(q,end);a[2]=(byte)(0x80|(q[2]&1));a[3]=(byte)(0x80|(q[3]&0x10)|code);
  Arrays.fill(a,6,12,(byte)0);return a;
 }
 static boolean validAnswer(byte[] q,byte[] a){
  int end=questionEnd(q);
  if(end<0 || a==null || a.length<end || a[0]!=q[0] || a[1]!=q[1] ||
    (a[2]&0xf8)!=0x80 || u16(a,4)!=1)return false;
  // Query EDNS options need not be echoed. Compare the question, not whole query length.
  return Arrays.equals(Arrays.copyOfRange(q,12,end),Arrays.copyOfRange(a,12,end));
 }
 static int udpLimit(byte[] q){
  int end=questionEnd(q);if(end<0)return 512;
  if(u16(q,10)==1 && q.length>=end+11 && q[end]==0 && u16(q,end+1)==41 &&
    end+11+u16(q,end+9)==q.length)return Math.max(512,Math.min(1232,u16(q,end+3)));
  return 512;
 }
 static byte[] fitUdp(byte[] q,byte[] a){
  if(a.length<=udpLimit(q))return a;
  byte[] truncated=error(q,a[3]&15);truncated[2]|=2;return truncated;
 }
 static byte[] reply(byte[] request,byte[] dns){
  byte[] b=new byte[28+dns.length];System.arraycopy(request,0,b,0,28);put(b,2,b.length);put(b,6,0);b[8]=64;
  for(int i=0;i<4;i++){b[12+i]=request[16+i];b[16+i]=request[12+i];}
  put(b,20,53);put(b,22,u16(request,20));put(b,24,8+dns.length);put(b,26,0);put(b,10,0);
  int sum=0;for(int i=0;i<20;i+=2)sum+=u16(b,i);while((sum>>16)!=0)sum=(sum&65535)+(sum>>16);put(b,10,~sum);
  System.arraycopy(dns,0,b,28,dns.length);return b;
 }
}
