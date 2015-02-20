#include <stdio.h>
#include <android/log.h>
#include <jni.h>
#include "wav_ogg_file_codec_jni.h"

#define BIT_DEPTH 16
#define MAX_AMPLITUDE ((1 << (BIT_DEPTH-1)) - 1)
#define APP_NAME "wasuramoti"

#define abort_task(...) { \
    __android_log_print(ANDROID_LOG_INFO,APP_NAME, __VA_ARGS__); \
    if(fin != NULL){ fclose(fin);}; \
    if(fout != NULL){ fclose(fout);}; \
    if(curThread != NULL){(*env)->DeleteLocalRef(env,curThread);}; \
    if(thread != NULL){(*env)->DeleteLocalRef(env,thread);}; \
    return(1); \
  \
}

#define fill_zero(x) memset(&x,0,sizeof(x))

// Returns zero for success, non-zero for failure
int decode_file(JNIEnv *env, const char* fin_path, const char * fout_path, struct wav_ogg_file_codec_info * return_info){

  jclass thread = NULL;
  jmethodID mCurThread = NULL;
  jmethodID mIsInterrupted = NULL;
  jobject curThread = NULL;

  FILE *fin = NULL;
  FILE *fout = NULL;
  if (!(fin = fopen(fin_path, "rb"))){
    abort_task("cannot read file: %s\n",fin_path);
  }

  if (!(fout = fopen(fout_path,"wb"))){
    abort_task("cannot write file: %s\n",fout_path);
  }

  /* calling AsyncTask.cancel(true) in java code will set isInterrupted() == true */

  thread = (*env)->FindClass(env, "java/lang/Thread");
  if(thread != NULL){
    mCurThread = (*env)->GetStaticMethodID(env, thread, "currentThread", "()Ljava/lang/Thread;");
    mIsInterrupted = (*env)->GetMethodID(env, thread, "isInterrupted", "()Z");
    if(mCurThread != NULL){
      curThread = (jobject)(*env)->CallStaticObjectMethod(env, thread, mCurThread);
    }
  }

  while(1){
    if(curThread != NULL && mIsInterrupted != NULL){
      jboolean res = (jboolean)(*env)->CallBooleanMethod(env, curThread, mIsInterrupted);
      if(res == JNI_TRUE){
        abort_task("Vorbis decoding interrupted.\n");
      }
    }
    // TODO: decode ogg
  }
  if(curThread != NULL){(*env)->DeleteLocalRef(env,curThread);};
  if(thread != NULL){(*env)->DeleteLocalRef(env,thread);};
  /*__android_log_print(ANDROID_LOG_INFO,APP_NAME,"Ogg Decode Done.\n"); */
  return(0);
}
