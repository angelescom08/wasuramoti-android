package karuta.hpnpwd.audio;

public class OggVorbisDecoder {
  public static boolean library_loaded = false;
  public int channels = 0;
  public long rate = 0;
  public int bit_depth = 0;
  static{
    try{
      System.loadLibrary("vorbis");
      library_loaded = true;
    }catch(UnsatisfiedLinkError e){

    }
  }
  public boolean decode(String a,String b){
    return (decodeFile(a,b) == 1);
  }
  public native int decodeFile(String a,String b);
}

