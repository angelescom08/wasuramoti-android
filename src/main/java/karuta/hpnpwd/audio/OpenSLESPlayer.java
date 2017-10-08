package karuta.hpnpwd.audio;

import android.util.Log;

public class OpenSLESPlayer {
  public static boolean library_loaded = false;
  static{
    try{
      System.loadLibrary("wsrmtslesplay");
      library_loaded = true;
    }catch(UnsatisfiedLinkError e){
      Log.e("wasuramoti", "cannot load wsrmtslesplay", e);
    }
  }
  // implemented in src/main/jni/native-audio-jni.c 
  public static native boolean slesCreateEngine();
  public static native boolean slesCreateBufferQueueAudioPlayer(int streamType);
  public static native boolean slesEnqueuePCM(short[] in_data, int data_length);
  public static native boolean slesPlay();
  public static native boolean slesStop();
  public static native void slesShutdown();
  public static native void slesMute(boolean is_mute);
}
