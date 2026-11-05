package com.example.systemblock;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
public class OpenProfileTest {
 private static final String BASE="client\ndev tun\nremote vpn.example 1194 udp\n<ca>\nTEST CA DATA\n</ca>\n";
 @Test public void enforcesServerRole()throws Exception{assertTrue(OpenProfile.validate(BASE).endsWith("remote-cert-tls server\n"));}
 @Test public void preservesEmbeddedMaterials()throws Exception{String s=BASE+"<cert>\ncertificate\n</cert>\n<key>\nkey\n</key>\nauth-user-pass\n";assertTrue(OpenProfile.validate(s).contains("<key>\nkey\n</key>"));}
 @Test public void rejectsClientRole()throws Exception{reject(BASE+"remote-cert-tls client\n");}
 @Test public void rejectsLegacyTLS()throws Exception{reject(BASE+"tls-version-min 1.0\n");}
 @Test public void rejectsMissingCA()throws Exception{reject("client\nremote vpn.example\n");}
 @Test public void rejectsUnclosedInline()throws Exception{reject(BASE+"<key>\nsecret");}
 @Test public void rejectsScriptsAndIncludes()throws Exception{for(String d:new String[]{"up","down","plugin","config","include","management","script-security","tls-verify","ignore-unknown-option","setenv"})reject(BASE+d+" anything\n");}
 @Test public void rejectsExternalAbsoluteFiles()throws Exception{reject(BASE+"key /sdcard/key.pem\n");}
 @Test public void rejectsPasswordFiles()throws Exception{reject(BASE+"auth-user-pass credentials.txt\n");}
 @Test public void rejectsCompressionAndTap()throws Exception{reject(BASE+"comp-lzo yes\n");reject(BASE+"dev tap\n");}
 @Test public void quoteParsing()throws Exception{assertArrayEquals(new String[]{"remote","vpn.example","1194"},OpenProfile.tokens("remote 'vpn.example' 1194 # ignored"));}
 @Test public void boundsReads()throws Exception{try{OpenProfile.read(new ByteArrayInputStream(new byte[33]),32);fail();}catch(IOException expected){}}
 @Test public void zipResolvesRelativeCertificates()throws Exception{byte[] zip=zip(new String[]{"vpn/profile.ovpn","client\nremote vpn.example\nca \"certs/ca.pem\"\n","vpn/certs/ca.pem","certificate"});assertTrue(OpenProfile.fromZip(new ByteArrayInputStream(zip)).contains("<ca>\ncertificate\n</ca>"));}
 @Test public void zipRejectsTraversal()throws Exception{try{OpenProfile.fromZip(new ByteArrayInputStream(zip(new String[]{"../key","secret"})));fail();}catch(IOException expected){}}
 @Test public void zipRejectsMissingReferences()throws Exception{try{OpenProfile.fromZip(new ByteArrayInputStream(zip(new String[]{"p.ovpn","client\nremote example\nca missing\n"})));fail();}catch(IOException expected){}}
 @Test public void zipRejectsMultipleProfiles()throws Exception{try{OpenProfile.fromZip(new ByteArrayInputStream(zip(new String[]{"a.ovpn",BASE,"b.ovpn",BASE})));fail();}catch(IOException expected){}}
 private static void reject(String s)throws Exception{try{OpenProfile.validate(s);fail("accepted unsafe config");}catch(IOException expected){}}
 private static byte[] zip(String[] values)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(int i=0;i<values.length;i+=2){zip.putNextEntry(new ZipEntry(values[i]));zip.write(values[i+1].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return out.toByteArray();}
}
