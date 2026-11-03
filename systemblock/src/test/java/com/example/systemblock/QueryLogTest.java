package com.example.systemblock;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class QueryLogTest {
 @Test public void activityIncludesEveryOutcomeAndSearchIsCaseInsensitive(){
  QueryLog log=new QueryLog();log.add("ads.example",1,1);log.add("News.Example",0,2);log.add("timeout.example",-1,3);
  assertEquals(3,log.latest(2,"").size());assertEquals("timeout.example",log.latest(2,"").get(0).host);
  assertEquals(1,log.latest(1,"").size());assertEquals("news.example",log.latest(0," NEWS ").get(0).host);assertEquals(0,log.latest(-1,"ads").size());
 }
 @Test public void evictionKeepsTheNewest200Queries(){
  QueryLog log=new QueryLog();for(int i=0;i<250;i++)log.add("host"+i+".example",0,i);
  List<QueryLog.Entry> entries=log.latest(2,"");assertEquals(200,entries.size());assertEquals(249,entries.get(0).time);assertEquals(50,entries.get(199).time);
 }
 @Test public void snapshotsAreStableAndClearingRemovesAllOutcomes(){
  QueryLog log=new QueryLog();log.add("example.com",0,1);List<QueryLog.Entry> snapshot=log.latest(2,"");log.clear();assertTrue(log.latest(2,"").isEmpty());assertEquals(1,snapshot.size());
 }
 @Test public void invalidRecordsAreNotRetained(){
  QueryLog log=new QueryLog();log.add(null,0,0);log.add("",0,0);log.add("example.com",3,0);assertTrue(log.latest(2,"").isEmpty());
 }
}
