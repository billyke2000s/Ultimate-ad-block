package com.example.systemblock;

import android.content.*;
import org.json.*;
import java.util.*;

final class Stats {
 private static Stats instance;
 static synchronized Stats get(Context c){if(instance==null)instance=new Stats(c);return instance;}
 private final SharedPreferences prefs;
 long blocked,allowed,failed;
 final Map<String,Long> domains=new HashMap<>(),days=new TreeMap<>();
 private final QueryLog log=new QueryLog();
 private boolean recording;
 private long lastSave;
 Stats(Context c){
  prefs=c.getSharedPreferences("dns_stats",0);recording=prefs.getBoolean("record_activity",true);
  try{JSONObject j=new JSONObject(prefs.getString("data","{}"));blocked=j.optLong("blocked");allowed=j.optLong("allowed");failed=j.optLong("failed");load(j.optJSONObject("domains"),domains);load(j.optJSONObject("days"),days);trim();
   JSONArray history=j.optJSONArray("activity");if(history!=null)for(int i=Math.max(0,history.length()-QueryLog.LIMIT);i<history.length();i++){JSONObject e=history.optJSONObject(i);if(e!=null)log.add(e.optString("host"),e.optInt("outcome",2),e.optLong("time"));}
  }catch(JSONException ignored){}
 }
 void load(JSONObject j,Map<String,Long> m){if(j!=null)for(Iterator<String> i=j.keys();i.hasNext();){String k=i.next();m.put(k,j.optLong(k));}}
 synchronized void record(String host,int result){
  if(result==1){blocked++;domains.put(host,domains.getOrDefault(host,0L)+1);String day=java.time.LocalDate.now().toString();days.put(day,days.getOrDefault(day,0L)+1);trim();}else if(result==0)allowed++;else failed++;
  if(recording)log.add(host,result,System.currentTimeMillis());
  if(System.currentTimeMillis()-lastSave>5000)save();
 }
 synchronized List<QueryLog.Entry> activity(int outcome,String search){return log.latest(outcome,search);}
 synchronized boolean isRecording(){return recording;}
 synchronized void setRecording(boolean enabled){recording=enabled;prefs.edit().putBoolean("record_activity",enabled).apply();}
 synchronized void clearActivity(){log.clear();save();}
 private void trim(){while(domains.size()>200)domains.remove(Collections.min(domains.keySet(),Comparator.comparingLong(domains::get)));while(days.size()>90)days.remove(Collections.min(days.keySet()));}
 synchronized void save(){
  try{JSONObject j=new JSONObject();j.put("blocked",blocked);j.put("allowed",allowed);j.put("failed",failed);j.put("domains",new JSONObject(domains));j.put("days",new JSONObject(days));JSONArray activity=new JSONArray();List<QueryLog.Entry> entries=log.latest(2,"");
   for(int i=entries.size()-1;i>=0;i--){QueryLog.Entry e=entries.get(i);activity.put(new JSONObject().put("host",e.host).put("outcome",e.outcome).put("time",e.time));}
   j.put("activity",activity);prefs.edit().putString("data",j.toString()).apply();lastSave=System.currentTimeMillis();
  }catch(JSONException ignored){}
 }
 synchronized void clear(){blocked=allowed=failed=0;domains.clear();days.clear();log.clear();save();}
}
