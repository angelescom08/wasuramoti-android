#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>
#include <jni.h>

#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#define APP_NAME "wasuramoti"

#define close_all() { \
  if(vin != NULL){stb_vorbis_close(vin);}; \
  if(fout != NULL){ fclose(fout);}; \
  if(buffer != NULL){ free(buffer);}; \
  if(curThread != NULL){(*env)->DeleteLocalRef(env,curThread);}; \
  if(thread != NULL){(*env)->DeleteLocalRef(env,thread);}; \
}

#define abort_task(...) { \
  __android_log_print(ANDROID_LOG_INFO,APP_NAME, __VA_ARGS__); \
  close_all() \
  return(1); \
}

#define fill_zero(x) memset(&x,0,sizeof(x))

// Returns zero for success, non-zero for failure
int decode_file(JNIEnv *env, const char* fin_path, const char * fout_path, stb_vorbis_info * return_info){

  // we have to define all the variable used in abort_task() here
  jclass thread = NULL;
  jmethodID mCurThread = NULL;
  jmethodID mIsInterrupted = NULL;
  jobject curThread = NULL;

  stb_vorbis *vin = NULL;
  FILE *fout = NULL;
  short *buffer = NULL;
  int error = 0;

  if((vin = stb_vorbis_open_filename(fin_path, &error, NULL)) == NULL) {
    abort_task("cannot open read file: %s\n",fin_path)
  }

  if (!(fout = fopen(fout_path,"wb"))){
    abort_task("cannot open write file: %s\n",fout_path);
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

  *return_info = stb_vorbis_get_info(vin);
  int channels = return_info->channels;

  // mostly taken from stb_vorbis_decode_filename()
  int buf_size = channels * 4096;
  buffer = (short *) malloc(buf_size * sizeof(*buffer));
  if (buffer == NULL) {
    abort_task("buffer malloc failed.\n")
  }
  for (;;) {
     int n = stb_vorbis_get_frame_short_interleaved(vin, channels, buffer, buf_size);
     if (n == 0) break;
     // __android_log_print(ANDROID_LOG_INFO,APP_NAME,"Ogg Decode: %d.\n",n);
     fwrite(buffer, sizeof(*buffer), n * channels, fout);
    
     // check whether current thread is interrupted or not
    if(curThread != NULL && mIsInterrupted != NULL){
      jboolean res = (jboolean)(*env)->CallBooleanMethod(env, curThread, mIsInterrupted);
      if(res == JNI_TRUE){
        abort_task("Vorbis decoding interrupted.\n");
      }
    }
  }
  // __android_log_print(ANDROID_LOG_INFO,APP_NAME,"Ogg Decode Done.\n");
  close_all();
  return(0);
}
