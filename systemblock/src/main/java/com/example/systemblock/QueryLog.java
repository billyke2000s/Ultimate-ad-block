package com.example.systemblock;

import java.util.*;

/** Bounded local activity, oldest first internally; snapshots are newest first. */
final class QueryLog {
 static final int LIMIT=200;
 static final class Entry {
  final String host; final int outcome; final long time;
  Entry(String host,int outcome,long time){this.host=host;this.outcome=outcome;this.time=time;}
 }
 private final Deque<Entry> entries=new ArrayDeque<>();
 void add(String host,int outcome,long time){
  if(host==null || host.isEmpty() || host.length()>253 || outcome < -1 || outcome > 1)return;
  entries.addLast(new Entry(host.toLowerCase(Locale.ROOT),outcome,time));
  while(entries.size()>LIMIT)entries.removeFirst();
 }
 List<Entry> latest(int outcome,String search){
  String needle=search.toLowerCase(Locale.ROOT).trim();List<Entry> result=new ArrayList<>();
  Iterator<Entry> it=entries.descendingIterator();while(it.hasNext()){Entry e=it.next();if((outcome==2||outcome==e.outcome)&&e.host.contains(needle))result.add(e);}
  return Collections.unmodifiableList(result);
 }
 void clear(){entries.clear();}
}
