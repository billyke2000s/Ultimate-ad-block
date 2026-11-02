package com.example.systemblock;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.Arrays;

public class DnsRobustnessTest {
 @Test public void queryBoundsRejectOversizedLength(){assertNull(DnsPacket.query(new byte[4],100));assertNull(DnsPacket.query(null,100));}
 @Test public void responseMayOmitQueryEdns(){
  byte[] q=withEdns(1232);byte[] answer=DnsPacket.error(q,3);
  assertTrue(answer.length<q.length);assertTrue(DnsPacket.validAnswer(q,answer));
  assertEquals(CoreTest.query().length,answer.length);assertEquals(0,DnsPacket.u16(answer,10));
 }
 @Test public void questionTypeAndClassMustMatch(){
  byte[] q=CoreTest.query(),a=DnsPacket.error(q,0);a[a.length-1]=2;
  assertFalse(DnsPacket.validAnswer(q,a));
 }
 @Test public void opcodeMismatchRejected(){byte[] q=CoreTest.query(),a=DnsPacket.error(q,0);a[2]|=8;assertFalse(DnsPacket.validAnswer(q,a));q[2]|=8;assertNull(DnsPacket.host(q));}
 @Test public void multipleQuestionsAndQueryAnswersRejected(){byte[] q=CoreTest.query();q[5]=2;assertNull(DnsPacket.host(q));q[5]=1;q[7]=1;assertNull(DnsPacket.host(q));}
 @Test public void responseKeepsRecursionAndCheckingDisabled(){byte[] q=CoreTest.query();q[2]=0;q[3]=16;byte[] a=DnsPacket.error(q,2);assertEquals(0,a[2]&1);assertEquals(16,a[3]&16);}
 @Test public void largeReplyTruncatesWithoutSplittingRecords(){byte[] q=CoreTest.query(),a=Arrays.copyOf(DnsPacket.error(q,0),900);byte[] result=DnsPacket.fitUdp(q,a);assertEquals(q.length,result.length);assertEquals(2,result[2]&2);assertTrue(DnsPacket.validAnswer(q,result));}
 @Test public void ednsBudgetHonouredAndCapped(){assertEquals(512,DnsPacket.udpLimit(CoreTest.query()));assertEquals(1232,DnsPacket.udpLimit(withEdns(4096)));assertEquals(512,DnsPacket.udpLimit(withEdns(128)));byte[] a=new byte[1000];assertSame(a,DnsPacket.fitUdp(withEdns(1232),a));}
 @Test public void encryptedBodyHandlesFragmentedReads()throws Exception {
  byte[] data=CoreTest.query();
  InputStream fragmented=new ByteArrayInputStream(data){@Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(1,len));}};
  assertArrayEquals(data,TlsDns.readBody(fragmented));
 }
 @Test(expected=IOException.class) public void oversizedEncryptedReplyRejected()throws Exception{TlsDns.readBody(new ByteArrayInputStream(new byte[65536]));}
 @Test(expected=IOException.class) public void shortEncryptedReplyRejected()throws Exception{TlsDns.readBody(new ByteArrayInputStream(new byte[11]));}
 private static byte[] withEdns(int budget){byte[] base=CoreTest.query(),q=Arrays.copyOf(base,base.length+11);q[11]=1;DnsPacket.put(q,base.length+1,41);DnsPacket.put(q,base.length+3,budget);return q;}
}
