package mita.nep.audio;

public class OggVorbisDecoder {

  static{
    System.loadLibrary("vorbis");
  }
  public void decode(String a,String b){
    decodeFile(a,b);
  }
  public native void decodeFile(String a,String b);
}

