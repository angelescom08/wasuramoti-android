package tami.pen.wasuramoti

import _root_.mita.nep.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.java.io.{EOFException,File,FileInputStream,DataInputStream}
import _root_.java.nio.{ByteBuffer,ByteOrder}

object AudioHelper{
  def makeAudioTrack(decoder:OggVorbisDecoder,buffer_size:Int):AudioTrack ={
    val audio_format = if(decoder.bit_depth == 16){
      AudioFormat.ENCODING_PCM_16BIT
    }else{
      //TODO:set appropriate value
      AudioFormat.ENCODING_PCM_8BIT
    }
    val channels = if(decoder.channels == 1){
      AudioFormat.CHANNEL_CONFIGURATION_MONO
    }else{
      AudioFormat.CHANNEL_CONFIGURATION_STEREO
    }
    new AudioTrack( AudioManager.STREAM_VOICE_CALL,
      decoder.rate.toInt,
      channels,
      audio_format,
      buffer_size,
      AudioTrack.MODE_STATIC )
  }
  def readShortsFromFile(f:File):Array[Short] = {
    val bytes = new Array[Byte](f.length.toInt)
    val fin = new FileInputStream(f)
    fin.read(bytes)
    fin.close()
    // convert byte array to short array
    val shorts = new Array[Short](f.length.toInt/2)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
    return(shorts)
  }
}
//TODO:handle stereo audio
class WavBuffer(buffer:Array[Short],decoder:OggVorbisDecoder){
  var index_begin = 0
  var index_end = buffer.length
  def writeToAudioTrack(track:AudioTrack){ 
    var offset = index_begin
    var len = 0
    do{
     len = track.write(buffer,offset,index_end-offset)
     offset += len
    }while( len > 0 && offset < index_end)
  }
}
