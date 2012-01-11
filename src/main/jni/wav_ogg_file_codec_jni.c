#include <string.h>
#include <jni.h>

void
Java_mita_nep_audio_OggVorbisDecoder_decodeFile( JNIEnv* env,
                                                  jobject thiz, jstring fin_path, jstring fout_path)
{
  //Get the native string from javaString
  const char *native_fin_path = (*env)->GetStringUTFChars(env, fin_path, 0);
  const char *native_fout_path = (*env)->GetStringUTFChars(env, fout_path, 0);

  if(!decode_file(native_fin_path, native_fout_path)){
    //TODO: errror handling
  }
  //Do something with the nativeString

  //DON'T FORGET THIS LINE!!!
  (*env)->ReleaseStringUTFChars(env, fin_path, native_fin_path);
  (*env)->ReleaseStringUTFChars(env, fout_path, native_fout_path);	 

} 
