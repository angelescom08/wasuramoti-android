package karuta.hpnpwd.audio;

import android.content.Context;
import android.util.Log;

import com.getkeepsafe.relinker.ReLinker;

public class OpenSLESPlayer {
  public static boolean library_loaded = false;
  
  synchronized public static void loadLibrary(Context context){
    if(library_loaded){
      return;
    }
    try{
      ReLinker.loadLibrary(context, "wsrmtslesplay");
      library_loaded = true;
    }catch(Error e){
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
