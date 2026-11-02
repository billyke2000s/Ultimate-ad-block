package com.example.systemblock;
import java.io.*;
import java.util.*;
final class DomainRules {
 final Set<String> blocked=new HashSet<>(), allowed=new HashSet<>();
 DomainRules(InputStream input,String exceptions)throws IOException{
  try(BufferedReader r=new BufferedReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null){s=s.trim().toLowerCase(Locale.ROOT);if(!s.isEmpty()&&!s.startsWith("#"))blocked.add(s);}}
  for(String s:exceptions.split("[\\s,]+")){s=s.trim().toLowerCase(Locale.ROOT);if(s.matches("[a-z0-9.-]+"))allowed.add(s);}
 }
 boolean blocks(String host){if(matches(allowed,host))return false;return matches(blocked,host);}
 static boolean matches(Set<String> set,String host){String s=host.toLowerCase(Locale.ROOT);while(true){if(set.contains(s))return true;int i=s.indexOf('.');if(i<0)return false;s=s.substring(i+1);}}
}
