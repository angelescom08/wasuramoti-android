package karuta.hpnpwd.audio;

import android.util.Log;
import android.content.Context;
import android.content.res.AssetManager;

import com.getkeepsafe.relinker.ReLinker;

import java.nio.ShortBuffer;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public class OggVorbisDecoder {
  public static boolean library_loaded = false;
  public static Error unsatisfied_link_error = null;
  public int channels = 0;
  public long rate = 0;
  public int bit_depth = 0;
  public int data_length = 0;

  synchronized public static void loadLibrary(Context context){
    if(library_loaded){
      return;
    }
    try{
      ReLinker.loadLibrary(context, "wsrmtvorbis");
      if(android.os.Build.VERSION.SDK_INT >= 9){
        library_loaded = initDynAsset();
      }else{
        library_loaded = true;
      }
    }catch(Error e){
      unsatisfied_link_error = e;
      Log.e("wasuramoti", "cannot load wsrmtvorbis", e);
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

  public static String reportApi(Context context, String asset_path){
    ByteArrayOutputStream bao = new ByteArrayOutputStream();
    AssetManager mgr = context.getAssets();
    testApi(bao, mgr, asset_path);
    return bao.toString();
  }

  // implemented in src/main/jni/wav_ogg_file_codec_jni.c
  public native short[] decodeFile(String file_path);
  public native short[] decodeAsset(AssetManager asset_manager, String asset_path);
  public static native boolean initDynAsset();
  public static native void testApi(OutputStream stream,AssetManager asset_manager, String asset_path);
}
