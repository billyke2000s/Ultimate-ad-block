package com.example.systemblock;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded import with no filesystem access or execution of profile directives. */
final class OpenProfile {
 static final int LIMIT=1024*1024;
 static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Import exceeds size limit");out.write(b,0,n);}return out.toByteArray();}
 static String fromZip(InputStream in)throws IOException{
  Map<String,String> files=new HashMap<>();String profile=null;int total=0;
  try(ZipInputStream zip=new ZipInputStream(in)){ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.isDirectory())continue;String name=e.getName();if(name.startsWith("/")||name.contains("\\")||Arrays.asList(name.split("/")).contains(".."))throw new IOException("Unsafe ZIP path");byte[] data=read(zip,LIMIT);total+=data.length;if(total>3*LIMIT||files.size()>=32)throw new IOException("ZIP limit exceeded");if(files.put(name,new String(data,StandardCharsets.UTF_8))!=null)throw new IOException("Duplicate ZIP path");if(name.toLowerCase(Locale.ROOT).endsWith(".ovpn")){if(profile!=null)throw new IOException("ZIP must contain one .ovpn");profile=name;}}}
  if(profile==null)throw new IOException("No .ovpn in ZIP");String base=profile.contains("/")?profile.substring(0,profile.lastIndexOf('/')+1):"";
  StringBuilder out=new StringBuilder();String inline=null;for(String line:files.get(profile).split("\\r?\\n")){
   String trimmed=line.trim();if(inline!=null){out.append(line).append('\n');if(trimmed.equals("</"+inline+">"))inline=null;continue;}if(trimmed.matches("<[a-zA-Z0-9-]+>")){inline=trimmed.substring(1,trimmed.length()-1);out.append(line).append('\n');continue;}
   String[] words=tokens(trimmed);if(words.length>=2&&Arrays.asList("ca","cert","key","tls-auth","tls-crypt","tls-crypt-v2").contains(words[0])){
    String path=words[1];if(path.startsWith("/")||path.contains("\\")||Arrays.asList(path.split("/")).contains(".."))throw new IOException("Unsafe certificate reference");String data=files.get(base+path);if(data==null)throw new IOException("Referenced certificate missing from ZIP");out.append('<').append(words[0]).append(">\n").append(data).append("\n</").append(words[0]).append(">\n");if(words[0].equals("tls-auth")&&words.length==3)out.append("key-direction ").append(words[2]).append('\n');
   }else out.append(line).append('\n');
  }return validate(out.toString());
 }
 static String[] tokens(String line)throws IOException{
  List<String> out=new ArrayList<>();StringBuilder token=new StringBuilder();char quote=0;boolean escape=false;
  for(char c:line.toCharArray()){if(escape){token.append(c);escape=false;continue;}if(c=='\\'){escape=true;continue;}if(quote!=0){if(c==quote)quote=0;else token.append(c);continue;}if(c=='\''||c=='"'){quote=c;continue;}if(c=='#'||c==';')break;if(Character.isWhitespace(c)){if(token.length()>0){out.add(token.toString());token.setLength(0);}}else token.append(c);}
  if(quote!=0||escape)throw new IOException("Malformed profile quoting");if(token.length()>0)out.add(token.toString());return out.toArray(new String[0]);
 }
 static String validate(String text)throws IOException{
  if(text.length()>LIMIT||text.indexOf('\0')>=0)throw new IOException("Invalid profile size or content");boolean client=false,remote=false,ca=false,verify=false;String inline=null;StringBuilder out=new StringBuilder();
  for(String line:text.split("\\r?\\n")){
   String t=line.trim();if(inline!=null){if(t.equals("</"+inline+">"))inline=null;out.append(line).append('\n');continue;}
   if(t.matches("<[a-zA-Z0-9-]+>")){inline=t.substring(1,t.length()-1);if(!Arrays.asList("ca","cert","key","tls-auth","tls-crypt","tls-crypt-v2","auth-user-pass").contains(inline))throw new IOException("Unsupported inline section");if(inline.equals("ca"))ca=true;if(inline.equals("auth-user-pass"))throw new IOException("Enter username and password in profile settings");out.append(line).append('\n');continue;}
   String[] w=tokens(t);if(w.length==0)continue;String k=w[0];if(k.startsWith("--"))k=k.substring(2);
   if(Arrays.asList("up","down","route-up","route-pre-down","ipchange","plugin","script-security","management","config","include","cd","chroot","daemon","log","log-append","writepid","status","tls-verify","auth-user-pass-verify","client-connect","client-disconnect","setenv-safe","setenv","ignore-unknown-option","tls-cert-profile","tls-version-max","verify-x509-name-disable").contains(k))throw new IOException("Unsafe or unsupported directive: "+k);
   if(Arrays.asList("ca","cert","key","pkcs12","crl-verify","tls-auth","tls-crypt","tls-crypt-v2","extra-certs").contains(k))throw new IOException("Use embedded certificates or a ZIP with relative certificate files");
   if(k.equals("auth-user-pass")&&w.length!=1)throw new IOException("Enter credentials in profile settings");
   if(k.equals("remote-cert-tls")){if(w.length!=2||!w[1].equals("server"))throw new IOException("Server certificate verification required");verify=true;}
   if(k.equals("tls-version-min")&&(w.length!=2||!Arrays.asList("1.2","1.3").contains(w[1])))throw new IOException("TLS 1.2 or newer required");
   if(k.equals("compress")||k.equals("comp-lzo"))throw new IOException("Compression profiles are unsupported");
   if(k.equals("dev")&&(w.length!=2||!w[1].startsWith("tun")))throw new IOException("Only TUN profiles are supported");
   if(k.equals("client"))client=true;if(k.equals("remote")&&w.length>=2)remote=true;
   out.append(line).append('\n');
  }
  if(inline!=null)throw new IOException("Unclosed inline section");if(!client||!remote||!ca)throw new IOException("Profile needs client, remote and an embedded CA certificate");if(!verify)out.append("remote-cert-tls server\n");return out.toString();
 }
}
