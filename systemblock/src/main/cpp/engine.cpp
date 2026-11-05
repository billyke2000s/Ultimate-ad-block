// SPDX-License-Identifier: MIT
#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <sys/socket.h>
#include <unistd.h>
#include <openvpn/common/stop.hpp>
#include <client/ovpncli.hpp>
#include "libubgo.h"

static JavaVM *vm;
struct Env {
 JNIEnv *e=nullptr; bool attached=false;
 Env(){if(vm->GetEnv(reinterpret_cast<void**>(&e),JNI_VERSION_1_6)!=JNI_OK){vm->AttachCurrentThread(&e,nullptr);attached=true;}}
 ~Env(){if(attached)vm->DetachCurrentThread();}
};
static std::string str(JNIEnv*e,jstring v){if(!v)return {};const char*p=e->GetStringUTFChars(v,nullptr);std::string s(p);e->ReleaseStringUTFChars(v,p);return s;}
struct Callbacks {
 jobject obj;
 explicit Callbacks(JNIEnv*e,jobject o):obj(e->NewGlobalRef(o)){}
 virtual ~Callbacks(){Env v;v.e->DeleteGlobalRef(obj);}
 int integer(const char*method,const char*sig,...){Env v;auto c=v.e->GetObjectClass(obj);auto m=v.e->GetMethodID(c,method,sig);va_list args;va_start(args,sig);int r=v.e->CallIntMethodV(obj,m,args);va_end(args);v.e->DeleteLocalRef(c);if(v.e->ExceptionCheck()){v.e->ExceptionClear();return -1;}return r;}
 void event(const std::string&name){Env v;jstring s=v.e->NewStringUTF(name.c_str());integer("onEvent","(Ljava/lang/String;)I",s);v.e->DeleteLocalRef(s);}
 bool text(const char*method,const std::string&s,int n=0){Env v;jstring text=v.e->NewStringUTF(s.c_str());int r=integer(method,"(Ljava/lang/String;I)I",text,n);v.e->DeleteLocalRef(text);return r==1;}
};
static int protect(uintptr_t ctx,int fd){return reinterpret_cast<Callbacks*>(ctx)->integer("protectSocket","(I)I",fd)==1;}
static int filter(uintptr_t ctx,const char*host){Env v;jstring s=v.e->NewStringUTF(host);int r=reinterpret_cast<Callbacks*>(ctx)->integer("filterHost","(Ljava/lang/String;)I",s);v.e->DeleteLocalRef(s);return r;}
static void record(uintptr_t ctx,const char*host,int result){Env v;jstring s=v.e->NewStringUTF(host);reinterpret_cast<Callbacks*>(ctx)->integer("recordHost","(Ljava/lang/String;I)I",s,result);v.e->DeleteLocalRef(s);}
struct Cancellation {openvpn::Stop token;};
class Client final: public Cancellation, public openvpn::ClientAPI::OpenVPNClient,public Callbacks {
public:
 Client(JNIEnv*e,jobject o):Callbacks(e,o){}
 openvpn::Stop* get_async_stop()override{return &token;}
 bool socket_protect(openvpn_io::detail::socket_type s,std::string,bool)override{return protect(reinterpret_cast<uintptr_t>(thisCallbacks()),s);}
 Callbacks* thisCallbacks(){return static_cast<Callbacks*>(this);}
 bool pause_on_connection_timeout()override{return false;}
 void event(const openvpn::ClientAPI::Event&e)override{if(e.name=="CONNECTED"){auto info=connection_info();text("setServer",info.serverIp+":"+info.serverPort);}Callbacks::event(e.error?"ERROR:"+e.name:e.name);}
 void log(const openvpn::ClientAPI::LogInfo&)override{} // Never log profiles, credentials or server messages.
 void acc_event(const openvpn::ClientAPI::AppCustomControlMessageEvent&)override{}
 void external_pki_cert_request(openvpn::ClientAPI::ExternalPKICertRequest&r)override{r.error=true;r.errorText="External PKI is unsupported";}
 void external_pki_sign_request(openvpn::ClientAPI::ExternalPKISignRequest&r)override{r.error=true;r.errorText="External PKI is unsupported";}
 bool tun_builder_new()override{return integer("newBuilder","()I")==1;}
 bool tun_builder_set_layer(int layer)override{return layer==3;}
 bool tun_builder_set_remote_address(const std::string&,bool)override{return true;}
 bool tun_builder_add_address(const std::string&a,int prefix,const std::string&,bool,bool)override{return text("addAddress",a,prefix);}
 bool tun_builder_add_route(const std::string&a,int prefix,int,bool)override{return text("addRoute",a,prefix);}
 bool tun_builder_reroute_gw(bool v4,bool v6,unsigned int)override{return (!v4||text("addRoute","0.0.0.0",0))&&(!v6||text("addRoute","::",0));}
 bool tun_builder_exclude_route(const std::string&,int,int,bool)override{return false;} // Core route emulation converts excludes on Android.
 bool tun_builder_set_dns_options(const openvpn::DnsOptions&dns)override{
  for(const auto&[priority,server]:dns.servers){
   if(server.transport!=openvpn::DnsServer::Transport::Unset&&server.transport!=openvpn::DnsServer::Transport::Plain)return false;
   if(!server.domains.empty())return false; // Do not flatten split DNS silently.
   for(const auto&a:server.addresses){if(a.port!=0&&a.port!=53)return false;if(!text("addDns",a.address))return false;}
  }
  for(const auto&d:dns.search_domains)if(!text("addSearch",d.domain))return false;
  return true;
 }
 bool tun_builder_set_mtu(int mtu)override{return integer("setMtu","(I)I",mtu)==1;}
 bool tun_builder_set_session_name(const std::string&)override{return true;}
 bool tun_builder_set_allow_family(int,bool)override{return true;} // Android builder blocks unused families.
 bool tun_builder_set_allow_local_dns(bool allow)override{return !allow;}
 int tun_builder_establish()override{return integer("establish","()I");}
 bool tun_builder_persist()override{return false;}
 void tun_builder_teardown(bool)override{} // Android keeps routes fail-closed through reconnect.
};
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*v,void*){vm=v;return JNI_VERSION_1_6;}
#define JNI(name) extern "C" JNIEXPORT
JNI(create) jlong JNICALL Java_com_example_systemblock_NativeSession_create(JNIEnv*e,jobject o){return reinterpret_cast<jlong>(new Callbacks(e,o));}
JNI(destroy) void JNICALL Java_com_example_systemblock_NativeSession_destroy(JNIEnv*,jobject,jlong p){delete reinterpret_cast<Callbacks*>(p);}
JNI(wgStart) jlong JNICALL Java_com_example_systemblock_NativeSession_wgStart(JNIEnv*e,jobject,jlong ctx,jint fd,jint mtu,jstring conf){auto s=str(e,conf);return UBWgStart(fd,mtu,const_cast<char*>(s.c_str()),reinterpret_cast<uintptr_t>(protect),ctx);}
JNI(wgStop) void JNICALL Java_com_example_systemblock_NativeSession_wgStop(JNIEnv*,jobject,jlong id){UBWgStop(id);}
JNI(wgRebind) jboolean JNICALL Java_com_example_systemblock_NativeSession_wgRebind(JNIEnv*,jobject,jlong id){return UBWgRebind(id)!=0;}
JNI(wgConfig) jstring JNICALL Java_com_example_systemblock_NativeSession_wgConfig(JNIEnv*e,jobject,jlong id){char*s=UBWgConfig(id);jstring out=e->NewStringUTF(s);free(s);return out;}
JNI(bridgeStart) jlong JNICALL Java_com_example_systemblock_NativeSession_bridgeStart(JNIEnv*e,jobject,jlong ctx,jint tun,jint engine,jint mtu,jstring ips,jstring dns){auto a=str(e,ips),d=str(e,dns);return UBBridgeStart(tun,engine,mtu,const_cast<char*>(a.c_str()),const_cast<char*>(d.c_str()),reinterpret_cast<uintptr_t>(filter),reinterpret_cast<uintptr_t>(record),ctx);}
JNI(bridgeStop) void JNICALL Java_com_example_systemblock_NativeSession_bridgeStop(JNIEnv*,jobject,jlong id){UBBridgeStop(id);}
JNI(bridgeBytes) jlong JNICALL Java_com_example_systemblock_NativeSession_bridgeBytes(JNIEnv*,jobject,jlong id,jboolean rx){return UBBridgeBytes(id,rx);}
JNI(openCreate) jlong JNICALL Java_com_example_systemblock_NativeSession_openCreate(JNIEnv*e,jobject o){return reinterpret_cast<jlong>(new Client(e,o));}
JNI(openRun) jstring JNICALL Java_com_example_systemblock_NativeSession_openRun(JNIEnv*e,jobject,jlong ptr,jstring profile,jstring user,jstring password,jstring keyPassword){
 auto*c=reinterpret_cast<Client*>(ptr);try{
  openvpn::ClientAPI::Config config;config.content=str(e,profile);config.disableClientCert=config.content.find("<cert>")==std::string::npos;config.privateKeyPassword=str(e,keyPassword);config.guiVersion="AdBlock 0.6 experimental";config.connTimeout=60;config.tunPersist=false;config.googleDnsFallback=false;config.allowUnusedAddrFamilies="no";config.compressionMode="no";config.tlsVersionMinOverride="tls_1_2";config.tlsCertProfileOverride="preferred";config.enableRouteEmulation=true;
  auto eval=c->eval_config(config);if(eval.error)return e->NewStringUTF("Profile rejected by OpenVPN core");
  if(eval.externalPki)return e->NewStringUTF("External PKI profiles are unsupported");
  openvpn::ClientAPI::ProvideCreds creds;creds.username=str(e,user);creds.password=str(e,password);auto auth=c->provide_creds(creds);if(auth.error)return e->NewStringUTF("Credentials rejected by OpenVPN core");
  auto result=c->connect();return e->NewStringUTF(result.error?result.status.c_str():"");
 }catch(const std::exception&){return e->NewStringUTF("OpenVPN engine error");}
}
JNI(openStop) void JNICALL Java_com_example_systemblock_NativeSession_openStop(JNIEnv*,jobject,jlong ptr){reinterpret_cast<Client*>(ptr)->token.stop();}
JNI(openReconnect) void JNICALL Java_com_example_systemblock_NativeSession_openReconnect(JNIEnv*,jobject,jlong ptr){reinterpret_cast<Client*>(ptr)->reconnect(0);}
JNI(openDestroy) void JNICALL Java_com_example_systemblock_NativeSession_openDestroy(JNIEnv*,jobject,jlong ptr){delete reinterpret_cast<Client*>(ptr);}

JNI(bridgeAlive) jboolean JNICALL Java_com_example_systemblock_NativeSession_bridgeAlive(JNIEnv*,jobject,jlong id){return UBBridgeAlive(id)!=0;}

JNI(socketPair) jintArray JNICALL Java_com_example_systemblock_NativeSession_socketPair(JNIEnv*e,jobject){int fds[2];if(socketpair(AF_UNIX,SOCK_DGRAM|SOCK_CLOEXEC,0,fds)!=0)return e->NewIntArray(0);jintArray out=e->NewIntArray(2);e->SetIntArrayRegion(out,0,2,fds);return out;}
