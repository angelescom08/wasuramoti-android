package karuta.hpnpwd.wasuramoti

import android.media.AudioManager
import android.annotation.TargetApi
import android.content.{BroadcastReceiver,Context,Intent}
import android.app.{PendingIntent,AlarmManager}
import android.widget.{Button,Toast}
import android.os.{Bundle,Handler,PowerManager,SystemClock}
import android.net.Uri
import android.util.Log
import android.view.View

import scala.collection.mutable
object KarutaPlayUtils{
  var audio_focus = None:Option[AudioManager.OnAudioFocusChangeListener]
  var wake_lock = None:Option[PowerManager#WakeLock]
  var have_to_mute = false:Boolean
  var last_confirmed_for_volume = None:Option[Long]
  var last_confirmed_for_ringer_mode = None:Option[Long]
  var replay_audio_queue = None:Option[AudioHelper.AudioQueue]

  // I could not figure out why, but if we call Bundle.putSerializable to Enumeration,
  // it returns null when getting it by getSerializable. Therefore we use String instead.
  // In the previous version of Scala, there was no problem, so what's wrong?
  // TODO: Why we cannot use Enumaration here
  val SENDER_MAIN = "SENDER_MAIN"
  val SENDER_CONF = "SENDER_CONF"
  val SENDER_REPLAY = "SENDER_REPLAY"

  // TODO: Handler runs on thread that is created. does these really created on UI thread, or shuld we create on Activity.onCreate ?
  val border_handler = new Handler() 
  val end_handler = new Handler()
  val auto_handler = new Handler()
  val check_consistency_handler = new Handler()
  val wake_lock_handler = new Handler()

  val karuta_play_schema = "wasuramoti://karuta_play/"

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

  def cancelAllPlay(){
    cancelAutoTimer()
    Globals.player.foreach(_.stop())
  }

  def startBorderTimer(delay:Long){
    cancelBorderTimer()
    border_handler.postDelayed(new Runnable(){
      override def run(){
        Globals.player.foreach{_.doWhenBorder()}
      }
    },delay)
  }

  def cancelBorderTimer(){
    border_handler.removeCallbacksAndMessages(null)
  }

  def startEndTimer(delay:Long,bundle:Bundle){
    cancelEndTimer()
    end_handler.postDelayed(new Runnable(){
      override def run(){
        Globals.player.foreach{_.doWhenDone(bundle)}
      }
    },delay)
  }

  def cancelEndTimer(){
    end_handler.removeCallbacksAndMessages(null)
  }

  def startAutoTimer(delay:Long){
    cancelAutoTimer()
    auto_handler.postDelayed(new Runnable(){
      override def run(){
        Globals.player.foreach{_.activity.doPlay(auto_play=true)}
      }
    },delay)
  }

  def cancelAutoTimer(){
    auto_handler.removeCallbacksAndMessages(null)
  }

  def haveToFullyWakeLock():Boolean = {
    // Since Android >= 4.0.3 (not sure), AudioTrack and OpenSLES holds PARTIAL_WAKE_LOCK via AudioFlinger service.
    // So we don't have to hold our own wake lock when audio is playing.
    // You can confirm it in
    //   $ adb shell dumpsys power
    //   $ adb shell dumpsys media.audio_flinger
    android.os.Build.VERSION.SDK_INT < 15
  }

  def startWakeLockTimer(context:Context,delay:Long,timeout:Long){
    cancelWakeLockTimer()
    wake_lock_handler.postDelayed(new Runnable(){
      override def run(){
        acquireWakeLock(context,timeout)
      }
    },delay)
  }
  def cancelWakeLockTimer(){
    wake_lock_handler.removeCallbacksAndMessages(null)
  }

  def startCheckConsistencyTimers(){
    cancelCheckConsistencyTimers()
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

  def releaseResourcesHeldDuringAutoPlay(context:Context){
    abandonAudioFocus(context)
    cancelWakeLockTimer()
    releaseWakeLock()
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
          releaseResourcesHeldDuringAutoPlay(context)
        }
      }
      if(auto && !Globals.player.isEmpty){
        val auto_delay = Globals.prefs.get.getLong("autoplay_span", 3)*1000
        startAutoTimer(auto_delay)
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
              cancelAllPlay()
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

  def acquireWakeLock(context:Context,timeout:Long){
    val pm = context.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    if(pm == null){
      return
    }
    if(wake_lock.isEmpty){
      wake_lock = Some{
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"karuta.hpnpwd.wasuramoti.KarutaPlayUtils")
        lock.setReferenceCounted(false)
        lock
      }
    }
    if(android.os.Build.VERSION.SDK_INT >= 15){
      // timeout is just for sure in case that wake lock is not correctly released at audio stop.
      wake_lock.get.acquire(timeout)
    }else{
      // Android < 4.0.1 has a bug that previous timeout releaser would not be canceled in next acquire,
      // Therefore we won't use PowerManager#acquire(timeout) for those versions of android.
      // It won't be a matter as far as wake lock is correctly released at audio stop.
      // The bug was fixed in:
      //   https://android.googlesource.com/platform/frameworks/base/+/d7350e3a56daa44e2d2c6e5175e6430492cf0dc9
      wake_lock.get.acquire()
    }
  }

  def releaseWakeLock(){
    wake_lock.foreach{ lock =>
      if(lock.isHeld){
        lock.release()
      }
    }
    wake_lock = None
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

