package karuta.hpnpwd.audio;

import android.util.Log;
import java.nio.ShortBuffer;

public class OggVorbisDecoder {
  public static boolean library_loaded = false;
  public int channels = 0;
  public long rate = 0;
  public int bit_depth = 0;
  public int data_length = 0;
  static{
    try{
      System.loadLibrary("stbvorbis");
      library_loaded = true;
    }catch(UnsatisfiedLinkError e){
      Log.e("wasuramoti", "cannot load stbvorbis", e);
    }
  }
  public ShortBuffer decodeFileToShortBuffer(String oggfile_path){
    short[] output = decodeFile(oggfile_path);
    if(output == null){
      return null;
    }else{
      return ShortBuffer.wrap(output);
    }
  }

  // implemented in src/main/jni/wav_ogg_file_codec_jni.c
  public native short[] decodeFile(String oggfile_pat);

  // implemented in src/main/jni/native-audio-jni.c 
  public static native void slesCreateEngine();
  public static native void slesCreateBufferQueueAudioPlayer();
  public static native boolean slesEnqueuePCM(short[] in_data, int data_length);
  public static native boolean slesPlay();
  public static native boolean slesStop();
  public static native void slesShutdown();

}

