package mita.nep.audio;

public class OggVorbisDecoder {
  public int channels;
  public long rate;
  public int max_amplitude;
  static{
    System.loadLibrary("vorbis");
  }
  public void decode(String a,String b){
    decodeFile(a,b);
  }
  public native void decodeFile(String a,String b);
}

