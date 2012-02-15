package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.Context
import _root_.android.view.Gravity
import _root_.java.io.{File,FileInputStream,RandomAccessFile}
import _root_.java.nio.{ByteBuffer,ByteOrder,ShortBuffer}
import _root_.java.nio.channels.FileChannel
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
  def makeAudioTrack(decoder:OggVorbisDecoder):AudioTrack ={
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

    val buffer_size = AudioTrack.getMinBufferSize(
      decoder.rate.toInt, channels, audio_format); 

    new AudioTrack( AudioManager.STREAM_MUSIC,
      decoder.rate.toInt,
      channels,
      audio_format,
      buffer_size,
      AudioTrack.MODE_STREAM )
  }
  def writeSilence(track:AudioTrack,millisec:Int){
    val buf = new Array[Short](track.getSampleRate()*millisec/1000)
    track.write(buf,0,buf.length)
  }
  // note: the change made to ShortBuffer is reflected to tho original file
  def withMappedShortsFromFile(f:File,func:ShortBuffer=>Unit){
    val raf = new RandomAccessFile(f,"rw")
    func(raf.getChannel().map(FileChannel.MapMode.READ_WRITE,0,f.length()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer())
    raf.close()
  }
}
//TODO:handle stereo audio
class WavBuffer(buffer:ShortBuffer,orig_file:File,val decoder:OggVorbisDecoder){
  val max_amp = (1 << (decoder.bit_depth-1)).toDouble
  var index_begin = 0
  var index_end = orig_file.length().toInt / 2
  // in milliseconds
  def audioLength():Long = {
    (1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong
  }
  def bufferSize():Int = {
    (java.lang.Short.SIZE/java.lang.Byte.SIZE) * (index_end - index_begin)
  }
  def writeToAudioTrack(track:AudioTrack){
    // Using ShortBuffer.array() throws UnsupportedOperationException (maybe because we use FileChannel.map ?)),
    // thus we use ShortBuffer.get() instead.
    val b_size = index_end-index_begin
    val b = new Array[Short](b_size)
    buffer.rewind()
    buffer.get(b,index_begin,b_size)
    buffer.rewind()
    track.write(b,0,b_size)
  }
  def threasholdIndex(threashold:Double,fromEnd:Boolean):Int = {
    val (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    for( i <- bg to (ed,step) ){
      if( scala.math.abs(buffer.get(i)) / max_amp > threashold ){
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
        buffer.put(i,(buffer.get(i)*amp).toShort)
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
  var audio_thread = None:Option[Thread]
  var simo_millsec = 0:Long
  var kami_millsec = 0:Long
  var timer_start = None:Option[Timer]
  var timer_kamiend = None:Option[Timer]
  var timer_simoend = None:Option[Timer]
  var is_decoding = false
  var is_kaminoku = false
  var audio_track = None:Option[AudioTrack]
  var audio_queue = None:Option[mutable.Queue[Either[WavBuffer,Int]]] // file or silence in millisec 
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
          }},simo_millsec+kami_millsec)
      audio_track = Some(AudioHelper.makeAudioTrack(audio_queue.get.find{_.isLeft}.get match{case Left(w)=>w.decoder}))
      audio_track.get.play()

      audio_thread = Some(new Thread(new Runnable(){
        override def run(){
          audio_queue.foreach{_.foreach{ arg =>{
            if(Thread.interrupted()){
              return
            }
            audio_track.foreach{ track =>
              arg match {
                case Left(w) => w.writeToAudioTrack(track)
                case Right(millisec) => AudioHelper.writeSilence(track,millisec)
              }
            }
          }}
        }}
      }))
      audio_thread.get.start()
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
      audio_thread.foreach(_.interrupt())
      audio_thread = None
      audio_track.foreach(x => {x.stop();x.release()})
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
          Utils.deleteAllCache(context)
          audio_queue = Some(new mutable.Queue[Either[WavBuffer,Int]]())
          simo_millsec = 0
          kami_millsec = 0
          val span_simokami = (Utils.getPrefAs[Double]("wav_span_simokami",1.0) * 1000).toInt
          def add_to_audio_queue(w:Either[WavBuffer,Int],is_kami:Boolean){
            audio_queue.foreach{_.enqueue(w)}
            val alen = w match{
              case Left(wav) => wav.audioLength
              case Right(len) => len
            }
            if(is_kami){
              kami_millsec += alen
            }else{
              simo_millsec += alen
            }
          }
          if(simo_num == 0 && reader.exists(simo_num,1)){
            reader.withDecodedWav(simo_num, 1, wav => {
               wav.trimFadeSimo()
               add_to_audio_queue(Left(wav),false)
            })
            add_to_audio_queue(Right(span_simokami),false)
          }
          reader.withDecodedWav(simo_num, 2, wav => {
             wav.trimFadeSimo()
             add_to_audio_queue(Left(wav),false)
             if(simo_num == 0 && Globals.prefs.get.getBoolean("read_simo_joka_twice",false)){
               add_to_audio_queue(Right(span_simokami),false)
               add_to_audio_queue(Left(wav),false)
             }
          })
          add_to_audio_queue(Right(span_simokami),false)
          reader.withDecodedWav(kami_num, 1, wav => {
             wav.trimFadeKami()
             add_to_audio_queue(Left(wav),true)
          })
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
