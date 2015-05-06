package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.AudioTrack
import _root_.android.content.{BroadcastReceiver,Context,Intent}
import _root_.android.app.{PendingIntent,AlarmManager}
import _root_.android.widget.Button
import _root_.android.os.{Bundle,Handler}
import _root_.android.net.Uri
import _root_.android.util.Log
import _root_.android.view.View

import _root_.java.nio.{ByteOrder,ShortBuffer,ByteBuffer}

import scala.collection.mutable

object AudioHelper{
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]
  val SHORT_SIZE = java.lang.Short.SIZE/java.lang.Byte.SIZE

  def calcTotalMillisec(audio_queue:AudioQueue):Long = {
    audio_queue.map{ arg =>{
      arg match {
        case Left(w) => w.audioLength()
        case Right(millisec) => millisec
        }
      }
    }.sum
  }

  def millisecToBufferSizeInBytes(decoder:OggVorbisDecoder,millisec:Int):Int = {
    // must be multiple of channels * sizeof(Short)
    (millisec * decoder.rate.toInt / 1000) * decoder.channels * SHORT_SIZE
  }
  def refreshKarutaPlayer(activity:WasuramotiActivity,old_player:Option[KarutaPlayer],force:Boolean):Option[KarutaPlayer] = {
    val app_context = activity.getApplicationContext()
    val maybe_reader = ReaderList.makeCurrentReader(app_context)
    if(maybe_reader.isEmpty){
      return None
    }
    val current_index = FudaListHelper.getCurrentIndex(app_context)
    val num = if(Utils.isRandom){
      val cur_num = Globals.player.map{_.next_num}.getOrElse(0)
      val next_num = FudaListHelper.queryRandom()
      Some((cur_num,next_num))
    }else{
      FudaListHelper.queryNext(current_index).map{
        case (cur_num,next_num,_,_) => (cur_num,next_num)
      }
    }
    num.flatMap{case(cur_num,next_num) =>{
        val num_changed = old_player.forall{ x => (x.cur_num, x.next_num) != ((cur_num, next_num)) }
        if(!maybe_reader.get.bothExists(cur_num,next_num)){
          None
        }else if(force || Globals.forceRefresh || num_changed){
          Globals.forceRefresh = false

          old_player.foreach{ p=>
            // mayInterruptIfRunning must be true to set Thread.currentThread.isInterrupted() == true for AsyncTask's thread
            p.decode_task.cancel(true)
            // TODO: do we have to wait for decode to finish since it is jni ?
            p.stop
          }

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
}

class WavBuffer(val buffer:ShortBuffer, val decoder:OggVorbisDecoder, val num:Int, val kamisimo:Int) extends WavBufferDebugTrait with BugReportable{
  val MAX_AMP = (1 << (decoder.bit_depth-1))
  var index_begin = 0
  var index_end = decoder.data_length

  override def toBugReport():String = {
    val bld = new mutable.StringBuilder
    bld ++= s"num->$num;"
    bld ++= s"kamisimo->$kamisimo;"
    bld ++= s"index_begin->$index_begin;"
    bld ++= s"index_end->$index_end;"
    val crc = shortBufferCRC(buffer)
    bld ++= s"buffer->capacity[${buffer.capacity}].crc[${crc}];"
    bld ++= s"channels->${decoder.channels};"
    bld ++= s"rate->${decoder.rate};"
    bld ++= s"bit_depth->${decoder.bit_depth};"
    bld ++= s"data_length->${decoder.data_length};"
    bld.toString
  }

  def shortBufferCRC(buffer:ShortBuffer):String = {
    val crc = new java.util.zip.CRC32()
    while(buffer.hasRemaining){
      val ss = new Array[Short](Math.min(1024,buffer.remaining))
      val bb = new Array[Byte](ss.size*2)
      buffer.get(ss)
      ByteBuffer.wrap(bb).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer.put(ss)
      crc.update(bb)
    }
    f"${crc.getValue}%08X"
  }

  // in milliseconds
  def audioLength():Long = {
    ((1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong)/decoder.channels
  }
  def bufferSizeInBytes():Int = {
    AudioHelper.SHORT_SIZE * (index_end - index_begin)
  }

  def boundIndex(x:Int) = {
    math.min(math.max(0,x),buffer.limit)
  }

  def writeToAudioTrack(track:AudioTrack){
    // Since using ShortBuffer.array() throws UnsupportedOperationException (maybe because we are using FileChannel.map() ?),
    // we use ShortBuffer.get() instead.

    // Some device raises java.lang.IllegalArgumentException in java.nio.Buffer.position().
    // The Exception should not be raised if there's no bug in previous procedure such as fade().
    // However I could not find the bug. Therefore I prevent it by checking the index bound here.
    // TODO: fix the bug in fade() or some other function which causes IllegalArgumentException here.
    val ib = boundIndex(index_begin)
    val ie = boundIndex(index_end)

    val b_size = ie - ib
    val b = new Array[Short](b_size)

    buffer.position(ib)
    buffer.get(b,0,b_size)
    buffer.rewind()
    track.write(b,0,b_size)
  }
  def writeToShortBuffer(dst:Array[Short], offset:Int):Int = {
    // See writeToAudioTrack() why we apply boundIndex()
    val ib = boundIndex(index_begin)
    val ie = boundIndex(index_end)
    val b_base = ie - ib
    val b_size = math.min( b_base, dst.length-offset )
    buffer.position(ib)
    buffer.get(dst, offset, b_size)
    buffer.rewind()
    // TODO: which should we return: b_base or b_size ?
    return(b_size)
  }
  def thresholdIndex(threshold:Double, fromEnd:Boolean):Int = {
    val threshold_amp = (MAX_AMP *
      (if(threshold > 0.0001){ threshold }else{ 0 })
     ).toShort
    var (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    bg = indexInBuffer(bg)
    ed = indexInBuffer(ed)
    try{
      for( i <- bg to (ed,step) ){
        // TODO: check by block of signal instead of single signal
        if( math.abs(buffer.get(i)) > threshold_amp ){
          return i
        }
      }
    }catch{
      // These exceptions should not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case _:IndexOutOfBoundsException => return(bg)
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

  // index must be multiple of decoder.channels
  def fitIndex(i:Int):Int = {
    (i / decoder.channels) * decoder.channels
  }

  // if begin < end then fade-in else fade-out
  // center_amp must be between 0.0 and 1.0
  // TODO: strictly speaking, this fade does not fade stereo wav correctly since left and right amplitudes are amplified differently.
  def fade(i_begin:Int, i_end:Int, center_amp:Double = 0.5){
    val begin = indexInBuffer(i_begin)
    val end = indexInBuffer(i_end)
    val width = math.abs(begin - end)
    if(width < secToIndex(0.01) ){
      return
    }
    val step = if( begin < end ){ 1 } else { -1 }
    try{
      for( i <- begin to (end,step) ){
        val x = (math.abs(i - begin).toDouble)/width.toDouble
        val amp = if(x <= 0.5){
          center_amp * x * 2.0
        }else{
          (1.0 - center_amp) * 2.0 * x + (2.0 * center_amp - 1.0)
        }
        buffer.put(i,(buffer.get(i)*amp).toShort)
      }
    }catch{
      // These exceptions should not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case _:IndexOutOfBoundsException => None
    }
  }

  def secToIndex(sec:Double):Int = {
    (sec * decoder.rate).toInt * decoder.channels
  }

  // fadein
  def trimFadeIn(){
    val threshold = Utils.getPrefAs[Double]("wav_threshold", 0.003, 1.0)
    val fadelen = secToIndex(Utils.getPrefAs[Double]("wav_fadein_kami", 0.1, 10.0))
    val beg = fitIndex(thresholdIndex(threshold,false))
    val fadebegin = Math.max( beg - fadelen, 0)
    // Log.v("wasuramoti_fadein", s"num=${num}, kamisimo=${kamisimo}, fadebegin=${fadebegin}, beg=${beg}, len=${beg-fadebegin}")
    fade(fadebegin, beg, 0.75)
    index_begin = fitIndex(fadebegin)
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_begin >= index_end){
      index_begin = index_end - decoder.channels
    }
  }
  // fadeout
  def trimFadeOut(){
    val threshold = Utils.getPrefAs[Double]("wav_threshold", 0.003, 1.0)
    val fadelen = secToIndex(Utils.getPrefAs[Double]("wav_fadeout_simo", 0.2, 10.0))
    val end = fitIndex(thresholdIndex(threshold,true))
    val fadeend = Math.max( end - fadelen, 0)
    // Log.v("wasuramoti_fadeout", s"num=${num}, kamisimo=${kamisimo}, fadeend=${decoder.data_length-fadeend}, end=${decoder.data_length-end}, len=${end-fadeend}")
    fade(end,fadeend)
    index_end = fitIndex(end)
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_end <= index_begin){
      index_end = index_begin + decoder.channels
    }
  }
}

trait WavBufferDebugTrait{
  self:WavBuffer =>
  def checkSum():String = {
    if(Globals.IS_DEBUG){
      var (bg,ed) = (index_begin,index_end-1)
      bg = indexInBuffer(bg)
      ed = indexInBuffer(ed)
      // sampling in every 128 elems
      val s = (for( i <- bg to (ed,128) )yield buffer.get(i)).sum
      audioLength().toString + ":" + ("%04X".format(s))
    }else{
      ""
    }
  }
}

object KarutaPlayUtils{
  object Action extends Enumeration{
    type Action = Value
    val Auto,Start,Border,End,WakeUp1,WakeUp2,WakeUp3 = Value
  }
  // I could not figure out why, but if we call Bundle.putSerializable to Enumeration,
  // it returns null when getting it by getSerializable. Therefore we use String instead.
  // In the previous version of Scala, there was no problem, so what's wrong?
  // TODO: Why we cannot use Enumaration here
  val SENDER_MAIN = "SENDER_MAIN"
  val SENDER_CONF = "SENDER_CONF"

  val karuta_play_schema = "wasuramoti://karuta_play/"
  def getPendingIntent(context:Context,action:Action.Action,task:Intent=>Unit={_=>Unit}):PendingIntent = {
    val intent = new Intent(context, classOf[KarutaPlayReceiver])
    val id = action.id + 1
    intent.setAction(action.toString)
    intent.setData(Uri.parse(karuta_play_schema+action.toString))
    task(intent)
    PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_CANCEL_CURRENT)
  }

  def cancelKarutaPlayTimer(context:Context,action:Action.Action){
    val alarm_manager = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    if(alarm_manager == null){
      return
    }
    val pendingIntent = getPendingIntent(context,action)
    alarm_manager.cancel(pendingIntent)
  }

  def startKarutaPlayTimer(context:Context,action:Action.Action,millisec:Long,task:Intent=>Unit={_=>Unit}){
    val alarm_manager = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    if(alarm_manager == null){
      Log.v("wasuramoti","WARNING: ALARM_SERVICE is not supported on this device.")
      return
    }
    val pendingIntent = getPendingIntent(context,action,task)
    val limit_millis = System.currentTimeMillis + millisec
    Utils.alarmManagerSetExact(alarm_manager, AlarmManager.RTC_WAKEUP, limit_millis, pendingIntent)
  }
  def setAudioPlayButton(view:View,context:Context,before_play:Option[KarutaPlayer=>Unit]=None){
    val btn = view.findViewById(R.id.audio_play).asInstanceOf[Button]
    val handler = new Handler()
    btn.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        Globals.global_lock.synchronized{
          Globals.player match{
            case Some(pl) => {
              if(Globals.is_playing){
                pl.stop()
                btn.setText(context.getResources().getString(R.string.audio_play))
              }else{
                before_play.foreach(_(pl))
                val bundle = new Bundle()
                bundle.putString("fromSender",KarutaPlayUtils.SENDER_CONF)
                pl.play(bundle)
                btn.setText(context.getResources().getString(R.string.audio_stop))
              }
            }
            case None =>
              handler.post(new Runnable(){
                override def run(){
                  Utils.messageDialog(context,Right(R.string.player_error_noplay))
                }
              })
          }
        }
      }
    })
  }
  def doAfterDone(bundle:Bundle){
    bundle.getString("fromSender") match{
      case SENDER_MAIN =>
        doAfterActivity(bundle)
      case SENDER_CONF =>
        doAfterConfiguration(bundle)
    }
  }
  def doAfterActivity(bundle:Bundle){
    Globals.global_lock.synchronized{
      if(Globals.player.isEmpty){
        return
      }
      val activity = Globals.player.get.activity
      val auto = Globals.prefs.get.getBoolean("autoplay_enable",false)
      if(auto || Globals.prefs.get.getBoolean("move_next_after_done",true)){
        activity.moveToNextFuda(!auto,auto)
      }else{
        activity.refreshAndInvalidate()
      }
      if(auto && Globals.player.isEmpty && Globals.prefs.get.getBoolean("autoplay_repeat",false) &&
        FudaListHelper.allReadDone(activity.getApplicationContext())
      ){
        FudaListHelper.shuffle()
        FudaListHelper.moveToFirst(activity.getApplicationContext())
        activity.refreshAndInvalidate(auto)
      }
      if(auto && !Globals.player.isEmpty){
        KarutaPlayUtils.startKarutaPlayTimer(
          activity.getApplicationContext,
          KarutaPlayUtils.Action.Auto,
          Globals.prefs.get.getLong("autoplay_span", 5)*1000
        )
      }
    }
  }
  def doAfterConfiguration(bundle:Bundle){
    Globals.current_config_dialog.foreach{dp=>
      val btn = dp.getDialog.findViewById(R.id.audio_play).asInstanceOf[Button]
      if(btn != null){
        Globals.player.foreach{p=>
          val context = p.activity.getApplicationContext
          btn.setText(context.getResources.getString(R.string.audio_play))
        }
      }
    }
  }
}

// Canonical way to keep CPU awake on auto play, in battery mode, is adding WAKE_LOCK permission and
// to use WakefulBroadcastReceiver or PowerManager.
// see:
//   http://stackoverflow.com/questions/8713361/keep-a-service-running-even-when-phone-is-asleep
//
// However, we want to avoid add new permission to the utmost. So we will try a little bit tricky hack as follows.
// As for current (Android 2.3 .. 5.0) android implementation, It seems that CPU goes into sleep after end of onReceive() function.
// However KarutaPlayer.onReallyStart() creates a new thread (in order to avoid ANR Timeout), and returns immediately before calling next timer.
// So we will try to wake up CPU using AlarmManager.setExact(RTC_WAKEUP,...) after this function ends.
// This method works quite well in all of my devices including Nexus 7, Kindle Fire, and so on.
class KarutaPlayReceiver extends BroadcastReceiver {
  import KarutaPlayUtils.Action._
  override def onReceive(context:Context, intent:Intent){
    withName(intent.getAction) match{
      case Auto =>
        Globals.player.foreach{_.activity.doPlay(auto_play=true)}
      case Start =>
        val bundle = intent.getParcelableExtra("bundle").asInstanceOf[Bundle]
        // try to wake up CPU three times, also this serves as text audio consistency check
        if( (bundle != null && bundle.getBoolean("auto_play",false)) || YomiInfoUtils.showPoemText ){
          for((t,a) <- Array((800,WakeUp1),(1600,WakeUp2),(2400,WakeUp3))){
            KarutaPlayUtils.startKarutaPlayTimer(context,a,t)
          }
        }
        Globals.player.foreach{_.onReallyStart(bundle)}
      case Border =>
        Globals.player.foreach{_.doWhenBorder()}
      case End =>
        val bundle = intent.getParcelableExtra("bundle").asInstanceOf[Bundle]
        Globals.player.foreach{_.doWhenDone(bundle)}
      case WakeUp1 | WakeUp2 | WakeUp3 =>
        Globals.player.foreach{_.activity.checkConsistencyBetweenPoemTextAndAudio()}
    }
  }
}
