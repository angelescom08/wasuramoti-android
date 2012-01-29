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
    if("RANDOM" == Globals.prefs.get.getString("read_order",null)){
      val cur_num = Globals.player match {
        case Some(player) => player.kami_num
        case None => 0
      }
      val next_num = FudaListHelper.queryRandom(context)
      Some(new KarutaPlayer(context,reader,cur_num,next_num))
    }else{
      FudaListHelper.queryNext(context,current_index).map(
        {case (cur_num,next_num,_,_) => new KarutaPlayer(context,reader,cur_num,next_num)}
      )
    }
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

class KarutaPlayer(context:Context,val reader:Reader,simo_num:Int,var kami_num:Int){
  var thread = None:Option[Thread]
  startDecode()
  var wav_buffer = None:Option[WavBuffer]
  var simo_millsec = None:Option[Long]
  var track = None:Option[AudioTrack]
  var timer_kamiend = None:Option[Timer]
  var timer_simoend = None:Option[Timer]
  var is_playing = false
  var is_decoding = false
  var is_kaminoku = false
  def play(onSimoEnd:Unit=>Unit=identity[Unit],onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      if(is_playing){
        return
      }
      is_playing = true
      waitDecode()
      track = Some(wav_buffer.get.writeToAudioTrack())
      timer_simoend = Some(new Timer())
      timer_kamiend = Some(new Timer())
      timer_simoend.get.schedule(new TimerTask(){
          override def run(){
            onSimoEnd()
            is_kaminoku=true
          }},simo_millsec.get)
      timer_kamiend.get.schedule(new TimerTask(){
          override def run(){
            onKamiEnd()
            is_playing=false
          }},wav_buffer.get.audioLength)
      track.get.play()
    }
  }
  def stop(){
    timer_simoend.get.cancel()
    timer_kamiend.get.cancel()
    timer_simoend = None
    timer_kamiend = None
    track.get.flush()
    track.get.stop()
    track.get.release()
    track = None
    is_playing = false
  }
  def startDecode(){ 
    Globals.global_lock.synchronized{
      if(is_decoding){
        return
      }
      is_decoding = true
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
          is_decoding = false
      }
      })
      thread = Some(t)
      t.start
    }
  }
  def waitDecode(){
    if(!thread.isEmpty){
      thread.get.join
    }
  }
}
