package karuta.hpnpwd.wasuramoti

import karuta.hpnpwd.audio.{OggVorbisDecoder,OpenSLESPlayer}
import android.media.{AudioManager,AudioFormat,AudioTrack}
import android.os.{AsyncTask,Bundle,SystemClock}
import android.media.audiofx.Equalizer
import android.widget.Toast
import android.util.Log
import android.content.Context

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

class KarutaPlayer(var activity:WasuramotiActivity,val maybe_reader:Option[Reader],val cur_num:Int,val next_num:Int) extends BugReportable{
  type AudioQueue = AudioHelper.AudioQueue
  var cur_millisec = 0:Long
  var music_track = None:Option[Either[AudioTrack,OpenSLESTrack]]
  var equalizer = None:Option[Equalizer]
  var equalizer_seq = None:Option[Utils.EqualizerSeq]
  var current_yomi_info = None:Option[Int]
  var set_audio_volume = true
  var play_started = None:Option[Long]
  var is_replay = false

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
    bld ++= s"reader_path:${maybe_reader.map(_.path)},"
    bld ++= s"equalizer_seq:${equalizer_seq},"
    bld ++= s"current_yomi_info:${current_yomi_info},"
    bld ++= s"set_audio_volume:${set_audio_volume},"
    val audio_queue_info = audioQueueInfo()
    bld ++= s"audio_queue:${audio_queue_info}"
    bld.toString
  }

  def isAfterFirstPhrase():Boolean = {
    play_started.exists{ x =>
      val elapsed = SystemClock.elapsedRealtime - x
      var counter = 0 // TODO: can we do the following without counter ?
      var joka_counter = 0 // Joka is treated as only one phrase in total
      val first_phrase_length = audio_queue.takeWhile{
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
      elapsed > first_phrase_length
    }
  }

  def makeMusicTrack(queue:AudioQueue){
    val decoder = getFirstDecoder(queue)

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
      makeAudioTrack(decoder,queue)
    }
  }

  def makeAudioTrack(decoder:OggVorbisDecoder,queue:AudioQueue){
    val buffer_size_bytes = AudioHelper.calcBufferSize(decoder,queue)
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
    if(equalizer.nonEmpty || (!force && ar.seq.isEmpty)){
      return
    }
    // TODO: Ensure that music_track is not None here.
    //       See also EqualizerPref's addSeekbars
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

  def isDeviceVolumeTooSmall():Boolean = {
    val threshold = 0.08f
    val av = Utils.readPrefAudioVolume
    if(set_audio_volume && av.nonEmpty){
      return (av.get < threshold)
    }else{
      val am = activity.getApplicationContext.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
      if(am != null){
        val stream_type = Utils.getAudioStreamType
        val max_volume =  am.getStreamMaxVolume(stream_type)
        val cur_volume = am.getStreamVolume(stream_type)
        if(max_volume > 0 && cur_volume.toFloat / max_volume.toFloat < threshold){
          return true
        }
      }
      return false
    }
  }

  def haveToAlertForSilentMode():Boolean = {
    val am = activity.getApplicationContext.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    if(am != null){
      am.getRingerMode match {
        case AudioManager.RINGER_MODE_SILENT | AudioManager.RINGER_MODE_VIBRATE =>
          // alert only when is earphone is not plugged in
          ! am.isWiredHeadsetOn && ! am.isBluetoothScoOn && ! am.isBluetoothA2dpOn
        case _ =>
          false
      }
    }else{
      false
    }
  }

  def checkEqualizerVolume():Boolean = {
    val eql = Utils.getPrefsEqualizer.flatten
    eql.isEmpty || ! eql.forall{  _ < 0.05}
  }

  def playWithoutConfirm(bundle:Bundle,fromAuto:Boolean=false,fromSwipe:Boolean=false){
    Globals.global_lock.synchronized{
      if(!fromAuto && !KarutaPlayUtils.requestAudioFocus(activity)){
        Toast.makeText(activity.getApplicationContext,R.string.stopped_since_audio_focus,Toast.LENGTH_SHORT).show()
        return
      }
      if(bundle.getString("fromSender") == KarutaPlayUtils.SENDER_MAIN && Globals.prefs.get.getBoolean("autoplay_enable",false)){
        bundle.putBoolean("autoplay",true)
        if(!fromAuto){
          Globals.autoplay_started = Some(System.currentTimeMillis)
        }
      }
      
      if(set_audio_volume){
        Utils.saveAndSetAudioVolume(activity.getApplicationContext())
      }
      Globals.is_playing = true
      is_replay = bundle.getString("fromSender") == KarutaPlayUtils.SENDER_REPLAY
      KarutaPlayUtils.setReplayButtonEnabled(activity,Some(false))
      KarutaPlayUtils.setRewindButtonEnabled(activity,Some(false))
      activity.setButtonTextByState()
      // if is_replay is true, poem text is invalidated in forceYomiInfoView(), so we dont invalidate here.
      if(YomiInfoUtils.showPoemText && !is_replay){
        if(Utils.readCurNext(activity.getApplicationContext)){
          activity.scrollYomiInfo(R.id.yomi_info_view_cur,false)
        }else if(fromAuto && !fromSwipe){
          activity.scrollYomiInfo(R.id.yomi_info_view_next,true,Some({() => activity.invalidateYomiInfo()}))
        }else{
          activity.invalidateYomiInfo()
        }
        KarutaPlayUtils.startCheckConsistencyTimers()
      }
      // do the rest in another thread
      onReallyStart(bundle, fromAuto)
    }
  }


  def play(bundle:Bundle,fromAuto:Boolean=false,fromSwipe:Boolean=false){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      lazy val baseBundle = {
        val b = new Bundle
        b.putBundle("play_bundle",bundle)
        b.putBoolean("from_auto",fromAuto)
        b.putBoolean("from_swipe",fromSwipe)
        b
      }
      // We don't need to confirm both volume_alert and ringer_mode_alert since volume_alert implies ringer_mode_alert.
      // That is because ringer_mode_alert is intended to "Don't play sound when silent mode", and if the volume is low,
      // playing the sound won't bother silent mode.
      val cur_time = java.lang.System.currentTimeMillis
      val haveToAlert = !fromAuto &&
         Array(KarutaPlayUtils.SENDER_MAIN,KarutaPlayUtils.SENDER_REPLAY).contains(bundle.getString("fromSender"))
      if(haveToAlert && KarutaPlayUtils.elapsedEnoghSinceLastConfirm(cur_time,KarutaPlayUtils.last_confirmed_for_volume)
        && Globals.prefs.get.getBoolean("volume_alert",true) && isDeviceVolumeTooSmall){
        val args = baseBundle
        args.putString("tag","volume_alert_confirm")
        CommonDialog.generalCheckBoxConfirmDialogWithCallback(
          activity,Right(R.string.conf_volume_alert_confirm),Right(R.string.never_confirm_again),args)
      }else if(haveToAlert && KarutaPlayUtils.elapsedEnoghSinceLastConfirm(cur_time,KarutaPlayUtils.last_confirmed_for_ringer_mode) &&
        Globals.prefs.get.getBoolean("ringer_mode_alert",true) && haveToAlertForSilentMode){
        val args = baseBundle
        args.putString("tag","ringer_mode_alert_confirm")
        CommonDialog.generalCheckBoxConfirmDialogWithCallback(
          activity,Right(R.string.conf_ringer_mode_alert_confirm),Right(R.string.never_confirm_again),args)
      }else{
        playWithoutConfirm(bundle,fromAuto,fromSwipe)
      }
    }
  }

  def getFirstDecoder(queue:AudioQueue):OggVorbisDecoder = {
    queue.collectFirst{
      case Left(w) => w.decoder
    }.getOrElse{
      throw new AudioQueueEmptyException("")
    }
  }

  def doWhenBorder(){
    current_yomi_info = Some(R.id.yomi_info_view_next)
    activity.scrollYomiInfo(R.id.yomi_info_view_next,true)
  }

  def forceYomiInfoView(q:AudioQueue){
    val num = q.find(_.isLeft).get.left.get.num
    val vw = activity.findViewById(R.id.yomi_info_view_cur).asInstanceOf[YomiInfoView]
    if(vw != null){
      activity.runOnUiThread(new Runnable(){
        override def run(){
          vw.updateCurNum(Some(num))
          vw.invalidate()
        }
      })
    }
  }

  def onReallyStart(bundle:Bundle, fromAuto:Boolean){
    // Since makeMusicTrack() waits for decode task to ends and often takes a long time, we do it in another thread to avoid ANR.
    // TODO: using global_lock here will cause ANR since WasuramotiActivity.onMainButtonClick uses same lock.
    val thread = new Thread(new Runnable(){override def run(){
    Globals.global_lock.synchronized{
      val abort_playing = (mid:Option[Either[String,Int]]) => {
        mid.foreach{mm =>
          // this dialog might be shown in background, so we use showMessageDialogOrEnqueue()
          activity.runOnUiThread(new Runnable{
            override def run(){
              activity.showMessageDialogOrEnqueue(mm)
            }
         })
        }
        stop()
        activity.setButtonTextByState()
      }
      val current_queue = if(is_replay){
        if(KarutaPlayUtils.replay_audio_queue.isEmpty){
          abort_playing(None)
          return
        }
        val q = KarutaPlayUtils.replay_audio_queue.get
        forceYomiInfoView(q)
        q
      }else{
        try{
          waitDecodeAndUpdateAudioQueue()
        }catch{
          case e:OggDecodeFailException => {
              val msg = activity.getResources.getString(R.string.ogg_decode_failed) + "\n---\n" + Option(e.getMessage).getOrElse("<null>")
              abort_playing(Some(Left(msg)))
              return
            }
            case e:java.lang.UnsatisfiedLinkError => {
              abort_playing(Some(Right(R.string.unsatisfied_link_error)))
              return
            }
        }
        audio_queue
      }
      try{
        makeMusicTrack(current_queue)
      }catch{
        case e:AudioQueueEmptyException => {
          abort_playing(None)
          return
        }
        case e:OpenSLESInvalidAudioFormatException => {
          abort_playing(Some(Right(R.string.opensles_invalid_audio_format)))
          return
        }
        case e:OpenSLESInitException => {
          abort_playing(Some(Right(R.string.opensles_init_error)))
          return
        }
      }

      val buffer_length_millisec = AudioHelper.calcTotalMillisec(current_queue)
      val buf = AudioHelper.makeBuffer(getFirstDecoder(current_queue),current_queue)

      val decode_and_play_again = () => {
        // Since refreshKarutaPlayer is synchronized in global_lock, we should do it in another thread
        music_track = None
        activity.runOnUiThread(new Runnable(){
            override def run(){
              Globals.player = AudioHelper.refreshKarutaPlayer(activity,Globals.player,true)
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
        KarutaPlayUtils.startBorderTimer(t)
      }


      // Using AudioTrack in static mode seems to have bug that
      // leaks shared memory even after stopped, released, and set to null.
      // You can confirm it by either executing `adb shell dumpsys meminfo karuta.hpnpwd.wasuramoti` and see Ashmem of PSS increases continuously in Android 4.x,
      // or `adb shell su -c "ls -l /proc/<pid>/fd | grep ashmem"` and see that a lot of symbolic link to /dev/ashmem is created.
      // The application will raise exception and terminates when this number of fd exceeds Max open files in /proc/<pid>/limits.
      // However, if we run garbage collection frequently, we can prevent the increase of fd's.
      // Thus this +200ms gives CPU free time and make DalvikVM to run GC and call the native destructor of AudioTrack.
      // Note that calling System.gc() seems to have no effect.
      // See the following for the bug info:
      //   https://issuetracker.google.com/code/p/android/issues/detail?id=17995
      val play_end_time = buffer_length_millisec+200

      // AudioTrack has a bug that onMarkerReached() is never invoked when static mode.
      // Therefore there seems no easy way to do a task when AudioTrack has finished playing.
      // As a workaround, I will start timer that ends when audio length elapsed.
      // See the following for the bug info:
      //   https://issuetracker.google.com/code/p/android/issues/detail?id=2563
      KarutaPlayUtils.startEndTimer(play_end_time,bundle)

      Globals.audio_track_failed_count = 0
      play_started = Some(SystemClock.elapsedRealtime)

      // we enable skip button after `play_started` is set.
      // this is because KarutaPlayUtils.skipToNext avoids miss click using value of `play_started`
      KarutaPlayUtils.setSkipButtonEnabled(activity,Some(true))
      
      music_track.foreach{ t => {
        if(fromAuto && KarutaPlayUtils.have_to_mute){
          Utils.setVolumeMute(t,true)
        }else{
          // TODO: warn that it have to mute, however, note that current `have_to_mute` flag is inaccurate
          //       since it cannot capture the event after abandonAudioFocus
          KarutaPlayUtils.have_to_mute  = false
          Utils.runOnUiThread(activity,()=>
              activity.setButtonTextByState()
          )
        }
        t match {
          case Left(atrk) => atrk.play()
          case Right(_) => OpenSLESPlayer.slesPlay()
        }
      }}

      if(bundle.getBoolean("autoplay",false)){
        val auto_delay = Globals.prefs.get.getLong("autoplay_span", 3)*1000
        // add three extra seconds of next wake lock timeout
        val EXTRA_TIMEOUT = 3000
        // acquire lock 0.3 seconds before play ends
        val EARLY_LOCK = 300
        if(KarutaPlayUtils.haveToFullyWakeLock){
          // acquire wake lock uses timeout handler, so maybe we have to run on UI thread ?
          Utils.runOnUiThread(activity,()=>
            KarutaPlayUtils.acquireWakeLock(activity.getApplicationContext,play_end_time + auto_delay + EXTRA_TIMEOUT)
          )
        }else{
          // we only have to acquire wake lock in play to play span since AudioFlinger does the wake lock.
          KarutaPlayUtils.releaseWakeLock()
          KarutaPlayUtils.startWakeLockTimer(activity.getApplicationContext,buffer_length_millisec - EARLY_LOCK, auto_delay + EARLY_LOCK + EXTRA_TIMEOUT)
        }
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

  def commonJobWhenFinished(releaseAudioFocusAndWakeLock:Boolean){
    equalizer.foreach(_.release())
    equalizer = None
    Globals.is_playing = false
    current_yomi_info = None
    if(set_audio_volume){
      Utils.restoreAudioVolume(activity.getApplicationContext())
    }
    if(releaseAudioFocusAndWakeLock){
      KarutaPlayUtils.releaseResourcesHeldDuringAutoPlay(activity.getApplicationContext)
    }
    if(isAfterFirstPhrase && !is_replay){
      KarutaPlayUtils.replay_audio_queue = AudioHelper.pickLastPhrase(audio_queue)
    }
    play_started = None // have to set None after calling isAfterFirstPhrase() since it uses this value
    if(maybe_reader.isEmpty){
      // player was created from startReplay(), so it does not have valid audio_queue
      // so we cleanup it to avoid playing by main button.
      Globals.player = None
    }
    KarutaPlayUtils.setReplayButtonEnabled(activity)
    KarutaPlayUtils.setSkipButtonEnabled(activity,Some(false))
    KarutaPlayUtils.setRewindButtonEnabled(activity,Some(true))
    if(is_replay){
      // restore normal poem text which is changed by forceYomiInfoView()
      activity.invalidateYomiInfo()
    }
    is_replay = false
  }


  // called when playing is interrupted by user or any other reason
  def stop(fromAuto:Boolean = false){
    Globals.global_lock.synchronized{
      KarutaPlayUtils.cancelEndTimer()
      KarutaPlayUtils.cancelBorderTimer()
      KarutaPlayUtils.cancelCheckConsistencyTimers()
      music_track.foreach{
        case Left(track) => {
          track.flush()
          try{
            track.stop()
          }catch{
            case _:IllegalStateException =>
              // We get some random IllegalStateException when track.stop() from bug report
              // We could not found any solution, so we just ignore it
          }
          track.release()
        }
        case Right(_) =>
          OpenSLESPlayer.slesStop()
      }
      music_track = None
      commonJobWhenFinished(!fromAuto)
    }
  }

  // called after playing is finished without any interruption
  def doWhenDone(bundle:Bundle){
    Globals.global_lock.synchronized{
      music_track.foreach{
        case Left(x) => {x.stop();x.release()}
        case Right(_) => OpenSLESPlayer.slesStop()
      }
      music_track = None
      commonJobWhenFinished(!bundle.getBoolean("autoplay",false))
    }
    KarutaPlayUtils.doAfterSender(bundle)
  }

  def waitDecodeAndUpdateAudioQueue(){
    Globals.global_lock.synchronized{
      if(audio_queue.isEmpty){
        decode_task.get match{
          case Left(aq) => audio_queue ++= aq
          case Right(e) => throw e
        }
      }
    }
  }
  class OggDecodeTask extends AsyncTask[AnyRef,Void,Either[AudioQueue,Throwable]] {
    override def onPreExecute(){
      activity.runOnUiThread(new Runnable{
        override def run(){
          if(!activity.isFinishing){
            activity.showProgress
          }
        }
      })
    }
    override def onPostExecute(rval:Either[AudioQueue,Throwable]){
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
      // According to "Order of execution" in https://developer.android.com/reference/android/os/AsyncTask.html
      // > Starting with HONEYCOMB, tasks are executed on a single thread to avoid common application errors caused by parallel execution.
      // Therefore maybe we don't need lock here. However, the behavior of AsyncTask is little bit confusing, so using Scala's Futures and Promises may be better choice.
      // TODO: Using AsyncTask seems not so convenient. maybe we should use Scala's Futures and Promises instead.
      Globals.decode_lock.synchronized{
      try{
        val res_queue = new AudioQueue()
        if(maybe_reader.isEmpty){
          return Left(res_queue)
        }
        val reader = maybe_reader.get
        val span_simokami = (Utils.getPrefAs[Double]("wav_span_simokami", 1.0, 9999.0) * 1000).toInt
        cur_millisec = 0
        def addToAudioQueue(w:Either[WavBuffer,Int],is_cur:Boolean){
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
        // However, note that taking silence_time_{begin,end} too much can consume much more memory.
        val silence_time_begin = (Utils.getPrefAs[Double]("wav_begin_read", 0.5, 5.0)*1000.0).toInt
        val silence_time_end = (Utils.getPrefAs[Double]("wav_end_read", 0.2, 5.0)*1000.0).toInt
        addToAudioQueue(Right(silence_time_begin),true)
        // reuse decoded wav
        val decoded_wavs = new mutable.HashMap[(Int,Int),WavBuffer]()
        val ss = AudioHelper.genReadNumKamiSimoPairs(activity.getApplicationContext,cur_num,next_num)
        for (((read_num,kami_simo,is_cur),i) <-ss.zipWithIndex){
          if(isCancelled){
            // TODO: what should we return ?
            return Left(new AudioQueue())
          }
          if(reader.canRead(read_num,kami_simo)._1){
            try{
              val key = (read_num,kami_simo)
              if(decoded_wavs.contains(key)){
                addToAudioQueue(Left(decoded_wavs(key)),is_cur)
              }else{
                reader.withDecodedWav(read_num, kami_simo, wav => {
                   wav.trimFadeIn()
                   wav.trimFadeOut()
                   addToAudioQueue(Left(wav),is_cur)
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
                        val bundle = new Bundle
                        bundle.putString("tag","already_reported_expection")
                        bundle.putString("error_message",e.getMessage)
                        CommonDialog.messageDialogWithCallback(activity,Left(msg),bundle)
                      }
                  })
                  return Right(new OggDecodeFailException("IOException: "+e.getMessage))
                }else{
                  throw e
                }
              }
              case e:java.lang.UnsatisfiedLinkError => return Right(e)
            }
            if( i != ss.length - 1 ){
              addToAudioQueue(Right(span_simokami),is_cur)
            }
          }
        }
        addToAudioQueue(Right(silence_time_end),false)
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

