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
  public native short[] decodeFile(String oggfile_pat);
}

