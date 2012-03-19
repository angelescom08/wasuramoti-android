package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.Context
import _root_.android.view.Gravity
import _root_.android.os.AsyncTask
import _root_.android.app.ProgressDialog
import _root_.java.io.{File,FileInputStream,RandomAccessFile}
import _root_.java.nio.{ByteBuffer,ByteOrder,ShortBuffer}
import _root_.java.nio.channels.FileChannel
import _root_.java.util.{Timer,TimerTask}
import scala.collection.mutable

object AudioHelper{
  def refreshKarutaPlayer(activity:WasuramotiActivity,old_player:Option[KarutaPlayer],force:Boolean):Option[KarutaPlayer] = {
    val app_context = activity.getApplicationContext()
    val maybe_reader = ReaderList.makeCurrentReader(app_context)
    if(maybe_reader.isEmpty){
      return None
    }
    val current_index = FudaListHelper.getCurrentIndex(app_context)
    val num = if("RANDOM" == Globals.prefs.get.getString("read_order",null)){
      val cur_num = Globals.player match {
        case Some(player) => player.kami_num
        case None => 0
      }
      val next_num = FudaListHelper.queryRandom(app_context)
      Some(cur_num,next_num)
    }else{
      FudaListHelper.queryNext(app_context,current_index).map{
        case (cur_num,next_num,_,_) => (cur_num,next_num)
      }
    }
    num.flatMap{case(cur_num,next_num) =>{
        if(!maybe_reader.get.bothExists(cur_num,next_num)){
          None
        }else if(force || old_player.isEmpty || old_player.get.reader.path != maybe_reader.get.path || 
        old_player.get.simo_num != cur_num || old_player.get.kami_num != next_num
        ){
          Some(new KarutaPlayer(activity,maybe_reader.get,cur_num,next_num))
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
  // note: modifying the follwing ShortBuffer is reflected to tho original file because it is casted from MappedByteBuffer
  def withMappedShortsFromFile(f:File,func:ShortBuffer=>Unit){
    val raf = new RandomAccessFile(f,"rw")
    func(raf.getChannel().map(FileChannel.MapMode.READ_WRITE,0,f.length()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer())
    raf.close()
  }
}
//TODO: handle stereo audio
class WavBuffer(buffer:ShortBuffer,val orig_file:File,val decoder:OggVorbisDecoder){
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
    // Since using ShortBuffer.array() throws UnsupportedOperationException (maybe because we are using FileChannel.map() ?),
    // we use ShortBuffer.get() instead.
    val b_size = index_end-index_begin
    val b = new Array[Short](b_size)
    buffer.position(index_begin)
    buffer.get(b,0,b_size)
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
  // if begin < end then fade-in else fade-out
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
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_begin >= index_end){
      index_begin = index_end - 1
    }
  }
  // fadeout
  def trimFadeSimo(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold",0.01)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadeout_simo",0.2) * decoder.rate).toInt
    val end = threasholdIndex(threashold,true)
    val fadeend = if ( end - fadelen < 0) { 0 } else { end - fadelen }
    fade(end,fadeend) 
    index_end = end
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_end <= index_begin){
      index_end = index_begin + 1
    }
  }
}

class KarutaPlayer(activity:WasuramotiActivity,val reader:Reader,val simo_num:Int,val kami_num:Int){
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]

  var audio_thread = None:Option[Thread]
  var simo_millsec = 0:Long
  var timer_start = None:Option[Timer]
  var timer_simoend = None:Option[Timer]
  var is_kaminoku = false
  var audio_track = None:Option[AudioTrack]
  val audio_queue = new AudioQueue() // file or silence in millisec 
  val decode_task = new OggDecodeTask().execute(new AnyRef()) // calling execute() with no argument raises AbstractMethodError "abstract method not implemented" in doInBackground

  def play(onSimoEnd:Unit=>Unit=identity[Unit],onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      Globals.is_playing = true
      Utils.setButtonTextByState(activity.getApplicationContext())
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
      timer_simoend.get.schedule(new TimerTask(){
          override def run(){
            onSimoEnd()
            is_kaminoku=true
          }},simo_millsec)
      audio_track = Some(AudioHelper.makeAudioTrack(audio_queue.find{_.isLeft}.get match{case Left(w)=>w.decoder}))
      audio_track.get.play()

      audio_thread = Some(new Thread(new Runnable(){
        override def run(){
          audio_queue.foreach{ arg =>{
            audio_track.foreach{ track =>
              arg match {
                case Left(w) => w.writeToAudioTrack(track)
                case Right(millisec) => AudioHelper.writeSilence(track,millisec)
              }
            }
            if(Thread.interrupted()){
              return
            }
          }}
          audio_track.foreach(x => {x.stop();x.release()})
          audio_track = None
          Globals.is_playing=false
          onKamiEnd()
        }
      }))
      audio_thread.get.start()
    }
  }
  def stop(){
    Globals.global_lock.synchronized{
      timer_start.foreach(_.cancel())
      timer_start = None
      timer_simoend.foreach(_.cancel())
      timer_simoend = None
      audio_thread.foreach(_.interrupt()) // Thread.inturrupt() just sets the audio_thread.isInterrupted flag to true. the actual interrupt is done in following.
      audio_track.foreach(track => {
          track.flush()
          track.stop() // calling this methods terminates AudioTrack.write() called in audio_thread.

          // Now since audio_thread.isInterrupted is true and AudioTrack.write() is terminated,
          // the audio_thread have to end immediately. 
          // Before calling AudioTrack.relase(), we have to wait for audio_thread to end.
          // The reason why I have to do this is assumed that
          // calling AudioTrack.stop() does not immediately terminate AudioTrack.write(), and
          // calling AudioTrack.release() before the termination is illecal.
          audio_thread.foreach{_.join()} 
          track.release()
        })
      audio_thread = None
      audio_track = None
      Globals.is_playing = false

    }
  }
  def waitDecode(){
    audio_queue ++= decode_task.get()
  }
  class OggDecodeTask extends AsyncTask[AnyRef,Void,AudioQueue] {
    var progress = None:Option[ProgressDialog]
    override def onPreExecute(){
      activity.runOnUiThread(new Runnable{
        override def run(){
          progress = Some(new ProgressDialog(activity))
          progress.get.setMessage(activity.getApplicationContext().getResources.getString(R.string.now_decoding))
          progress.get.getWindow.setGravity(Gravity.BOTTOM)
          progress.get.show()
        }
      })
    }
    override def onPostExecute(unused:AudioQueue){
      activity.runOnUiThread(new Runnable{
        override def run(){
          progress.get.dismiss()
        }
      })
    }
    override def doInBackground(unused:AnyRef*):AudioQueue = {
      Utils.deleteCache(activity.getApplicationContext(),path => List(Globals.CACHE_SUFFIX_OGG,Globals.CACHE_SUFFIX_WAV).exists{s=>path.endsWith(s)})
      val res_queue = new AudioQueue()
      simo_millsec = 0
      val span_simokami = (Utils.getPrefAs[Double]("wav_span_simokami",1.0) * 1000).toInt
      def add_to_audio_queue(w:Either[WavBuffer,Int],is_kami:Boolean){
        res_queue.enqueue(w)
        val alen = w match{
          case Left(wav) => wav.audioLength
          case Right(len) => len
        }
        if(!is_kami){
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
      return(res_queue)
    }
  }
}

