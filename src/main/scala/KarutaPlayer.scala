package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.os.{AsyncTask,Bundle}
import _root_.android.media.audiofx.Equalizer
import _root_.android.widget.{Toast,CheckBox,CompoundButton}
import _root_.android.util.Log
import _root_.android.app.AlertDialog
import _root_.android.content.DialogInterface
import _root_.android.view.LayoutInflater

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

class KarutaPlayer(var activity:WasuramotiActivity,val reader:Reader,val cur_num:Int,val next_num:Int) extends BugReportable{
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
  val decode_task = (new OggDecodeTask().execute(new AnyRef())).asInstanceOf[OggDecodeTask]

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
    bld ++= s"is_last_fuda:${is_last_fuda},"
    val audio_queue_info = audioQueueInfo()
    bld ++= s"audio_queue:${audio_queue_info}"
    bld.toString
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
            var r = ar.seq(i).getOrElse(0.5f)
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
      audio_track.foreach(x => {x.stop();x.release()})
      audio_track = None
      doWhenStop()
      KarutaPlayUtils.doAfterDone(bundle)
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
                      ()=>Utils.restartApplication(activity.getApplicationContext)
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

      val decode_and_play_again = () => {
        // Since refreshKarutaPlayer is synchronized in global_lock, we should do it in another thread
        audio_track = None
        activity.runOnUiThread(new Runnable(){
            override def run(){
              Globals.player = AudioHelper.refreshKarutaPlayer(activity,Globals.player,true)
              // we won't wait playing for second time
              bundle.remove("wait_time")
              Globals.player.foreach{_.play(bundle)}
            }
        })
      }

      // There was a bug report that inconsistency between audio and text occurs.
      // I could not found any test case that reproduces the inconsistency. 
      // It might be just misconception of the user, but I would check consistency for sure.
      // TODO: can we safely assume that .distinct() returns the list in original order ?
      val read_nums = audio_queue.collect{ case Left(w) => Some(w.num) }.distinct.toList
      if(!activity.checkConsintencyForYomiInfoAndAudioQueue(read_nums)){
        val INCONSISTENCY_THRESHOLD = new java.lang.Integer(5)
        val fallback_to_dialog = (Globals.text_audio_inconsistent_count >= INCONSISTENCY_THRESHOLD)
        val err_msg = activity.getApplicationContext.getResources.getString(R.string.text_audio_consistency_error);
        activity.runOnUiThread(new Runnable(){
          override def run(){
            if(fallback_to_dialog){
              val on_yes = () => {
                Utils.showBugReport(activity,err_msg)
              }
              val custom = (builder:AlertDialog.Builder) => {
                val checkbox = new CheckBox(activity)
                val choice = activity.getApplicationContext.getResources.getString(R.string.never_show_again)
                checkbox.setText(choice)
                checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                  override def onCheckedChanged(buttonView:CompoundButton, isChecked:Boolean){
                    val edit = Globals.prefs.get.edit
                    edit.putBoolean("show_text_audio_consintency_dialog", !isChecked)
                    edit.commit()
                  }
                })
                builder.setView(checkbox)
              }
              if(Globals.prefs.get.getBoolean("show_text_audio_consintency_dialog",true)){
                val dlg_txt = activity.getApplicationContext.getResources.getString(R.string.internal_error_dialog, INCONSISTENCY_THRESHOLD)
                Utils.confirmDialog(activity,Left(dlg_txt),on_yes,custom=custom)
              }
              Globals.text_audio_inconsistent_count = 0
            }else{
              if(Globals.prefs.get.getBoolean("show_text_audio_consintency_dialog",true)){
                Toast.makeText(activity.getApplicationContext,err_msg,Toast.LENGTH_LONG).show()
              }
              Globals.text_audio_inconsistent_count += 1
            }
          }
        })
        if(!fallback_to_dialog){
          decode_and_play_again()
          return
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
            decode_and_play_again()
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
    // the signature of doInBackground must be `java.lang.Object doInBackground(java.lang.Object[])`. check in javap command.
    // otherwise it raises AbstractMethodError "abstract method not implemented"
    override def doInBackground(unused:AnyRef*):AnyRef = {
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
        // Additionally, playing silence as wave file can avoid some wierd effect        // which occurs at beginning of wav when using bluetooth speaker.
        val (silence_time,_) = Utils.calcSilenceAndWaitLength
        add_to_audio_queue(Right(silence_time),true)
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

