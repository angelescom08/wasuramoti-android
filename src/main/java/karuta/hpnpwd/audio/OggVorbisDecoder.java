package karuta.hpnpwd.audio;

import android.util.Log;
import android.content.Context;
import android.content.res.AssetManager;
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

  public ShortBuffer decodeFileToShortBuffer(String file_path){
    return wrap(decodeFile(file_path));
  }

  public ShortBuffer decodeAssetToShortBuffer(Context context, String asset_path){
    AssetManager mgr = context.getAssets();
    return wrap(decodeAsset(mgr, asset_path));
  }

  private ShortBuffer wrap(short[] s){
    return (s == null) ? null : ShortBuffer.wrap(s);
  }

  // implemented in src/main/jni/wav_ogg_file_codec_jni.c
  public native short[] decodeFile(String file_path);
  public native short[] decodeAsset(AssetManager asset_manager, String asset_path);
}
