package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,Notification,AlarmManager,PendingIntent,NotificationManager}
import _root_.android.os.Bundle 
import _root_.android.view.View
import _root_.android.content.{Intent,Context,BroadcastReceiver}

class NotifyTimerActivity extends Activity{
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.notify_timer)
    Globals.alarm_manager = Some(getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager])

  }
  def onTimerStartClick(v:View){
    Globals.notify_manager.foreach{_.cancelAll}
    setOneTimeAlarm
    println("TimerStart Clicked")

  }
  def setOneTimeAlarm(){
    val intent = new Intent(this, classOf[NotifyTimerReceiver])
    val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)
    Globals.alarm_manager.foreach{
      _.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (5 * 1000), pendingIntent)
    }
  }
}

class NotifyTimerReceiver extends BroadcastReceiver {
  override def onReceive(context:Context, intent:Intent) {
    Globals.notify_manager = Some(context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager])
    val contentIntent = PendingIntent.getActivity(context, 0,new Intent(), 0)
    val notif = new Notification(_root_.android.R.drawable.star_on,"Message In Title Bar...", System.currentTimeMillis())
    val from = "from"
    val message = "message"
    notif.setLatestEventInfo(context, from, message, contentIntent)
    Globals.notify_manager.foreach{_.notify(1, notif)}
 }
}
