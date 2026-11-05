package com.example.systemblock;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** All profile metadata and secrets are encrypted; no keys/passwords in preferences. */
final class VpnProfiles {
 static final class Profile {
  String id=UUID.randomUUID().toString(), name="VPN", protocol,config,user="",password="";
  Profile(String protocol,String config){this.protocol=protocol;this.config=config;}
  JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("protocol",protocol).put("config",config).put("user",user).put("password",password);}
 }
 private final AtomicFile file;
 VpnProfiles(Context c){file=new AtomicFile(new File(c.getNoBackupFilesDir(),"vpn-profiles.aes"));}
 private javax.crypto.SecretKey key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias("adblock-vpn-v1")){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("adblock-vpn-v1",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  return (javax.crypto.SecretKey)ks.getKey("adblock-vpn-v1",null);
 }
 synchronized List<Profile> load()throws Exception{
  if(!file.getBaseFile().exists())return new ArrayList<>();byte[] data=file.readFully();if(data.length<29||data.length>4*1024*1024||data[0]!=1)throw new IOException("Invalid encrypted profile store");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(data,1,13)));cipher.updateAAD("AdBlock VPN profiles v1".getBytes(StandardCharsets.UTF_8));
  JSONArray a=new JSONArray(new String(cipher.doFinal(data,13,data.length-13),StandardCharsets.UTF_8));List<Profile> out=new ArrayList<>();if(a.length()>32)throw new IOException("Too many profiles");
  for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);Profile p=new Profile(j.getString("protocol"),j.getString("config"));p.id=j.getString("id");p.name=j.getString("name");p.user=j.optString("user");p.password=j.optString("password");out.add(p);}return out;
 }
 synchronized void save(List<Profile> profiles)throws Exception{
  if(profiles.size()>32)throw new IOException("At most 32 profiles");JSONArray a=new JSONArray();for(Profile p:profiles)a.put(p.json());byte[] plain=a.toString().getBytes(StandardCharsets.UTF_8);if(plain.length>3*1024*1024)throw new IOException("Profile storage limit reached");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD("AdBlock VPN profiles v1".getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(plain);Arrays.fill(plain,(byte)0);
  FileOutputStream out=file.startWrite();try{out.write(1);out.write(cipher.getIV());out.write(encrypted);file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
 }
 Profile find(String id)throws Exception{for(Profile p:load())if(p.id.equals(id))return p;throw new IOException("Select a saved VPN profile");}
}
