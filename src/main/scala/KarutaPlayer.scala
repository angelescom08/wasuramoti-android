package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.os.{AsyncTask,Bundle}
import _root_.android.media.audiofx.Equalizer
import _root_.android.util.Log

import scala.collection.mutable

object KarutaPlayerDebug{
  val checksum_table = new mutable.HashMap[(String,Int,Int),String]
  def checkValid(w:WavBuffer,read_num:Int,kami_simo:Int){
    if(Globals.IS_DEBUG){
      val cs = w.checkSum
      val reader = Globals.prefs.get.getString("reader_path",null)
      val key = (reader,read_num,kami_simo)
      println("wasuramoti_debug: key="+key+" , checksum="+cs)
      checksum_table.get(key) match{
        case None => checksum_table += {(key,cs)}
        case Some(x) => if( x != cs ){ 
                          val message = "checksum mismatch for " + key + ": " + x + " != " + cs
                          println("wasuramoti_debug: " + message)
                          throw new Exception(message)
                        }
      }
    }
  }
}

class KarutaPlayer(var activity:WasuramotiActivity,val reader:Reader,val cur_num:Int,val next_num:Int){
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]
  var cur_millisec = 0:Long
  var audio_track = None:Option[AudioTrack]
  var equalizer = None:Option[Equalizer]
  var equalizer_seq = None:Option[Utils.EqualizerSeq]
  var current_yomi_info = None:Option[Int]
  var set_audio_volume = true

  val audio_queue = new AudioQueue() // file or silence in millisec
  // Executing SQLite query in doInBackground causes `java.lang.IllegalStateException: Cannot perform this operation because the connection pool has been closed'
  // Therefore, we execute it here
  val is_last_fuda = FudaListHelper.isLastFuda(activity.getApplicationContext())
  val decode_task = (new OggDecodeTask().execute(new AnyRef())).asInstanceOf[OggDecodeTask] // calling execute() with no argument raises AbstractMethodError "abstract method not implemented" in doInBackground

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
    makeAudioTrackAux(getFirstDecoder(),calcBufferSize())
  }

  def makeAudioTrackAux(decoder:OggVorbisDecoder,buffer_size_bytes:Int=0){
    val (audio_format,rate_1) = if(decoder.bit_depth == 16){
      (AudioFormat.ENCODING_PCM_16BIT,2)
    }else{
      //TODO:set appropriate value
      (AudioFormat.ENCODING_PCM_8BIT,1)
    }
    val (channels,rate_2) = if(decoder.channels == 1){
      (AudioFormat.CHANNEL_OUT_MONO,1)
    }else{
      (AudioFormat.CHANNEL_OUT_STEREO,2)
    }

    var (buffer_size,mode) = (buffer_size_bytes, AudioTrack.MODE_STATIC)
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
            case _:Exception => Unit
          }
        }
      })
    }catch{
      // Equalizer is only supported in Android 2.3+(API Level 9)
      case e:NoClassDefFoundError => None
    }
  }
  def play(bundle:Bundle,auto_play:Boolean=false){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      if(set_audio_volume){
        Utils.saveAndSetAudioVolume(activity.getApplicationContext())
      }
      Globals.is_playing = true

      Utils.setButtonTextByState(activity.getApplicationContext())
      val wait_time = bundle.getLong("wait_time",100)
      if(YomiInfoUtils.showPoemText){
        if(Utils.readCurNext(activity.getApplicationContext)){
          activity.scrollYomiInfo(R.id.yomi_info_view_cur,false)
        }else if(auto_play){
          activity.scrollYomiInfo(R.id.yomi_info_view_next,true,Some({_:Unit => activity.invalidateYomiInfo()}))
        }else{
          activity.invalidateYomiInfo()
        }
      }
      KarutaPlayUtils.startKarutaPlayTimer(
        activity.getApplicationContext,
        KarutaPlayUtils.Action.Start,
        wait_time,
        {_.putExtra("bundle",bundle)}
      )
    }
  }

  def getFirstDecoder():OggVorbisDecoder = {
    waitDecode()
    audio_queue.find{_.isLeft} match{
      case Some(x) => x match{
        case Left(w)=>w.decoder
        case Right(_) => throw new AudioQueueEmptyException("")// never happen
      }
      case None => throw new AudioQueueEmptyException("")
    }
  }

  def doWhenStop(){
    equalizer.foreach(_.release())
    equalizer = None
    Globals.is_playing = false
    current_yomi_info = None
    if(set_audio_volume){
      Utils.restoreAudioVolume(activity.getApplicationContext())
    }
  }

  def doWhenBorder(){
    current_yomi_info = Some(R.id.yomi_info_view_next)
    activity.scrollYomiInfo(R.id.yomi_info_view_next,true)
  }

  def doWhenDone(bundle:Bundle){
    Globals.global_lock.synchronized{
      audio_track.foreach(x => {x.stop();x.release()})
      audio_track = None
      doWhenStop()
      KarutaPlayUtils.doAfterDone(bundle:Bundle)
    }
  }

  def onReallyStart(bundle:Bundle){
    // Since makeAudioTrack() waits for decode task to ends and often takes a long time, we do it in another thread.
    new Thread(new Runnable(){override def run(){
    Globals.global_lock.synchronized{
      try{
        makeAudioTrack()
      }catch{
        case e:OggDecodeFailException => {
            activity.runOnUiThread(new Runnable{
                override def run(){
                  Utils.messageDialog(activity,Left(Option(e.getMessage).getOrElse("<null>") + "... will restart app."),{
                      _=>Utils.restartApplication(activity.getApplicationContext)
                    })
                }
            })
            return
          }
        case e:AudioQueueEmptyException => {
          stop()
          Utils.setButtonTextByState(activity.getApplicationContext())
          return
        }
      }
    
      val buffer_length_millisec = AudioHelper.calcTotalMillisec(audio_queue)
      val buf = new Array[Short](calcBufferSize()/(java.lang.Short.SIZE/java.lang.Byte.SIZE))
      var offset = 0
      audio_queue.foreach{ arg => {
          arg match {
            case Left(w) => offset += w.writeToShortBuffer(buf,offset)
            case Right(millisec) => offset += AudioHelper.millisecToBufferSizeInBytes(getFirstDecoder(),millisec) / (java.lang.Short.SIZE/java.lang.Byte.SIZE)
            }
        }
      }
      var r_write = audio_track.get.write(buf,0,buf.length)

      // The following exception is throwed if AudioTrack.getState != STATE_INITIALIZED when AudioTrack.play() is called
      // `java.lang.IllegalStateException: play() called on uninitialized AudioTrack.`
      // It is occasionally reported from customer, but I could not figure out the cause.
      // Thus I will just retry again, and throw exeception if it occurs second time.
      if( Array(AudioTrack.ERROR_INVALID_OPERATION,AudioTrack.ERROR_BAD_VALUE).contains(r_write) ||
          audio_track.get.getState != AudioTrack.STATE_INITIALIZED ){
          val message = "AudioTrack.write failed: rval=" + r_write + ", state=" + audio_track.get.getState + ", blen=" + buf.length
          Log.v("wasuramoti",message)
          if(Globals.audio_track_failed_count == 0){
            Globals.audio_track_failed_count += 1
            // Since refreshKarutaPlayer is synchronized in global_lock, we should do it in another thread
            activity.runOnUiThread(new Runnable(){
                override def run(){
                  doWhenStop()
                  Globals.player = AudioHelper.refreshKarutaPlayer(activity,Globals.player,true)
                  // we won't wait playing for second time
                  bundle.remove("wait_time")
                  Globals.player.foreach{_.play(bundle)}
                }
            })
          }else{
            throw new Exception(message)
          }
          return
      }

      if(bundle.getBoolean("have_to_run_border",false)){
        val t = Math.max(10,cur_millisec-900) // begin 900ms earlier
        // This timer must be started after makeAudioTrack() since it would take some time to finish
        KarutaPlayUtils.startKarutaPlayTimer(
          activity.getApplicationContext,
          KarutaPlayUtils.Action.Border,
          t,
          {_.putExtra("bundle",bundle)}
        )
      }

      // AudioTrack has a bug that onMarkerReached() is never invoked when static mode.
      // Therefore there seems no easy way to do a task when AudioTrack has finished playing.
      // As a workaround, I will start timer that ends when audio length elapsed.
      // See the following for the bug info:
      //   https://code.google.com/p/android/issues/detail?id=2563

      // Also, using AudioTrack in static mode seems to have another bug that
      // leaks shared memory even after stopped, released, and set to null.
      // You can confirm it by either executing `adb shell dumpsys meminfo karuta.hpnpwd.wasuramoti` and see Ashmem of PSS increases continuously in Android 4.x,
      // or `adb shell su -c "ls -l /proc/<pid>/fd | grep ashmem"` and see that a lot of symbolic link to /dev/ashmem is created.
      // The application will raise exception and terminates when this number of fd exceeds Max open files in /proc/<pid>/limits.
      // However, if we run garbage collection frequently, we can prevent the increase of fd's. 
      // Thus this +200ms gives CPU free time and make DalvikVM to run GC and call the native destructor of AudioTrack.
      // Note that calling System.gc() seems to have no effect.
      // See the following for the bug info:
      //   https://code.google.com/p/android/issues/detail?id=17995
      KarutaPlayUtils.startKarutaPlayTimer(
        activity.getApplicationContext,
        KarutaPlayUtils.Action.End,
        buffer_length_millisec+200,
        {_.putExtra("bundle",bundle)}
      )
      audio_track.get.play()
      Globals.audio_track_failed_count = 0
    }}}).start()
  }

  def stop(){
    Globals.global_lock.synchronized{
      for(action <- Array(
        KarutaPlayUtils.Action.Start,
        KarutaPlayUtils.Action.Border,
        KarutaPlayUtils.Action.End)
      ){
        KarutaPlayUtils.cancelKarutaPlayTimer(
          activity.getApplicationContext,action
        )
      }
      audio_track.foreach(track => {
          track.flush()
          track.stop()
          track.release()
        })
      audio_track = None
      doWhenStop()
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
    override def onPreExecute(){
      activity.runOnUiThread(new Runnable{
        override def run(){
          if(!activity.isFinishing){
            activity.showProgress
          }
        }
      })
    }
    override def onPostExecute(rval:Either[AudioQueue,Exception]){
      activity.runOnUiThread(new Runnable{
        override def run(){
          activity.hideProgress
          if(Globals.IS_DEBUG){
            if(rval.isLeft){
              activity.showAudioLength(AudioHelper.calcTotalMillisec(rval.left.get))
            }
          }
        }
      })
    }
    override def doInBackground(unused:AnyRef*):Either[AudioQueue,Exception] = {
      this.synchronized{
      try{
        Utils.deleteCache(activity.getApplicationContext(),path => List(Globals.CACHE_SUFFIX_OGG,Globals.CACHE_SUFFIX_WAV).exists{s=>path.endsWith(s)})
        val res_queue = new AudioQueue()
        val span_simokami = (Utils.getPrefAs[Double]("wav_span_simokami", 1.0, 9999.0) * 1000).toInt
        cur_millisec = 0
        def add_to_audio_queue(w:Either[WavBuffer,Int],is_cur:Boolean){
          res_queue.enqueue(w)
          val alen = w match{
            case Left(wav) => wav.audioLength
            case Right(len) => len
          }
          if(is_cur){
            cur_millisec += alen
          }
        }
        // Since android.media.audiofx.AudioEffect takes a little bit time to apply the effect,
        // we insert additional silence as wave data.
        add_to_audio_queue(Right(Globals.HEAD_SILENCE_LENGTH),true)
        val read_order_each = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
        var ss = read_order_each.split("_")
        if(cur_num == 0){
          val read_order_joka = Globals.prefs.get.getString("read_order_joka","upper_1,lower_1")
          ss = read_order_joka.split(",").flatMap{ s =>
            val Array(t,num) = s.split("_")
            Array.fill(num.toInt){
              t match{
                case "upper" => "CUR1"
                case "lower" => "CUR2"
              }
            }
          } ++ ss.filter(_.startsWith("NEXT"))
        }
        if(is_last_fuda && read_order_each.endsWith("NEXT1")){
          ss ++= Array("NEXT2")
        }
        for (i <- 0 until ss.length ){
          if(isCancelled){
            // TODO: what should we return ?
            return Left(new AudioQueue())
          }
          val s = ss(i)
          val read_num = if(s.startsWith("CUR")){
            cur_num
          }else{
            next_num
          }
          val kami_simo = if(s.endsWith("1")){
            1
          }else{
            2
          }
          if(reader.exists(read_num,kami_simo)){
            try{
              reader.withDecodedWav(read_num, kami_simo, wav => {
                 wav.trimFadeIn()
                 wav.trimFadeOut()
                 add_to_audio_queue(Left(wav),s.startsWith("CUR"))
                 if(Globals.IS_DEBUG){
                   KarutaPlayerDebug.checkValid(wav,read_num,kami_simo)
                 }
              })
            }catch{
              // Some device throws 'No space left on device' here
              case e:java.io.IOException => {
                if(Option(e.getMessage).getOrElse("").contains("No space left on device")){
                  activity.runOnUiThread(new Runnable{
                      override def run(){
                        val msg = activity.getResources.getString(R.string.error_ioerror,e.getMessage)
                        Utils.messageDialog(activity,Left(msg),{_=>throw new AlreadyReportedException(e.getMessage)})
                      }
                  })
                  return Right(new OggDecodeFailException("ogg decode failed with IOException: "+e.getMessage))
                }else{
                  throw e
                }
              }
            }
            if( i != ss.length - 1 ){
              add_to_audio_queue(Right(span_simokami),s.startsWith("CUR"))
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
}

class AudioQueueEmptyException(s:String) extends Exception(s){
}

