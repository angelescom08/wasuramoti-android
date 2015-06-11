/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/* This is a JNI example where we use native methods to play sounds
 * using OpenSL ES. See the corresponding Java source file located at:
 *
 *   src/com/example/nativeaudio/NativeAudio/NativeAudio.java
 */

#include <assert.h>
#include <jni.h>
#include <string.h>

// for __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");
#include <android/log.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

const char * APP_NAME = "wasuramoti";

#define abort_task(...) { \
  __android_log_print(ANDROID_LOG_INFO,APP_NAME, __VA_ARGS__);\
  return JNI_FALSE;\
}

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay = NULL;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue = NULL;
static SLVolumeItf bqPlayerVolume;

// pointer and size of the next player buffer to enqueue, and number of remaining buffers
static short *nextBuffer = NULL;
static unsigned nextSize;

void destroyPlayer(){
  // destroy buffer queue audio player object, and invalidate all associated interfaces
  if (bqPlayerObject != NULL) {
      (*bqPlayerObject)->Destroy(bqPlayerObject);
      bqPlayerObject = NULL;
      bqPlayerPlay = NULL;
      bqPlayerBufferQueue = NULL;
  }
}


// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);
    if(nextBuffer != NULL){
      free(nextBuffer);
      nextBuffer = NULL;
    }
}

// create the engine and output mix objects
jboolean Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesCreateEngine(JNIEnv* env, jclass clazz)
{
    if(engineObject != NULL){
      return JNI_TRUE;
    }
    SLresult result;

    // create engine
    if(SL_RESULT_SUCCESS != slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL)){
      abort_task("create engine failed");
    }
    // realize the engine
    if(SL_RESULT_SUCCESS != (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE)){
      abort_task("realize engine failed");
    }
    // get the engine interface, which is needed in order to create other objects
    if(SL_RESULT_SUCCESS != (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine)){
      abort_task("getting engine interface failed");
    }

    // create output mix
    if(SL_RESULT_SUCCESS != (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL)){
      abort_task("create output mix failed");
    }

    // realize the output mix
    if(SL_RESULT_SUCCESS != (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE)){
      abort_task("realize output mix failed");
    }
    return JNI_TRUE;
}


// create buffer queue audio player
jboolean Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesCreateBufferQueueAudioPlayer(JNIEnv* env,
        jclass clazz,
        jint _streamType
        )
{
    destroyPlayer();  

    // configure audio source
    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};
    SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_22_05,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};
    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    // create audio player
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_ANDROIDCONFIGURATION};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    if(SL_RESULT_SUCCESS != (*engineEngine)->CreateAudioPlayer(
          engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, sizeof(ids)/sizeof(*ids), ids, req)
      ){
      abort_task("create audio player failed");
    }

    // set stream type
    SLAndroidConfigurationItf playerConfig;
    if(SL_RESULT_SUCCESS != (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_ANDROIDCONFIGURATION, &playerConfig)){
      abort_task("getting configuration interface failed");
    }
    SLint32 streamType = _streamType; // jint is compatible with SLint32, android.media.AudioManager.STREAM_* has same value with SL_ANDROID_STREAM_* defined in OpenSLES_Android.sh
    if(SL_RESULT_SUCCESS != (*playerConfig)->SetConfiguration(playerConfig, SL_ANDROID_KEY_STREAM_TYPE, &streamType, sizeof(SLint32))){
      abort_task("setting stream type failed");
    }

    // realize the player
    if(SL_RESULT_SUCCESS != (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE)){
      abort_task("realize player failed");
    }

    // get the play interface
    if(SL_RESULT_SUCCESS != (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay)){
      abort_task("getting player interface failed");
    }

    // get the buffer queue interface
    if(SL_RESULT_SUCCESS != (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE, &bqPlayerBufferQueue)){
      abort_task("buffer queue interface failed");
    }

    // register callback on the buffer queue
    if(SL_RESULT_SUCCESS != (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL)){
      abort_task("register callback failed");
    }
    // get the volume interface
    if(SL_RESULT_SUCCESS != (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume)){
      abort_task("getting volume interface failed");
    }
    return JNI_TRUE;
}

void stopAndClear(){
  if(bqPlayerPlay != NULL){
    (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
  }
  if(bqPlayerBufferQueue != NULL){
    (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);
  }
  if(nextBuffer != NULL){
    free(nextBuffer);
    nextBuffer = NULL;
  }
}

// enqueue PCM data
jboolean Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesEnqueuePCM(JNIEnv* env, jclass clazz, jshortArray in_data, jint data_length)
{
    if(data_length == 0){
      return JNI_TRUE;
    }
    stopAndClear();
    nextSize = data_length * sizeof(short);
    nextBuffer = malloc(nextSize);
    (*env)->GetShortArrayRegion(env, in_data, 0, data_length, nextBuffer);
    SLresult result;
    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer, nextSize);
    if (SL_RESULT_SUCCESS != result) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

jboolean Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesPlay(JNIEnv* env, jclass clazz)
{
    SLresult result;
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    if(SL_RESULT_SUCCESS == result){
      return JNI_TRUE;
    }else{
      return JNI_FALSE;
    }
  
}
jboolean Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesStop(JNIEnv* env, jclass clazz)
{
  stopAndClear();
}


// shut down the native audio system
void Java_karuta_hpnpwd_audio_OpenSLESPlayer_slesShutdown(JNIEnv* env, jclass clazz)
{

    destroyPlayer();

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }

    if(nextBuffer != NULL){
      free(nextBuffer);
      nextBuffer = NULL;
    }

}
