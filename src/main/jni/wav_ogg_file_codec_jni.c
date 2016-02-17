#include <stdlib.h>
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#include "dynamic_asset.h"

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

jboolean Java_karuta_hpnpwd_audio_OggVorbisDecoder_initDynAsset(
    JNIEnv* env, jclass thiz
    ){
  // since AAsset_* can be only used in Android >= 2.3
  // we have to dynamically load the shared library.
  // if you don't need to support Android < 2.3, just add LOCAL_LDLIBS := -landroid to Android.mk and remove this function.
  if(dynAssetHandle != NULL){
    return JNI_TRUE;
  }
  if(NULL==(dynAssetHandle = dlopen("libandroid.so",RTLD_NOW))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlopen('libandroid.so') failed");
    return JNI_FALSE;
  }
  if(NULL==(DynAssetManager_fromJava = dlsym(dynAssetHandle,"AAssetManager_fromJava"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAssetManager_fromJava') failed");
    return JNI_FALSE;
  }
  if(NULL==(DynAssetManager_open = dlsym(dynAssetHandle,"AAssetManager_open"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAssetManager_open') failed");
    return JNI_FALSE;
  };
  if(NULL==(DynAsset_close = dlsym(dynAssetHandle,"AAsset_close"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAsset_close') failed");
    return JNI_FALSE;
  };
  if(NULL==(DynAsset_getLength = dlsym(dynAssetHandle,"AAsset_getLength"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAsset_getLength') failed");
    return JNI_FALSE;
  };
  if(NULL==(DynAsset_getRemainingLength = dlsym(dynAssetHandle,"AAsset_getRemainingLength"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAsset_getRemainingLength') failed");
    return JNI_FALSE;
  };
  if(NULL==(DynAsset_read = dlsym(dynAssetHandle,"AAsset_read"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAsset_read') failed");
    return JNI_FALSE;
  };
  if(NULL==(DynAsset_seek = dlsym(dynAssetHandle,"AAsset_seek"))){
    __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlsym('AAsset_seek') failed");
    return JNI_FALSE;
  };
  __android_log_print(ANDROID_LOG_INFO,"wasuramoti","dlopen and all dlsym success");
  return JNI_TRUE;
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
  if(DynAssetManager_fromJava == NULL){
    // we won't try to dlopen & dlsym here since it should already be tried in initDynAsset().
    return NULL;
  }
  AAssetManager *mgr = (AAssetManager *)DynAssetManager_fromJava(env, asset_manager);
  return decodeCommon(env, thiz, mgr, fin_path);
} 

void Java_karuta_hpnpwd_audio_OggVorbisDecoder_testApi(JNIEnv* env, jclass thiz,
    jobject output_stream, jobject asset_manager, jstring fin_path
    ){
      if(DynAssetManager_fromJava == NULL){
        return;
      }
      const char *native_fin_path = (*env)->GetStringUTFChars(env, fin_path, 0);
      AAssetManager *mgr = (AAssetManager *)DynAssetManager_fromJava(env, asset_manager);
      testApi(env, output_stream, mgr, native_fin_path);
      (*env)->ReleaseStringUTFChars(env, fin_path, native_fin_path);
}


void * dynAssetHandle = NULL;
AAssetManager* (*DynAssetManager_fromJava)(JNIEnv* env, jobject assetManager);
AAsset* (*DynAssetManager_open)(AAssetManager* mgr, const char* filename, int mode);
void (*DynAsset_close)(AAsset* asset);
off_t (*DynAsset_getLength)(AAsset* asset);
off_t (*DynAsset_getRemainingLength)(AAsset* asset);
int (*DynAsset_read)(AAsset* asset, void* buf, size_t count);
off_t (*DynAsset_seek)(AAsset* asset, off_t offset, int whence);
