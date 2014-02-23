package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.view.{Gravity,View,WindowManager}
import _root_.android.os.{AsyncTask,Handler}
import _root_.android.app.ProgressDialog
import _root_.android.media.audiofx.Equalizer
import _root_.java.util.{Timer,TimerTask}

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
                          throw new Exception("checksum mismatch for " + key + ": " + x + " != " + cs)
                        }
      }
    }
  }
}

class KarutaPlayer(var activity:WasuramotiActivity,val reader:Reader,val cur_num:Int,val next_num:Int){
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]
  var cur_millisec = 0:Long
  var read_both = false
  var audio_thread = None:Option[Thread]
  var timer_start = None:Option[Timer]
  var timer_onend = None:Option[Timer]
  var timer_curend = None:Option[Timer]
  var audio_track = None:Option[AudioTrack]
  var equalizer = None:Option[Equalizer]
  var equalizer_seq = None:Option[Utils.EqualizerSeq]
  var old_orientation = None:Option[Int]
  var set_audio_volume = true

  val audio_queue = new AudioQueue() // file or silence in millisec
  // Executing SQLite query in doInBackground causes `java.lang.IllegalStateException: Cannot perform this operation because the connection pool has been closed'
  // Therefore, we execute it here
  val is_last_fuda = FudaListHelper.isLastFuda(activity.getApplicationContext())
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
            case _:Exception => Unit
          }
        }
      })
    }catch{
      // Equalizer is only supported in Android 2.3+(API Level 9)
      case e:NoClassDefFoundError => None
    }
  }
  def play(onPlayEnd:Unit=>Unit=identity[Unit],onCurEnd:Option[Unit=>Unit]=None){
    Globals.global_lock.synchronized{
      if(Globals.is_playing){
        return
      }
      if(set_audio_volume){
        Utils.saveAndSetAudioVolume(activity.getApplicationContext())
      }
      Globals.is_playing = true
      // prevent screen rotation during play since it restarts activity
      activity.lockOrientation(true)

      Utils.setButtonTextByState(activity.getApplicationContext())
      timer_start = Some(new Timer())
      // Since we insert some silence at beginning of audio,
      // the actual wait_time should be shorter.
      val wait_time = Math.max(100,Utils.getPrefAs[Double]("wav_begin_read", 0.5, 9999.0)*1000.0 - Globals.HEAD_SILENCE_LENGTH)
      if(Utils.showYomiInfo){
        (new Handler()).post(new Runnable(){
            override def run(){
              if(Utils.readCurNext){
                activity.invalidateYomiInfo()
              }else{
                activity.scrollYomiInfo(R.id.yomi_info_view_next,false)
              }
            }
        })
      }
      timer_start.get.schedule(new TimerTask(){
        override def run(){
          onReallyStart(onPlayEnd,onCurEnd)
          timer_start.foreach(_.cancel())
          timer_start = None
        }},wait_time.toLong)
    }
  }

  def getFirstDecoder():OggVorbisDecoder = {
    waitDecode()
    audio_queue.find{_.isLeft}.get match{case Left(w)=>w.decoder}
  }

  def doWhenStop(){
    equalizer.foreach(_.release())
    equalizer = None
    Globals.is_playing = false
    activity.lockOrientation(false)
    if(set_audio_volume){
      Utils.restoreAudioVolume(activity.getApplicationContext())
    }
  }

  def onReallyStart(onPlayEnd:Unit=>Unit,onCurEnd:Option[Unit=>Unit]){
    Globals.global_lock.synchronized{
      onCurEnd.foreach{hook =>
        timer_curend = Some(new Timer())
        val t = Math.max(10,cur_millisec-900) // begin 900ms earlier
        timer_curend.get.schedule(new TimerTask(){
            override def run(){
              hook()
            }
        },t)
      }
      val do_when_done = { _:Unit => {
        Globals.global_lock.synchronized{
          audio_track.foreach(x => {x.stop();x.release()})
          audio_track = None
          doWhenStop()
          onPlayEnd()
        }
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
        // This method requires small memory, but there is possibility of noise at few seconds after writeToAudioTrack.
        // I could not confirm such noise in my device, but some users claim that they have a noise in the beginning of upper poem.

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
        // This method requires some memory overhead, but able to reduce possibility of noise since it only writes to AudioTrack once.

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
        audio_track.get.write(buf,0,buf.length)
        timer_onend = Some(new Timer())
        timer_onend.get.schedule(new TimerTask(){
          override def run(){
            do_when_done()
          }},buffer_length_millisec+200) // +200 is just for sure that audio is finished playing
        audio_track.get.play()
      }
    }
  }

  def stop(){
    Globals.global_lock.synchronized{
      timer_curend.foreach(_.cancel())
      timer_curend = None
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
          // Before calling AudioTrack.release(), we have to wait for audio_thread to end.
          // The reason why I have to do this is assumed that
          // calling AudioTrack.stop() does not immediately terminate AudioTrack.write(), and
          // calling AudioTrack.release() before the termination is illegal.
          audio_thread.foreach{_.join()}
          track.release()
        })
      audio_thread = None
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
    var progress = None:Option[ProgressDialog]
    override def onPreExecute(){
      activity.runOnUiThread(new Runnable{
        override def run(){

          //We have to check whether activity is in finishing phase or not to avoid the following error:
          //android.view.WindowManager$BadTokenException: Unable to add window -- token android.os.BinderProxy@XXXXXXXX is not valid; is your activity running?
          if(!activity.isFinishing){
            val dlg = new ProgressDialog(activity)
            dlg.setMessage(activity.getApplicationContext.getResources.getString(R.string.now_decoding))
            val w = dlg.getWindow
            w.setGravity(Gravity.BOTTOM)
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dlg.show
            progress = Some(dlg)
          }
        }
      })
    }
    override def onPostExecute(rval:Either[AudioQueue,Exception]){
      activity.runOnUiThread(new Runnable{
        override def run(){
          progress.foreach(_.dismiss())
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
        read_both = false
        def add_to_audio_queue(w:Either[WavBuffer,Int],is_cur:Boolean){
          res_queue.enqueue(w)
          val alen = w match{
            case Left(wav) => wav.audioLength
            case Right(len) => len
          }
          if(is_cur){
            cur_millisec += alen
          }
          if(w.isLeft && is_cur){
            read_both = true
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
        if(is_last_fuda && !read_order_each.endsWith("NEXT2")){
          ss ++= Array("NEXT2")
        }
        for (i <- 0 until ss.length ){
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
                activity.runOnUiThread(new Runnable{
                    override def run(){
                      val msg = activity.getResources.getString(R.string.error_ioerror,e.getMessage)
                      Utils.messageDialog(activity,Left(msg),{_=>throw new AlreadyReportedException(e.getMessage)})
                    }
                })
                return Right(new OggDecodeFailException("ogg decode failed with IOException:"+e.getMessage))
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

