package karuta.hpnpwd.wasuramoti

import android.media.AudioManager
import android.annotation.TargetApi
import android.content.{BroadcastReceiver,Context,Intent}
import android.app.{PendingIntent,AlarmManager}
import android.widget.{Button,Toast}
import android.os.{Bundle,Handler}
import android.net.Uri
import android.util.Log
import android.view.View

import scala.collection.mutable
object KarutaPlayUtils{
  var audio_focus = None:Option[AudioManager.OnAudioFocusChangeListener]
  var have_to_mute = false:Boolean
  var last_confirmed_for_volume = None:Option[Long]
  var last_confirmed_for_ringer_mode = None:Option[Long]
  var replay_audio_queue = None:Option[AudioHelper.AudioQueue]

  object Action extends Enumeration{
    type Action = Value
    // TODO: automatically create WakeUp{1..6}
    val Auto,End,WakeUp1,WakeUp2,WakeUp3,WakeUp4,WakeUp5,WakeUp6 = Value
  }
  // I could not figure out why, but if we call Bundle.putSerializable to Enumeration,
  // it returns null when getting it by getSerializable. Therefore we use String instead.
  // In the previous version of Scala, there was no problem, so what's wrong?
  // TODO: Why we cannot use Enumaration here
  val SENDER_MAIN = "SENDER_MAIN"
  val SENDER_CONF = "SENDER_CONF"
  val SENDER_REPLAY = "SENDER_REPLAY"

  // TODO: shold we use single handler and multiple callbacks?
  val border_handler = new Handler() 
  val check_consistency_handler = new Handler()

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

  def startReplay(activity:WasuramotiActivity){
    if(Globals.player.isEmpty){
      Globals.player = Some(new KarutaPlayer(activity,null,0,0))
    }
    Globals.player.foreach{ pl =>
      KarutaPlayUtils.replay_audio_queue.foreach{ raq =>
        val bundle = new Bundle()
        bundle.putString("fromSender",KarutaPlayUtils.SENDER_REPLAY)
        pl.play(bundle)
      }
    }
  }

  def cancelAutoPlay(context:Context){
    cancelKarutaPlayTimer(context,Action.Auto)
  }

  def cancelAllPlay(context:Context){
    cancelAutoPlay(context)
    Globals.player.foreach(_.stop())
    cancelWakeUpTimers(context)
  }

  def startBorderTimer(delay:Long){
    border_handler.postDelayed(new Runnable(){
      override def run(){
        Globals.player.foreach{_.doWhenBorder()}
      }
    },delay)
  }

  def cancelBorderTimer(){
    border_handler.removeCallbacksAndMessages(null)
  }

  def startCheckConsistencyTimers(){
    for(delay <- Array(600,1200,1800,2400)){
      check_consistency_handler.postDelayed(new Runnable(){
        override def run(){
          Globals.player.foreach{_.activity.checkConsistencyBetweenPoemTextAndAudio()}
        }
      },delay)
    }
  }

  def cancelCheckConsistencyTimers(){
    check_consistency_handler.removeCallbacksAndMessages(null)
  }

  var wake_up_timer_group_switch = true

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
  def startWakeUpTimers(context:Context,play_end_time:Long){
    // If we call startKarutaPlayTimer with same action id, the previous timer will be overwrited.
    // We need to run maximum of two groups of timers simultaneously because this timer is for NEXT play
    val actions = if(wake_up_timer_group_switch){
      Array(
        Action.WakeUp1,
        Action.WakeUp2,
        Action.WakeUp3
        )
    }else{
      Array(
        Action.WakeUp4,
        Action.WakeUp5,
        Action.WakeUp6
        )
    }
    val auto_delay = Globals.prefs.get.getLong("autoplay_span", 5)*1000
    for((act,t) <- actions.zip(Array(800,1600,2400))){
      val delay = play_end_time + auto_delay + t
      startKarutaPlayTimer(context,act,delay)
    }
    wake_up_timer_group_switch ^= true
  }
  def cancelWakeUpTimers(context:Context){
    for(action <- Array(
      Action.WakeUp1,
      Action.WakeUp2,
      Action.WakeUp3,
      Action.WakeUp4,
      Action.WakeUp5,
      Action.WakeUp6
    )
    ){
      cancelKarutaPlayTimer(context,action)
    }
  }

  def doAfterSenderMain(bundle:Bundle){
    Globals.global_lock.synchronized{
      if(Globals.player.isEmpty){
        return
      }
      val activity = Globals.player.get.activity
      val context = activity.getApplicationContext
      val auto = bundle.getBoolean("autoplay",false)
      if(auto || Globals.prefs.get.getBoolean("move_next_after_done",true)){
        activity.moveToNextFuda(!auto,auto)
      }else{
        activity.refreshAndInvalidate()
      }
      if(auto && Globals.player.isEmpty){
        if(Globals.prefs.get.getBoolean("autoplay_repeat",false) && FudaListHelper.allReadDone(context)){
          FudaListHelper.shuffle(context)
          FudaListHelper.moveToFirst(context)
          activity.refreshAndInvalidate(auto)
        }else{
          KarutaPlayUtils.abandonAudioFocus(context)
        }
      }
    }
  }
  
  def doAfterSenderConf(bundle:Bundle){
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

  def doAfterSenderReplay(bundle:Bundle){
    Globals.player.foreach{ pl =>
      pl.activity.refreshAndInvalidate()
    }
  }

  def doAfterSender(bundle:Bundle){
    bundle.getString("fromSender") match{
      case SENDER_MAIN =>
        doAfterSenderMain(bundle)
      case KarutaPlayUtils.SENDER_CONF =>
        doAfterSenderConf(bundle)
      case KarutaPlayUtils.SENDER_REPLAY =>
        doAfterSenderReplay(bundle)
    }
  }

  @TargetApi(8) // Audio Focus requires API >= 8
  def requestAudioFocus(context:Context):Boolean = {
    if(! Globals.prefs.get.getBoolean("use_audio_focus",true) || android.os.Build.VERSION.SDK_INT < 8){
      return true
    }
    abandonAudioFocus(context)
    val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    if(am != null){
      val af = new AudioManager.OnAudioFocusChangeListener(){
        override def onAudioFocusChange(focusChange:Int){
          import AudioManager._
          focusChange match {
            case AUDIOFOCUS_GAIN =>
              recoverVolume(context)
            case AUDIOFOCUS_LOSS =>
              cancelAllPlay(context)
              Toast.makeText(context,R.string.stopped_since_audio_focus,Toast.LENGTH_SHORT).show()
            case AUDIOFOCUS_LOSS_TRANSIENT | AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK =>
              // The recommended behavior of AUDIOFOCUS_LOSS_TRANSIENT event is to pause the audio, and resume in next AUDIOFOCUS_GAIN event.
              // As for AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK event, we should lower the volume, and recover it in next AUDIOFOCUS_GAIN event.
              // However, current KarutaPlayer does not support pause/resume because of AudioTrack's bug (re-using AudioTrack is completely broken!).
              // So we just lower the volume in both cases.
              // TODO: pause/resume when AUDIOFOCUS_LOSS_TRANSIENT/AUDIOFOCUS_GAIN
              // See following link:
              //  http://developer.android.com/training/managing-audio/audio-focus.html
              //  https://code.google.com/p/android/issues/detail?id=155984
              //  https://code.google.com/p/android/issues/detail?id=17995
              lowerVolume(context)
            case _ =>
          }
        }
      }
      val res = am.requestAudioFocus(af,Utils.getAudioStreamType,AudioManager.AUDIOFOCUS_GAIN)
      if(res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
        audio_focus = Some(af)
        return true
      }else{
        return false
      }
    }
    return true
  }

  def abandonAudioFocus(context:Context){
    audio_focus.foreach{af =>
      if(android.os.Build.VERSION.SDK_INT >= 8){
        val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
        if(am != null){
          am.abandonAudioFocus(af)
          audio_focus = None
        }
      } 
    }
  }
  def lowerVolume(context:Context){
    have_to_mute = true
    Globals.player.foreach{
      _.music_track.foreach{Utils.setVolumeMute(_,true)}
    }
    Utils.setButtonTextByState(context)
  }

  def recoverVolume(context:Context){
    have_to_mute = false
    Globals.player.foreach{
      _.music_track.foreach{Utils.setVolumeMute(_,false)}
    }
    Utils.setButtonTextByState(context)
  }


  val CONFIRM_THRESHOLD_TIME = 20*60*1000 // 20 minutes
  def elapsedEnoghSinceLastConfirm(cur_time:Long,prev_time:Option[Long]):Boolean = {
    prev_time.forall{
      cur_time - _ > CONFIRM_THRESHOLD_TIME
    }
  }

  def setReplayButtonEnabled(activity:WasuramotiActivity,force:Option[Boolean]=None){
    val btn = Option(activity.findViewById(R.id.replay_last_button))
    .orElse(
      Option(activity.findViewById(R.id.yomi_info_search_fragment)).flatMap( x=>
        Option(x.findViewWithTag(YomiInfoSearchDialog.PREFIX_REPLAY+"_LAST"))
      ))
    btn.foreach{ b =>
      activity.runOnUiThread(new Runnable(){
        override def run(){
          b.setEnabled(force.getOrElse(replay_audio_queue.nonEmpty))
        }
      })
    }
  }
}

class KarutaPlayReceiver extends BroadcastReceiver {
  import KarutaPlayUtils.Action._
  override def onReceive(context:Context, intent:Intent){
    withName(intent.getAction) match{
      case Auto =>
        Globals.player.foreach{_.activity.doPlay(auto_play=true)}
      case End =>
        val bundle = intent.getParcelableExtra("bundle").asInstanceOf[Bundle]
        Globals.player.foreach{_.doWhenDone(bundle)}
      case WakeUp1 | WakeUp2 | WakeUp3 | WakeUp4 | WakeUp5 | WakeUp6 =>
        // Do nothing, just wake up device
    }
  }
}
