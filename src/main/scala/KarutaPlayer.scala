package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.{OggVorbisDecoder,OpenSLESPlayer}
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.os.{AsyncTask,Bundle,SystemClock}
import _root_.android.media.audiofx.Equalizer
import _root_.android.widget.{Toast,CheckBox,CompoundButton}
import _root_.android.util.Log
import _root_.android.app.AlertDialog

import scala.collection.mutable

object KarutaPlayerDebug{
  val checksum_table = new mutable.HashMap[(String,Int,Int),String]
  def checkValid(w:WavBuffer,read_num:Int,kami_simo:Int){
    if(Globals.IS_DEBUG){
      val cs = w.checkSum
      val reader = Globals.prefs.get.getString("reader_path",null)
      val key = (reader,read_num,kami_simo)
      Log.d("wasuramoti_debug",s"key=${key}, checksum=${cs}")
      checksum_table.get(key) match{
        case None => checksum_table += {(key,cs)}
        case Some(x) => if( x != cs ){
                          val message = "checksum mismatch for " + key + ": " + x + " != " + cs
                          Log.d("wasuramoti_debug",message)
                          throw new Exception(message)
                        }
      }
    }
  }
}

case class OpenSLESTrack()

class KarutaPlayer(var activity:WasuramotiActivity,val reader:Reader,val cur_num:Int,val next_num:Int) extends BugReportable{
  type AudioQueue = Utils.AudioQueue
  var cur_millisec = 0:Long
  var music_track = None:Option[Either[AudioTrack,OpenSLESTrack]]
  var equalizer = None:Option[Equalizer]
  var equalizer_seq = None:Option[Utils.EqualizerSeq]
  var current_yomi_info = None:Option[Int]
  var set_audio_volume = true
  var play_started = None:Option[Long]

  val audio_queue = new AudioQueue() // file or silence in millisec
  // Executing SQLite query in doInBackground causes `java.lang.IllegalStateException: Cannot perform this operation because the connection pool has been closed'
  // Therefore, we execute it here
  val decode_task = (new OggDecodeTask().execute(new AnyRef())).asInstanceOf[OggDecodeTask]

  def releaseTrackSetNone(){
    music_track.foreach{case Left(x) => x.release();case _=>}
    music_track = None
  }

  def audioQueueInfo():String = {
    if(audio_queue.isEmpty){
      "(Empty)"
    }else{
      audio_queue.map{
        case Left(wav_buffer) => s"wav#${wav_buffer.toBugReport}"
        case Right(length) => s"blank#${length}ms"
      }.mkString("/")
    }
  }

  override def toBugReport():String = {
    val bld = new mutable.StringBuilder
    bld ++= s"cur_num:${cur_num},"
    bld ++= s"next_num:${next_num},"
    bld ++= s"reader_path:${reader.path},"
    bld ++= s"equalizer_seq:${equalizer_seq},"
    bld ++= s"current_yomi_info:${current_yomi_info},"
    bld ++= s"set_audio_volume:${set_audio_volume},"
    val audio_queue_info = audioQueueInfo()
    bld ++= s"audio_queue:${audio_queue_info}"
    bld.toString
  }

  def isAfterFirstPoem():Boolean = {
    play_started.exists{ x =>
      val elapsed = SystemClock.elapsedRealtime - x
      var counter = 0 // TODO: can we do the following without counter ?
      var joka_counter = 0 // Joka is treated as only one phrase in total
      val first_poem_length = audio_queue.takeWhile{
        case Left(wav_buffer) =>
          if(wav_buffer.num > 0){
            counter += 1
          }else if(joka_counter == 0){
            joka_counter = 1
          }
          (counter + joka_counter) < 2
        case Right(_) => true
      }.map{
        case Left(wav_buffer) => wav_buffer.audioLength
        case Right(length) => length
      }.sum
      elapsed > first_poem_length
    }
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

  def makeMusicTrack(){
    // getFirstDecoder waits for decoding ends
    val decoder = getFirstDecoder

    if(Globals.prefs.get.getBoolean("use_opensles",false)){
      // TODO: support audio other than 22050 mono
      if(decoder.channels != 1 || decoder.rate != 22050){
        throw new OpenSLESInvalidAudioFormatException("invalid audio format")
      }
      // it is safe to call slesCreateEngine multiple times.
      if(!(OpenSLESPlayer.slesCreateEngine() && OpenSLESPlayer.slesCreateBufferQueueAudioPlayer(Utils.getAudioStreamType))){
        throw new OpenSLESInitException("init error")
      }
      music_track = Some(Right(new OpenSLESTrack))
    }else{
      makeAudioTrack(getFirstDecoder)
    }
  }

  def makeAudioTrack(decoder:OggVorbisDecoder){
    val buffer_size_bytes = calcBufferSize
    val (audio_format,rate_1) = if(decoder.bit_depth == 16){
      (AudioFormat.ENCODING_PCM_16BIT,2)
    }else{
      //TODO: set appropriate value
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

    music_track = Some(Left(new AudioTrack( Utils.getAudioStreamType,
      decoder.rate.toInt,
      channels,
      audio_format,
      buffer_size,
      mode )))
    makeEqualizer()
  }
  def makeEqualizer(force:Boolean=false){
    val ar = equalizer_seq.getOrElse(Utils.getPrefsEqualizer())
    // Equalizer is supported by API >= 9
    if(android.os.Build.VERSION.SDK_INT < 9 || equalizer.nonEmpty || (!force && ar.seq.isEmpty)){
      return
    }
    // TODO: Ensure that music_track is not None here.
    //       See also EqualizerPref's add_seekbars
    music_track.foreach{ case Left(atrk) => {
      equalizer = Some{
        val eql = new Equalizer(0, atrk.getAudioSessionId)
        eql.setEnabled(true)
        val Array(min_eq,max_eq) = eql.getBandLevelRange
        for( i <- 0 until eql.getNumberOfBands){
          try{
            val r = ar.seq(i).getOrElse(0.5f)
            eql.setBandLevel(i.toShort,(min_eq+(max_eq-min_eq)*r).toShort)
          }catch{
            case _:Exception => Unit
          }
        }
        eql
      }
    }
    case _ =>
    }
  }
  def play(bundle:Bundle,auto_play:Boolean=false,from_swipe:Boolean=false){
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
        }else if(auto_play && !from_swipe){
          activity.scrollYomiInfo(R.id.yomi_info_view_next,true,Some({() => activity.invalidateYomiInfo()}))
        }else{
          activity.invalidateYomiInfo()
        }
      }
      bundle.putBoolean("auto_play",auto_play)
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
    audio_queue.collectFirst{
      case Left(w) => w.decoder
    }.getOrElse{
      throw new AudioQueueEmptyException("")
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
      music_track.foreach{
        case Left(x) => {x.stop();x.release()}
        case Right(_) => OpenSLESPlayer.slesStop()
      }
      music_track = None
      doWhenStop()
      KarutaPlayUtils.doAfterDone(bundle)
    }
  }

  def onReallyStart(bundle:Bundle){
    // Since makeMusicTrack() waits for decode task to ends and often takes a long time, we do it in another thread to avoid ANR.
    // Note: when calling Utils.messageDialog, you have to use activity.runOnUithread
    // TODO: using global_lock here will cause ANR since WasuramotiActivity.onMainButtonClick uses same lock.
    val thread = new Thread(new Runnable(){override def run(){
    Globals.global_lock.synchronized{
      val abort_playing = (mid:Option[Int]) => {
        mid.foreach{mm =>
          activity.runOnUiThread(new Runnable{
            override def run(){
              Utils.messageDialog(activity,Right(mm))
            }
         })
        }
        stop()
        Utils.setButtonTextByState(activity.getApplicationContext())
      }
      try{
        makeMusicTrack()
      }catch{
        case e:OggDecodeFailException => {
            activity.runOnUiThread(new Runnable{
                override def run(){
                  Utils.messageDialog(activity,Left(Option(e.getMessage).getOrElse("<null>") + "... will restart app."),{
                      ()=>Utils.restartApplication(activity.getApplicationContext)
                    })
                }
            })
            return
          }
        case e:AudioQueueEmptyException => {
          abort_playing(None)
          return
        }
        case e:OpenSLESInvalidAudioFormatException => {
          abort_playing(Some(R.string.opensles_invalid_audio_format))
          return
        }
        case e:OpenSLESInitException => {
          abort_playing(Some(R.string.opensles_init_error))
          return
        }
      }

      val buffer_length_millisec = AudioHelper.calcTotalMillisec(audio_queue)
      val buf = new Array[Short](calcBufferSize/AudioHelper.SHORT_SIZE)
      var offset = 0
      audio_queue.foreach{ arg => {
          arg match {
            case Left(w) => offset += w.writeToShortBuffer(buf,offset)
            case Right(millisec) => offset += AudioHelper.millisecToBufferSizeInBytes(getFirstDecoder(),millisec) / AudioHelper.SHORT_SIZE
            }
        }
      }

      val decode_and_play_again = () => {
        // Since refreshKarutaPlayer is synchronized in global_lock, we should do it in another thread
        music_track = None
        activity.runOnUiThread(new Runnable(){
            override def run(){
              Globals.player = AudioHelper.refreshKarutaPlayer(activity,Globals.player,true)
              // we won't wait playing for second time
              bundle.remove("wait_time")
              Globals.player.foreach{_.play(bundle)}
            }
        })
      }

      val r_write = music_track.map{
        case Left(atrk) => Left((atrk.write(buf,0,buf.length),atrk.getState))
        case Right(_) => Right(OpenSLESPlayer.slesEnqueuePCM(buf,buf.length))
      }

      if(Globals.IS_DEBUG){
        // you can add wav header to pcm file by following command
        //   $ sox -t raw -e signed -b 16 -r <sample_rate> -c <channels> IN_PCM_FILE OUT_WAV_FILE
        val temp_file = java.io.File.createTempFile("wasuramoti_","_decoded.pcm",activity.getApplicationContext.getCacheDir)
        val out = (new java.io.FileOutputStream(temp_file)).getChannel
        val bb = java.nio.ByteBuffer.allocate(buf.length*2)
        bb.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val sb = bb.asShortBuffer
        sb.put(buf)
        out.write(bb)
        out.close
      }

      // The following exception is throwed if AudioTrack.getState != STATE_INITIALIZED when AudioTrack.play() is called
      // `java.lang.IllegalStateException: play() called on uninitialized AudioTrack.`
      // It is occasionally reported from customer, but I could not figure out the cause.
      // Thus I will just retry again, and throw exception if it occurs second time.
      r_write.foreach{
        case Left((rw,state)) =>
          if( Array(AudioTrack.ERROR_INVALID_OPERATION,AudioTrack.ERROR_BAD_VALUE).contains(rw) ||
              state != AudioTrack.STATE_INITIALIZED ){
              val message = s"AudioTrack.write failed: rval=${rw}, state=${state}, blen=${buf.length}"
              Log.v("wasuramoti",message)
              if(Globals.audio_track_failed_count == 0){
                Globals.audio_track_failed_count += 1
                decode_and_play_again()
              }else{
                throw new Exception(message)
              }
              return
          }
        case _=>
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
      Globals.audio_track_failed_count = 0
      play_started = Some(SystemClock.elapsedRealtime)
      music_track.foreach{
        case Left(atrk) => atrk.play()
        case Right(_) => OpenSLESPlayer.slesPlay()
      }
    }}})
    thread.setUncaughtExceptionHandler(
      new Thread.UncaughtExceptionHandler(){
        override def uncaughtException(th:Thread,ex:Throwable){
          ex match{
            case e:java.lang.OutOfMemoryError => Utils.restartApplication(activity.getApplicationContext,true)
            case _ => throw(ex)
          }
        }
    })
    thread.start()
  }

  def stop(){
    Globals.global_lock.synchronized{
      for(action <- Array(
        KarutaPlayUtils.Action.Start,
        KarutaPlayUtils.Action.Border,
        KarutaPlayUtils.Action.End,
        KarutaPlayUtils.Action.WakeUp1,
        KarutaPlayUtils.Action.WakeUp2,
        KarutaPlayUtils.Action.WakeUp3
      )
      ){
        KarutaPlayUtils.cancelKarutaPlayTimer(
          activity.getApplicationContext,action
        )
      }
      music_track.foreach{
        case Left(track) => {
          track.flush()
          track.stop()
          track.release()
        }
        case Right(_) =>
          OpenSLESPlayer.slesStop()
      }
      music_track = None
      play_started = None
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
    // the signature of doInBackground must be `java.lang.Object doInBackground(java.lang.Object[])`. check in javap command.
    // otherwise it raises AbstractMethodError "abstract method not implemented"
    
    override def doInBackground(unused:AnyRef*):AnyRef = {
      this.synchronized{
      try{
        Utils.deleteCache(activity.getApplicationContext(),path => List(Globals.CACHE_SUFFIX_OGG).exists{s=>path.endsWith(s)})
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
        // Additionally, playing silence as wave file can avoid some weird effect
        // which occurs at beginning of wav when using bluetooth speaker.
        val (silence_time,_) = Utils.calcSilenceAndWaitLength
        add_to_audio_queue(Right(silence_time),true)
        // reuse decoded wav
        val decoded_wavs = new mutable.HashMap[(Int,Int),WavBuffer]()
        val ss = AudioHelper.genReadNumKamiSimoPairs(activity.getApplicationContext,cur_num,next_num)
        for (((read_num,kami_simo,is_cur),i) <-ss.zipWithIndex){
          if(isCancelled){
            // TODO: what should we return ?
            return Left(new AudioQueue())
          }
          if(reader.exists(read_num,kami_simo)){
            try{
              val key = (read_num,kami_simo)
              if(decoded_wavs.contains(key)){
                add_to_audio_queue(Left(decoded_wavs(key)),is_cur)
              }else{
                reader.withDecodedWav(read_num, kami_simo, wav => {
                   wav.trimFadeIn()
                   wav.trimFadeOut()
                   add_to_audio_queue(Left(wav),is_cur)
                   decoded_wavs.put(key,wav)
                   if(Globals.IS_DEBUG){
                     KarutaPlayerDebug.checkValid(wav,read_num,kami_simo)
                   }
                })
              }
            }catch{
              // Some device throws 'No space left on device' here
              case e:java.io.IOException => {
                if(Option(e.getMessage).getOrElse("").contains("No space left on device")){
                  activity.runOnUiThread(new Runnable{
                      override def run(){
                        val msg = activity.getResources.getString(R.string.error_ioerror,e.getMessage)
                        Utils.messageDialog(activity,Left(msg),{()=>throw new AlreadyReportedException(e.getMessage)})
                      }
                  })
                  return Right(new OggDecodeFailException("ogg decode failed with IOException: "+e.getMessage))
                }else{
                  throw e
                }
              }
            }
            if( i != ss.length - 1 ){
              add_to_audio_queue(Right(span_simokami),is_cur)
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
class OpenSLESInvalidAudioFormatException(s:String) extends Exception(s){
}
class OpenSLESInitException(s:String) extends Exception(s){
}

