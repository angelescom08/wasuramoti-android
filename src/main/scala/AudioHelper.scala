package tami.pen.wasuramoti

import _root_.mita.nep.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.Context
import _root_.java.io.{EOFException,File,FileInputStream,DataInputStream}
import _root_.java.nio.{ByteBuffer,ByteOrder}
import _root_.java.util.{Timer,TimerTask}
import scala.collection.mutable

object AudioHelper{
  def makeKarutaPlayer(context:Context,reader:Reader):Option[KarutaPlayer] = {
    val current_index = FudaListHelper.getCurrentIndex(context)
    FudaListHelper.queryNext(context,current_index).map(
      {case (cur_num,next_num,_,_) => new KarutaPlayer(context,reader,cur_num,next_num)}
    )
  }
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
    new AudioTrack( AudioManager.STREAM_MUSIC,
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
  def makeSilence(sec:Double,decoder:OggVorbisDecoder):WavBuffer = {
    val buf = new Array[Short]((sec*decoder.rate).toInt)
    new WavBuffer(buf,decoder)
  }
}
//TODO:handle stereo audio
class WavBuffer(buffer:Array[Short],decoder:OggVorbisDecoder){
  var index_begin = 0
  var index_end = buffer.length
  // in milliseconds
  def audioLength():Long = {
    (1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong
  }
  def bufferSize():Int = {
    (java.lang.Short.SIZE/java.lang.Byte.SIZE) * (index_end - index_begin)
  }
  def getBuffer():Array[Short] = {
    buffer.slice(index_begin,index_end)
  }
  def writeToAudioTrack():AudioTrack = {
    val track = AudioHelper.makeAudioTrack(decoder,bufferSize()) 
    track.write(buffer,index_begin,index_end-index_begin)
    return(track)
  }
}

class KarutaPlayer(context:Context,reader:Reader,simo_num:Int,kami_num:Int){
  var thread = None:Option[Thread]
  startDecode()
  var wav_buffer = None:Option[WavBuffer]
  var simo_millsec = None:Option[Long]
  var is_playing = false
  def play(onSimoEnd:Unit=>Unit=identity[Unit],onKamiEnd:Unit=>Unit=identity[Unit]){
    if(is_playing){
      return
    }
    is_playing = true
    waitDecode()
    val track = wav_buffer.get.writeToAudioTrack()
    new Timer().schedule(new TimerTask(){override def run(){onSimoEnd()}},simo_millsec.get)
    new Timer().schedule(new TimerTask(){override def run(){onKamiEnd();is_playing=false}},wav_buffer.get.audioLength)
    track.play()
  }
  def startDecode(){
    val t = new Thread(new Runnable(){
      override def run(){
        var buf = new mutable.ArrayBuffer[Short]()
        var g_decoder:Option[OggVorbisDecoder] = None
        reader.withDecodedFile(simo_num,2,(wav_file,decoder) => {
           g_decoder = Some(decoder)
           val w = new WavBuffer(AudioHelper.readShortsFromFile(wav_file),decoder)
           buf ++= w.getBuffer
           simo_millsec = Some(w.audioLength)
        })
        reader.withDecodedFile(kami_num,1,(wav_file,decoder) => {
           val w = new WavBuffer(AudioHelper.readShortsFromFile(wav_file),decoder)
           buf ++= w.getBuffer
        })
        wav_buffer = Some(new WavBuffer(buf.toArray,g_decoder.get))
    }
    })
    thread = Some(t)
    t.start
  }
  def waitDecode(){
    if(!thread.isEmpty){
      thread.get.join
    }
  }
}
