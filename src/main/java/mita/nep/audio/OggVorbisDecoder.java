package mita.nep.audio;

public class OggVorbisDecoder {
  public int channels = 0;
  public long rate = 0;
  public int bit_depth = 0;
  static{
    System.loadLibrary("vorbis");
  }
  public void decode(String a,String b){
    decodeFile(a,b);
  }
  public native void decodeFile(String a,String b);
}

