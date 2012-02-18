package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,Notification,AlarmManager,PendingIntent,NotificationManager}
import _root_.android.media.AudioManager
import _root_.android.os.Bundle 
import _root_.android.view.{View,LayoutInflater}
import _root_.android.content.{Intent,Context,BroadcastReceiver}
import _root_.android.widget.{CheckBox,EditText,ImageView,LinearLayout}

class NotifyTimerActivity extends Activity{
  val timer_icons = List(
    (R.drawable.baby_tux,13),
    (R.drawable.animals_dolphin,15)
  )
  val timer_iter = timer_icons.zipWithIndex.map{ case ((icon,default),i) => (icon,default,i+1,new Object())}
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.notify_timer)
    Globals.alarm_manager = Some(getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager])
    val inflater = LayoutInflater.from(this)
    for( (icon,default,id,tag) <- timer_iter ){
      val vw = inflater.inflate(R.layout.notify_timer_item,null)
      vw.setTag(tag)
      vw.findViewById(R.id.timer_icon).asInstanceOf[ImageView].setImageResource(icon)
      vw.findViewById(R.id.timer_limit).asInstanceOf[EditText].setText(default.toString)
      findViewById(R.id.notify_timer_linear).asInstanceOf[LinearLayout].addView(vw)
    }
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
      for( (timer_icon,_,timer_id,timer_tag) <- timer_iter ){
        val vw_item = findViewById(R.id.notify_timer_linear).asInstanceOf[LinearLayout].findViewWithTag(timer_tag)
        val limit = try{
          vw_item.findViewById(R.id.timer_limit).asInstanceOf[EditText].getText.toString.toInt
        }catch{
          case e:java.lang.NumberFormatException => 0
        }
        val play_sound = vw_item.findViewById(R.id.timer_play_sound).asInstanceOf[CheckBox].isChecked
        val do_vibrate = vw_item.findViewById(R.id.timer_do_vibrate).asInstanceOf[CheckBox].isChecked
        val intent = new Intent(this, classOf[NotifyTimerReceiver])
        val limit_millis = System.currentTimeMillis() + (limit * 60 * 1000)
        intent.putExtra("timer_id",timer_id)
        intent.putExtra("elapsed",limit)
        intent.putExtra("play_sound",play_sound)
        intent.putExtra("do_vibrate",do_vibrate)
        intent.putExtra("limit_millis",limit_millis)
        intent.putExtra("timer_icon",timer_icon)
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
      val icon = intent.getExtras.getInt("timer_icon")
      Globals.notify_timers.remove(timer_id)
      Utils.setButtonTextByState(context)
      val message = intent.getExtras.getInt("elapsed") + " " + context.getResources.getString(R.string.timer_minutes_elapsed)
      val from = context.getResources.getString(R.string.app_name)
      val notif = new Notification(icon,message, System.currentTimeMillis())
      if(!Globals.is_playing){
        if(intent.getExtras.getBoolean("play_sound")){ 
          notif.defaults |= Notification.DEFAULT_SOUND
          notif.audioStreamType = AudioManager.STREAM_ALARM // able to play sound even when it is silent mode.
        }
        if(intent.getExtras.getBoolean("do_vibrate")){
          notif.defaults |= Notification.DEFAULT_VIBRATE
        }
      }
      notif.setLatestEventInfo(context, from, message, contentIntent)
      Globals.notify_manager.foreach{_.notify(timer_id, notif)}
    }
  }
}

