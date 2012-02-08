package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,Notification,AlarmManager,PendingIntent,NotificationManager}
import _root_.android.os.Bundle 
import _root_.android.view.View
import _root_.android.content.{Intent,Context,BroadcastReceiver}
import _root_.android.widget.{CheckBox,EditText}

class NotifyTimerActivity extends Activity{
  val TIMER_FIRST_ID = 1
  val TIMER_LAST_ID = 2
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.notify_timer)
    Globals.alarm_manager = Some(getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager])

  }
  def onTimerStartClick(v:View){
    setAllTimeAlarm
    finish
  }
  def onTimerCancelClick(v:View){
    Globals.global_lock.synchronized{
      Globals.notify_manager.foreach{_.cancelAll}
      Globals.notify_timers.foreach{case (k,v) =>
        val pendingIntent = PendingIntent.getBroadcast(this, k, v,PendingIntent.FLAG_CANCEL_CURRENT)
        try{
          Globals.alarm_manager.foreach{_.cancel(pendingIntent)}
          Globals.notify_timers.remove(k)
        }catch{case e:Exception => Unit}
      }
    }
    finish
  }
  def setAllTimeAlarm(){
    Globals.global_lock.synchronized{
      Globals.notify_manager.foreach{_.cancelAll}
      val iters = Array((TIMER_FIRST_ID,R.id.timer_first_limit,R.id.timer_first_play_sound),
                        (TIMER_LAST_ID,R.id.timer_last_limit,R.id.timer_last_play_sound))
      for( (timer_id, limit_id, play_sound_id ) <- iters ){
        val limit = try{
          findViewById(limit_id).asInstanceOf[EditText].getText.toString.toInt
        }catch{
          case e:java.lang.NumberFormatException => 0
        }
        val play_sound = findViewById(play_sound_id).asInstanceOf[CheckBox].isChecked
        val intent = new Intent(this, classOf[NotifyTimerReceiver])
        val limit_millis = System.currentTimeMillis() + (limit * 60 * 1000)
        intent.putExtra("timer_id",timer_id)
        intent.putExtra("elapsed",limit)
        intent.putExtra("is_last",timer_id == TIMER_LAST_ID)
        intent.putExtra("play_sound",play_sound)
        intent.putExtra("limit_millis",limit_millis)
        val pendingIntent = PendingIntent.getBroadcast(this, timer_id, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        Globals.alarm_manager.foreach{ x => 
          x.set(AlarmManager.RTC_WAKEUP, limit_millis, pendingIntent)
          Globals.notify_timers.update(timer_id,intent)
        }
      }
    }
  }
}



class NotifyTimerReceiver extends BroadcastReceiver {
  override def onReceive(context:Context, intent:Intent) {
    Globals.global_lock.synchronized{
      Globals.notify_manager = Some(context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager])
      val contentIntent = PendingIntent.getActivity(context, 0,new Intent(), 0)
      val timer_id = intent.getExtras.getInt("timer_id")
      val icon = if(intent.getExtras.getBoolean("is_last")){
        R.drawable.animals_dolphin
      }else{
        R.drawable.baby_tux
      }
      Globals.notify_timers.remove(timer_id)
      Utils.setButtonTextByState(context)
      val message = intent.getExtras.getInt("elapsed") + " " + context.getResources.getString(R.string.timer_minutes_elapsed)
      val from = context.getResources.getString(R.string.app_name)
      val notif = new Notification(icon,message, System.currentTimeMillis())
      if(intent.getExtras.getBoolean("play_sound") && !Globals.is_playing){
        notif.defaults |= Notification.DEFAULT_SOUND
      }
      notif.setLatestEventInfo(context, from, message, contentIntent)
      Globals.notify_manager.foreach{_.notify(timer_id, notif)}
    }
  }
}

