package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.Context
import _root_.android.view.Gravity
import _root_.android.os.AsyncTask
import _root_.android.app.ProgressDialog
import _root_.android.media.audiofx.Equalizer
import _root_.java.io.{File,FileInputStream,RandomAccessFile}
import _root_.java.nio.{ByteBuffer,ByteOrder,ShortBuffer}
import _root_.java.nio.channels.FileChannel
import _root_.java.util.{Timer,TimerTask}
import scala.collection.mutable

object AudioHelper{
  def millisecToBufferSizeInBytes(decoder:OggVorbisDecoder,millisec:Int):Int = {
    ((java.lang.Short.SIZE/java.lang.Byte.SIZE) *millisec * decoder.rate.toInt / 1000)*decoder.channels
  }
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
        }else if(force || Globals.forceRefresh || old_player.isEmpty || 
        old_player.get.simo_num != cur_num || old_player.get.kami_num != next_num
        ){
          Globals.forceRefresh = false
          Some(new KarutaPlayer(activity,maybe_reader.get,cur_num,next_num))
        }else{
          old_player
        }
      }
    }
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
class WavBuffer(buffer:ShortBuffer,val orig_file:File,val decoder:OggVorbisDecoder){
  val max_amp = (1 << (decoder.bit_depth-1)).toDouble
  var index_begin = 0
  var index_end = orig_file.length().toInt / 2
  // in milliseconds
  def audioLength():Long = {
    ((1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong)/decoder.channels
  }
  def bufferSizeInBytes():Int = {
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
  def WriteToShortBuffer(dst:Array[Short],offset:Int):Int = {
    val b_size = index_end-index_begin
    buffer.position(index_begin)
    buffer.get(dst,offset,if(offset+b_size > dst.length){dst.length-offset}else{b_size})
    buffer.rewind()
    return(b_size)
  }
  def threasholdIndex(threashold:Double,fromEnd:Boolean):Int = {
    var (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    bg = indexInBuffer(bg)
    ed = indexInBuffer(ed)
    try{
      for( i <- bg to (ed,step) ){
        if( scala.math.abs(buffer.get(i)) / max_amp > threashold ){
          return i
        }
      }
    }catch{
      // These exceptions shold not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case e:ArrayIndexOutOfBoundsException => return(bg)
      case e:IndexOutOfBoundsException => return(bg)
    }
    return(ed)
  }

  def indexInBuffer(i:Int):Int = {
    if(i < 0){
      return 0
    }
    if(i >= buffer.limit()){
      return (buffer.limit() - 1)
    }
    return i
  }

  // if begin < end then fade-in else fade-out
  def fade(i_begin:Int,i_end:Int){
    val begin = indexInBuffer(i_begin)
    val end = indexInBuffer(i_end)
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
      // These exceptions shold not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case e:ArrayIndexOutOfBoundsException => None
      case e:IndexOutOfBoundsException => None
    }
  }
  // fadein
  def trimFadeIn(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold", 0.01, 1.0)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadein_kami", 0.1, 9999.0) * decoder.rate * decoder.channels ).toInt
    val beg = threasholdIndex(threashold,false)
    val fadebegin = if ( beg - fadelen < 0 ) { 0 } else { beg - fadelen }
    fade(fadebegin,beg) 
    index_begin = ( fadebegin / decoder.channels ) * decoder.channels
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_begin >= index_end){
      index_begin = index_end - decoder.channels
    }
  }
  // fadeout
  def trimFadeOut(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold", 0.01, 1.0)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadeout_simo", 0.2, 9999.0) * decoder.rate * decoder.channels).toInt
    val end = threasholdIndex(threashold,true)
    val fadeend = if ( end - fadelen < 0) { 0 } else { end - fadelen }
    fade(end,fadeend) 
    index_end = ( end / decoder.channels ) * decoder.channels
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_end <= index_begin){
      index_end = index_begin + decoder.channels
    }
  }
}

class KarutaPlayer(activity:WasuramotiActivity,val reader:Reader,val simo_num:Int,val kami_num:Int){
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]

  var audio_thread = None:Option[Thread]
  var timer_start = None:Option[Timer]
  var timer_onend = None:Option[Timer]
  var audio_track = None:Option[AudioTrack]
  var equalizer = None:Option[Equalizer]
  var equalizer_seq = None:Option[EqualizerSeq]
  val audio_queue = new AudioQueue() // file or silence in millisec 
  val decode_task = new OggDecodeTask().execute(new AnyRef()) // calling execute() with no argument raises AbstractMethodError "abstract method not implemented" in doInBackground

  def isStreamMode():Boolean={
    return (Globals.prefs.get.getString("audio_track_mode","STATIC") == "STREAM")
  }
  def calcBufferSize():Int = {
      audio_queue.map{ arg =>{
          arg match {
            case Left(w) => w.bufferSizeInBytes()
            case Right(millisec) => AudioHelper.millisecToBufferSizeInBytes(getFirstDecoder(),millisec)
          }
        }
      }.sum
  }
  def makeAudioTrack(){
    if(isStreamMode()){
      makeAudioTrackAux(getFirstDecoder(),true)
    }else{
      makeAudioTrackAux(getFirstDecoder(),false,calcBufferSize())
    }
  }

  def makeAudioTrackAux(decoder:OggVorbisDecoder,is_stream:Boolean,buffer_size_bytes:Int=0){
    val (audio_format,rate_1) = if(decoder.bit_depth == 16){
      (AudioFormat.ENCODING_PCM_16BIT,2)
    }else{
      //TODO:set appropriate value
      (AudioFormat.ENCODING_PCM_8BIT,1)
    }
    val (channels,rate_2) = if(decoder.channels == 1){
      (AudioFormat.CHANNEL_CONFIGURATION_MONO,1)
    }else{
      (AudioFormat.CHANNEL_CONFIGURATION_STEREO,2)
    }

    var (buffer_size,mode) = if(is_stream){
        (AudioTrack.getMinBufferSize(
          decoder.rate.toInt, channels, audio_format), AudioTrack.MODE_STREAM)
      }else{
        (buffer_size_bytes, AudioTrack.MODE_STATIC)
      }
    // In order to avoid 'Invalid audio buffer size' from AudioTrack.audioBuffSizeCheck()
    val rate_3 = rate_1 * rate_2
    buffer_size = (buffer_size / rate_3) * rate_3

    audio_track = Some(new AudioTrack( AudioManager.STREAM_MUSIC,
      decoder.rate.toInt,
      channels,
      audio_format,
      buffer_size,
      mode ))
    makeEqualizer() 
  }
  // Precondition: audio_track is not None
  def makeEqualizer(force:Boolean=false){
    val ar = equalizer_seq.getOrElse(Utils.getPrefsEqualizer())
    if(!equalizer.isEmpty || (!force && ar.seq.isEmpty)){
      return
    }
    try{
      equalizer = Some(new Equalizer(0, audio_track.get.getAudioSessionId))
      equalizer.foreach(dest => {
        dest.setEnabled(true)
        val Array(min_eq,max_eq) = dest.getBandLevelRange
        for( i <- 0 until dest.getNumberOfBands){
          try{
            var r = ar.seq(i).getOrElse(0.5)
            dest.setBandLevel(i.toShort,(min_eq+(max_eq-min_eq)*r).toShort)
          }catch{
            case e:Exception => Unit
          }
        }
      })
    }catch{
      // Equalizer is only supported in Android 2.3+(API Level 9)
      case e:NoClassDefFoundError => None
    }
  }
  def play(onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      Globals.is_playing = true
      Utils.setButtonTextByState(activity.getApplicationContext())
      timer_start = Some(new Timer())
      // Since we insert some silence at beginning of audio,
      // the actual wait_time should be shorter.
      var wait_time = Utils.getPrefAs[Double]("wav_begin_read", 0.5, 9999.0)*1000.0 - Globals.HEAD_SILENCE_LENGTH
      if(wait_time < 100){
        wait_time = 100
      }

      timer_start.get.schedule(new TimerTask(){
        override def run(){
          onReallyStart(onKamiEnd)
          timer_start.foreach(_.cancel())
          timer_start = None
        }},wait_time.toLong)
    }
  }

  def getFirstDecoder():OggVorbisDecoder = {
    waitDecode()
    audio_queue.find{_.isLeft}.get match{case Left(w)=>w.decoder}
  }


  def onReallyStart(onKamiEnd:Unit=>Unit=identity[Unit]){
    Globals.global_lock.synchronized{
      val do_when_done = { _:Unit => {  
        audio_track.foreach(x => {x.stop();x.release()})
        audio_track = None
        equalizer.foreach(_.release())
        equalizer = None
        Globals.is_playing=false
        onKamiEnd()
      }}
      try{
        makeAudioTrack()
      }catch{
        case e:OggDecodeFailException => {
            activity.runOnUiThread(new Runnable{
                override def run(){
                  Utils.messageDialog(activity,Left(e.getMessage),{_=>do_when_done()})
                }
            })
            return
          }
      }
      if(isStreamMode()){
        // Play with AudioTrack.MODE_STREAM
        // This method requires small memory, but there is possibility of noize at few seconds after writeToAudioTrack.
        // I could not confirm such noice in my device, but some users claim that they have a noize in the beggining of upper poem.

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
            do_when_done()
          }
        }))
        audio_thread.get.start()
      }else{
        // Play with AudioTrack.MODE_STATIC
        // This method requires some memory overhead, but able to reduce possibility of noize since it only writes to AudioTrack once.

        val buffer_length_millisec = audio_queue.map{ arg =>{
          arg match {
            case Left(w) => w.audioLength()
            case Right(millisec) => millisec
            }
          }
        }.sum
        val buf = new Array[Short](calcBufferSize()/(java.lang.Short.SIZE/java.lang.Byte.SIZE))
        var offset = 0
        audio_queue.foreach{ arg => {
            arg match {
              case Left(w) => offset += w.WriteToShortBuffer(buf,offset) 
              case Right(millisec) => offset += AudioHelper.millisecToBufferSizeInBytes(getFirstDecoder(),millisec) / (java.lang.Short.SIZE/java.lang.Byte.SIZE)
              }
          }
        }
        audio_track.get.write(buf,0,buf.length)
        timer_onend = Some(new Timer())
        timer_onend.get.schedule(new TimerTask(){
          override def run(){
            do_when_done()
          }},buffer_length_millisec +200) // +200 is just for sure that audio is finished playing
        audio_track.get.play()
      }
    }
  }
  
  def stop(){
    Globals.global_lock.synchronized{
      timer_start.foreach(_.cancel())
      timer_start = None
      timer_onend.foreach(_.cancel())
      timer_onend = None
      audio_thread.foreach(_.interrupt()) // Thread.inturrupt() just sets the audio_thread.isInterrupted flag to true. the actual interrupt is done in following.
      audio_track.foreach(track => {
          track.flush()
          track.stop() // calling this methods terminates AudioTrack.write() called in audio_thread.

          // Now since audio_thread.isInterrupted is true and AudioTrack.write() is terminated,
          // the audio_thread have to end immediately. 
          // Before calling AudioTrack.relase(), we have to wait for audio_thread to end.
          // The reason why I have to do this is assumed that
          // calling AudioTrack.stop() does not immediately terminate AudioTrack.write(), and
          // calling AudioTrack.release() before the termination is illegal.
          audio_thread.foreach{_.join()} 
          track.release()
        })
      audio_thread = None
      audio_track = None
      equalizer.foreach(_.release())
      equalizer = None
      Globals.is_playing = false

    }
  }
  def waitDecode(){
    Globals.global_lock.synchronized{
      if(audio_queue.isEmpty){
        decode_task.get match{
          case Left(aq) => audio_queue ++= aq
          case Right(e) => throw e
        }
      }
    }
  }
  class OggDecodeTask extends AsyncTask[AnyRef,Void,Either[AudioQueue,Exception]] {
    var progress = None:Option[ProgressDialog]
    override def onPreExecute(){
      activity.runOnUiThread(new Runnable{
        override def run(){
          //We have to check whether activity is in finishing phase or not to avoid the following error:
          //android.view.WindowManager$BadTokenException: Unable to add window -- token android.os.BinderProxy@XXXXXXXX is not valid; is your activity running?
          if(!activity.isFinishing()){
            progress = Some(new ProgressDialog(activity))
            progress.get.setMessage(activity.getApplicationContext().getResources.getString(R.string.now_decoding))
            progress.get.getWindow.setGravity(Gravity.BOTTOM)
            progress.get.show()
          }
        }
      })
    }
    override def onPostExecute(unused:Either[AudioQueue,Exception]){
      activity.runOnUiThread(new Runnable{
        override def run(){
          progress.foreach(_.dismiss())
        }
      })
    }
    override def doInBackground(unused:AnyRef*):Either[AudioQueue,Exception] = {
      try{
        Utils.deleteCache(activity.getApplicationContext(),path => List(Globals.CACHE_SUFFIX_OGG,Globals.CACHE_SUFFIX_WAV).exists{s=>path.endsWith(s)})
        val res_queue = new AudioQueue()
        val span_simokami = (Utils.getPrefAs[Double]("wav_span_simokami", 1.0, 9999.0) * 1000).toInt
        def add_to_audio_queue(w:Either[WavBuffer,Int]){
          res_queue.enqueue(w)
          val alen = w match{
            case Left(wav) => wav.audioLength
            case Right(len) => len
          }
        }
        // Since android.media.audiofx.AudioEffect takes a little bit time to apply the effect,
        // we insert additional silence as wave data.
        add_to_audio_queue(Right(Globals.HEAD_SILENCE_LENGTH))
        val read_order_each = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
        val ss = read_order_each.split("_")
        if(simo_num == 0 && read_order_each.startsWith("CUR2") && reader.exists(simo_num,1)){
          reader.withDecodedWav(simo_num, 1, wav => {
             wav.trimFadeOut()
             add_to_audio_queue(Left(wav))
          })
          add_to_audio_queue(Right(span_simokami))
        }
        for (i <- 0 until ss.length ){
          val s = ss(i)
          val read_num = if(s.startsWith("CUR")){
            simo_num
          }else{
            kami_num
          }
          val kami_simo = if(s.endsWith("1")){
            1
          }else{
            2
          }
          if(!(read_num == 0 && kami_simo == 1 && ! reader.exists(read_num,kami_simo))){
            reader.withDecodedWav(read_num, kami_simo, wav => {
               wav.trimFadeIn()
               wav.trimFadeOut()
               add_to_audio_queue(Left(wav))
               if(read_num == 0 && kami_simo == 2 && Globals.prefs.get.getBoolean("read_simo_joka_twice",false)){
                 add_to_audio_queue(Right(span_simokami))
                 add_to_audio_queue(Left(wav))
               }
            })
            if( i != ss.length - 1 ){
              add_to_audio_queue(Right(span_simokami))
            }
          }
        }
        return(Left(res_queue))
      }catch{
        case e:OggDecodeFailException => return(Right(e))
      }
    }
  }
}

