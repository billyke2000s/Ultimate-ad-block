package com.example.systemblock;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
public class CoreTest {
 @Test public void suffixAndExceptions()throws Exception{DomainRules r=new DomainRules(new ByteArrayInputStream("ads.example\ntracker.test\n".getBytes()),"safe.ads.example,\nallowed.test");assertTrue(r.blocks("sub.ads.example"));assertFalse(r.blocks("safe.ads.example"));assertFalse(r.blocks("notads.example"));assertTrue(r.allowed.contains("allowed.test"));}
 @Test public void malformedDnsRejected(){assertNull(DnsPacket.host(new byte[10]));byte[] q=query();q[12]=(byte)0xc0;assertNull(DnsPacket.host(q));}
 static byte[] query(){return new byte[]{1,2,1,0,0,1,0,0,0,0,0,0,3,97,100,115,7,101,120,97,109,112,108,101,0,0,1,0,1};}
 @Test public void responseValidation(){byte[] q=query();assertEquals("ads.example",DnsPacket.host(q));byte[] a=DnsPacket.error(q,3);assertTrue(DnsPacket.validAnswer(q,a));assertEquals(3,a[3]&15);a[0]++;assertFalse(DnsPacket.validAnswer(q,a));}
 @Test public void framing(){byte[] q=query(),p=new byte[28+q.length];p[0]=0x45;p[9]=17;DnsPacket.put(p,2,p.length);DnsPacket.put(p,20,12345);DnsPacket.put(p,22,53);DnsPacket.put(p,24,p.length-20);p[12]=10;p[15]=1;p[16]=10;p[17]=111;p[19]=2;System.arraycopy(q,0,p,28,q.length);assertArrayEquals(q,DnsPacket.query(p,p.length));byte[] reply=DnsPacket.reply(p,DnsPacket.error(q,3));assertEquals(53,DnsPacket.u16(reply,20));assertEquals(12345,DnsPacket.u16(reply,22));int sum=0;for(int i=0;i<20;i+=2)sum+=DnsPacket.u16(reply,i);while((sum>>16)>0)sum=(sum&65535)+(sum>>16);assertEquals(65535,sum);p[6]=0x20;assertNull(DnsPacket.query(p,p.length));}
}
