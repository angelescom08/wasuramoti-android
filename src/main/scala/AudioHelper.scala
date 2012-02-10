package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.Context
import _root_.android.view.Gravity
import _root_.java.io.{File,FileInputStream}
import _root_.java.nio.{ByteBuffer,ByteOrder}
import _root_.java.util.{Timer,TimerTask}
import scala.collection.mutable

object AudioHelper{
  def refreshKarutaPlayer(context:Context,old_player:Option[KarutaPlayer],force:Boolean):Option[KarutaPlayer] = {
    val maybe_reader = ReaderList.makeCurrentReader(context)
    if(maybe_reader.isEmpty){
      return None
    }
    val current_index = FudaListHelper.getCurrentIndex(context)
    val num = if("RANDOM" == Globals.prefs.get.getString("read_order",null)){
      val cur_num = Globals.player match {
        case Some(player) => player.kami_num
        case None => 0
      }
      val next_num = FudaListHelper.queryRandom(context)
      Some(cur_num,next_num)
    }else{
      FudaListHelper.queryNext(context,current_index).map{
        case (cur_num,next_num,_,_) => (cur_num,next_num)
      }
    }
    num.flatMap{case(cur_num,next_num) =>{
        if(!maybe_reader.get.bothExists(cur_num,next_num)){
          None
        }else if(force || old_player.isEmpty || old_player.get.reader.path != maybe_reader.get.path || 
        old_player.get.simo_num != cur_num || old_player.get.kami_num != next_num
        ){
          Some(new KarutaPlayer(context,maybe_reader.get,cur_num,next_num))
        }else{
          old_player
        }
      }
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
class WavBuffer(buffer:Array[Short],val decoder:OggVorbisDecoder){
  val max_amp = (1 << (decoder.bit_depth-1)).toDouble
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
  def threasholdIndex(threashold:Double,fromEnd:Boolean):Int = {
    val (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    for( i <- bg to (ed,step) ){
      if( scala.math.abs(buffer(i)) / max_amp > threashold ){
        return i
      }
    }
    return(ed)
  }
  def fade(begin:Int,end:Int){
    if(begin == end){
      return
    }
    val width = scala.math.abs(begin - end).toDouble
    val step = if( begin < end ){ 1 } else { -1 }
    try{
      for( i <- begin to (end,step) ){
        val len = scala.math.abs(i - begin).toDouble
        val amp = len / width
        buffer.update(i,(buffer(i)*amp).toShort)
      }
    }catch{
      case e:ArrayIndexOutOfBoundsException => None
    }
  }
  // fadein
  def trimFadeKami(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold",0.01)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadein_kami",0.1) * decoder.rate).toInt
    val beg = threasholdIndex(threashold,false)
    val fadebegin = if ( beg - fadelen < 0 ) { 0 } else { beg - fadelen }
    fade(fadebegin,beg) 
    index_begin = fadebegin
  }
  // fadeout
  def trimFadeSimo(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold",0.01)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadeout_simo",0.2) * decoder.rate).toInt
    val end = threasholdIndex(threashold,true)
    val fadeend = if ( end - fadelen < 0) { 0 } else { end - fadelen }
    fade(end,fadeend) 
    index_end = end
  }
}

class KarutaPlayer(context:Context,val reader:Reader,val simo_num:Int,val kami_num:Int){

  var decode_thread = None:Option[Thread]
  //TODO: holding both wav_buffer and audio_track is quite redundant because it requires twice memory
  var wav_buffer = None:Option[WavBuffer]
  var audio_track = None:Option[AudioTrack]
  var simo_millsec = 0:Long
  var timer_start = None:Option[Timer]
  var timer_kamiend = None:Option[Timer]
  var timer_simoend = None:Option[Timer]
  var is_decoding = false
  var is_kaminoku = false
  startDecode()
  def play(onSimoEnd:Unit=>Unit=identity[Unit],onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      Globals.is_playing = true
      Utils.setButtonTextByState(context)
      timer_start = Some(new Timer())
      timer_start.get.schedule(new TimerTask(){
        override def run(){
          onReallyStart(onSimoEnd,onKamiEnd)
          timer_start.foreach(_.cancel())
          timer_start = None
        }},(Utils.getPrefAs[Double]("wav_begin_read",0.5)*1000.0).toLong)
    }
  }

  def onReallyStart(onSimoEnd:Unit=>Unit=identity[Unit],onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      waitDecode()
      audio_track = Some(wav_buffer.get.writeToAudioTrack())
      // TODO: AudioTrack.play sometimes throws IllegalStateException. find the reason and fix it.
      audio_track.get.play()
      timer_simoend = Some(new Timer())
      timer_kamiend = Some(new Timer())
      timer_simoend.get.schedule(new TimerTask(){
          override def run(){
            onSimoEnd()
            is_kaminoku=true
          }},simo_millsec)
      timer_kamiend.get.schedule(new TimerTask(){
          override def run(){
            Globals.is_playing=false
            onKamiEnd()
          }},wav_buffer.get.audioLength)
    }
  }
  def stop(){
    Globals.global_lock.synchronized{
      timer_start.foreach(_.cancel())
      timer_start = None
      timer_simoend.foreach(_.cancel())
      timer_kamiend.foreach(_.cancel())
      timer_simoend = None
      timer_kamiend = None
      audio_track.foreach(_.flush())
      audio_track.foreach(_.stop())
      audio_track.foreach(_.release())
      audio_track = None
      Globals.is_playing = false
    }
  }
  def startDecode(){ 
    Globals.global_lock.synchronized{
      if(is_decoding){
        return
      }
      Globals.progress_dialog.foreach{ pd => {
        pd.setMessage(context.getResources.getString(R.string.now_decoding))
        pd.getWindow.setGravity(Gravity.BOTTOM)
        pd.showWithHandler()
      }}
      is_decoding = true
      val t = new Thread(new Runnable(){
        override def run(){
          val audio_buf = new mutable.ArrayBuffer[Short]()
          var g_decoder:Option[OggVorbisDecoder] = None
          simo_millsec = 0
          def add_to_simo(w:WavBuffer){
            audio_buf ++= w.getBuffer
            simo_millsec += w.audioLength
          }
          val span_simokami = Utils.getPrefAs[Double]("wav_span_simokami",1.0)
          lazy val ma_simokami = AudioHelper.makeSilence(span_simokami,g_decoder.get)
          if(simo_num == 0 && reader.exists(simo_num,1)){
            reader.withDecodedWav(simo_num, 1, wav => {
               g_decoder = Some(wav.decoder)
               wav.trimFadeSimo()
               add_to_simo(wav)
            })
            add_to_simo(ma_simokami)
          }
          reader.withDecodedWav(simo_num, 2, wav => {
             g_decoder = Some(wav.decoder)
             wav.trimFadeSimo()
             add_to_simo(wav)
             if(simo_num == 0 && Globals.prefs.get.getBoolean("read_simo_joka_twice",false)){
               add_to_simo(ma_simokami)
               add_to_simo(wav)
             }
          })
          audio_buf ++= ma_simokami.getBuffer
          reader.withDecodedWav(kami_num, 1, wav => {
             wav.trimFadeKami()
             audio_buf ++= wav.getBuffer
          })
          //I assumed that cause of ClassCastException reported by user is using toArray with no ClassManifest.
          //Therefore I use toArray[Short] instead of toArray.
          wav_buffer = Some(new WavBuffer(audio_buf.toArray[Short],g_decoder.get))
          is_decoding = false
          Globals.progress_dialog.foreach{_.dismissWithHandler()}
        }
      })
      decode_thread = Some(t)
      t.start
    }
  }
  def waitDecode(){
    decode_thread.foreach(_.join())
  }
}
