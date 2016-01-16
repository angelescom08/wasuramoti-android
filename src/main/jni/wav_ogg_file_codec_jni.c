#include <stdlib.h>
#include <jni.h>
#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

// Returns zero for success, non-zero for failure
static jshortArray decodeCommon(JNIEnv* env, jobject thiz, AAssetManager *mgr, jstring fin_path){
  //Get the native string from javaString
  const char *native_fin_path = (*env)->GetStringUTFChars(env, fin_path, 0);
  short * native_out_data;
  int out_data_len;
  stb_vorbis_info wi;
  int r = decode_asset_or_file(env, mgr, native_fin_path, &native_out_data, &out_data_len, &wi);
  //Don't forget to release strings
  (*env)->ReleaseStringUTFChars(env, fin_path, native_fin_path);
  if(r == 0){
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "channels","I");
    (*env)->SetIntField(env, thiz, fid, wi.channels);
    fid = (*env)->GetFieldID(env, cls, "rate","J");
    (*env)->SetLongField(env, thiz, fid, wi.sample_rate);
    fid = (*env)->GetFieldID(env, cls, "bit_depth","I");
    (*env)->SetIntField(env, thiz, fid, 16);
    fid = (*env)->GetFieldID(env, cls, "data_length","I");
    (*env)->SetIntField(env, thiz, fid, out_data_len);
    (*env)->DeleteLocalRef(env,cls);

    jshortArray out_data = (*env)->NewShortArray(env, out_data_len);
    (*env)->SetShortArrayRegion(env, out_data, 0, out_data_len, native_out_data);
    free(native_out_data);
    return out_data;
  }else{
    return NULL;
  }
}

jshortArray
Java_karuta_hpnpwd_audio_OggVorbisDecoder_decodeFile(
  JNIEnv* env, jobject thiz, jstring fin_path
){
  return decodeCommon(env, thiz, NULL, fin_path);
} 

jshortArray
Java_karuta_hpnpwd_audio_OggVorbisDecoder_decodeAsset(
  JNIEnv* env, jobject thiz, jobject asset_manager, jstring fin_path
){
  AAssetManager *mgr = (AAssetManager *)AAssetManager_fromJava(env, asset_manager);
  return decodeCommon(env, thiz, mgr, fin_path);
} 
