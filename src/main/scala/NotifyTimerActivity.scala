package karuta.hpnpwd.wasuramoti

import android.app.{Activity,Notification,AlarmManager,PendingIntent,NotificationManager,NotificationChannel}
import android.media.{AudioManager,RingtoneManager,Ringtone}
import android.os.{Bundle,Vibrator,Handler}
import android.net.Uri
import android.view.{View,LayoutInflater}
import android.content.{Intent,Context,BroadcastReceiver}
import android.widget.{CheckBox,EditText,ImageView,LinearLayout,TextView}
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

import android.support.v4.app.{NotificationCompat,TaskStackBuilder,FragmentActivity}

import java.text.SimpleDateFormat
import java.util.Date

import scala.collection.mutable

object NotifyTimerUtils {
  val NOTIFICATION_CHANNEL_ID = "wasuramoti_mem_timer"
  val notify_timers = new mutable.HashMap[Int,Intent]()
  var alarm_manager = None:Option[AlarmManager]
  var notify_manager = None:Option[NotificationManager]
  def loadTimers(){
    notify_timers.clear
    val str = Globals.prefs.get.getString("timer_intents","")
    var have_to_save = false
    if(!TextUtils.isEmpty(str)){
      str.split("<:>").foreach{s =>
        try{
          val intent = Intent.parseUri(s,0)
          val timer_id = intent.getExtras.getInt("timer_id")
          if(intent.getExtras.getLong("limit_millis") > System.currentTimeMillis){
            notify_timers.put(timer_id,intent)
          }else{
            have_to_save = true
          }
        }catch{
          // Intent.parseUri throws URISyntaxException
          // intent.getExtres throws NumberFormatException
          case e @ ( _:java.net.URISyntaxException | _:NullPointerException) => {
            Log.v("wasuramoti",e.toString)
            have_to_save = true
          }
        }
      }
    }
    if(have_to_save){
      saveTimers
    }
  }
  def saveTimers(){
    val str = notify_timers.map{_._2.toUri(0).toString}.mkString("<:>")
    Globals.prefs.get.edit
    .putString("timer_intents",str)
    .commit
  }
  def makeTimerText(context:Context):String = {
     val title = context.getResources.getString(R.string.timers_remaining)
     notify_timers.toList.sortWith{case ((k1,v1),(k2,v2)) => v1.getExtras.getLong("limit_millis") < v2.getExtras.getLong("limit_millis")}.map{case (k,v) =>
       val millis = v.getExtras.getLong("limit_millis")
       val minutes_left = scala.math.ceil((millis - System.currentTimeMillis()) / (1000 * 60.0)).toInt
       val df = new SimpleDateFormat(
         if( minutes_left < 60 * 24){
           "HH:mm"
         }else{
           "MM/dd HH:mm"
         }
       )
       df.format(new Date(millis)) + " (" + minutes_left.toString + " " + context.getResources.getString(R.string.timers_minutes_left) + ")"
     }.foldLeft(title)(_+"\n"+_)
  }
}

class NotifyTimerActivity extends FragmentActivity with WasuramotiBaseTrait{
  var current_ringtone = None:Option[Ringtone]
  val timer_icons = List(
    (R.drawable.baby_tux,13), // icon_id, default_minutes
    (R.drawable.animals_dolphin,15)
  )
  val timer_iter = timer_icons.zipWithIndex.map{ case ((icon,_),i) => (icon,i+1,Map("timer_id"->(i+1)))}

  def getRingtoneFromUri(uri:Uri):Option[Ringtone] = {
    val u = if(uri == null){ Settings.System.DEFAULT_NOTIFICATION_URI }else{uri}
    current_ringtone.foreach{_.stop} // we have to release MediaPlayer inside Ringtone (See source of android.media.Ringtone)
    val rt = RingtoneManager.getRingtone(this,u)
    current_ringtone = Option(rt)
    current_ringtone
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent){
    super.onActivityResult(requestCode, resultCode, intent)
    if(resultCode == Activity.RESULT_OK){
      val uri:Uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      if(uri != null){
        val item = findViewById[LinearLayout](R.id.notify_timer_linear).findViewWithTag[View](Map("timer_id"->requestCode))
        setTimerUri(item,uri)
      }
    }
  }

  def setTimerUri(item_view:View,uri:Uri){
    val tv = item_view.findViewById[TextView](R.id.timer_sound_uri)
    getRingtoneFromUri(uri).foreach{r=>
      tv.setText(r.getTitle(this))
      tv.setTag(Map("timer_uri"->uri))
    }
  }

  override def onCreate(savedInstanceState: Bundle){
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    setTheme(ColorThemeHelper.getFromPref.styleId)
    setContentView(R.layout.notify_timer)
    NotifyTimerUtils.alarm_manager = Option(getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager])
    val inflater = LayoutInflater.from(this)

    val pref = Globals.prefs.get
    val timer_minutes = pref.getString("timer_default_minutes","").split(",")
    val timer_uris = pref.getString("timer_default_uris","").split("<:>")
    val play_sounds =  pref.getString("timer_default_play_sounds","").split(",")
    val do_vibrates = pref.getString("timer_default_do_vibrates","").split(",")

    val get_boolean_at = {(ar:Array[String],i:Int) => if(ar.isDefinedAt(i) && ! TextUtils.isEmpty(ar(i))){ar(i).toBoolean }else{true}}

    for( ((icon,id,tag),index) <- timer_iter.zipWithIndex ){
      val vw = inflater.inflate(R.layout.notify_timer_item,null)
      vw.setTag(tag)
      vw.findViewById[ImageView](R.id.timer_icon).setImageResource(icon)
      val minute = try { timer_minutes(index).toInt } catch { case _:Exception => timer_icons(index)._2  }
      vw.findViewById[EditText](R.id.timer_limit).setText(minute.toString)
      val uri = if(timer_uris.isDefinedAt(index) && ! TextUtils.isEmpty(timer_uris(index))){Uri.parse(timer_uris(index))}else{null}
      setTimerUri(vw,uri)
      val ps = get_boolean_at(play_sounds,index)
      vw.findViewById[CheckBox](R.id.timer_play_sound).setChecked(ps)
      val dv = get_boolean_at(do_vibrates,index)
      vw.findViewById[CheckBox](R.id.timer_do_vibrate).setChecked(dv)
      findViewById[LinearLayout](R.id.notify_timer_linear).addView(vw)
    }

    this.setVolumeControlStream(AudioManager.STREAM_NOTIFICATION)
  }

  override def onPause(){
    super.onPause()
    current_ringtone.foreach{_.stop}
    current_ringtone = None
  }

  def onSoundChange(v:View){
    val intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION)
    val pv = Utils.findAncestorViewById(v,R.id.timer_item).get
    val tag = pv.findViewById[TextView](R.id.timer_sound_uri).getTag
    val uri = if(tag != null){
      tag.asInstanceOf[Map[String,Uri]].getOrElse("timer_uri",null)
    }else{
      Settings.System.DEFAULT_NOTIFICATION_URI
    }
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,uri)
    startActivityForResult(intent,pv.getTag.asInstanceOf[Map[String,Int]].get("timer_id").get)
  }

  def onSoundTest(v:View){
    current_ringtone.foreach{rt=>
      if(rt.isPlaying){
        rt.stop
        return
      }
    }
    val p = Utils.findAncestorViewById(v,R.id.timer_item).get.asInstanceOf[LinearLayout]
    val tv = p.findViewById[TextView](R.id.timer_sound_uri)
    val tag = tv.getTag
    val uri = if(tag == null){null}else{tag.asInstanceOf[Map[String,Uri]].getOrElse("timer_uri",null)}
    getRingtoneFromUri(uri).foreach{r=>
      r.setStreamType(AudioManager.STREAM_NOTIFICATION)
      r.play
    }
  }
  def onTimerStartClick(v:View){
    if(NotifyTimerUtils.alarm_manager.isEmpty){
      CommonDialog.messageDialog(this,Right(R.string.alarm_service_not_supported))
      return
    }
    setAllTimeAlarm
    finish
  }
  def cancelAndRemove(){
    NotifyTimerUtils.notify_manager.foreach{_.cancelAll}
    NotifyTimerUtils.notify_timers.foreach{case (k,v) =>
      val pendingIntent = PendingIntent.getBroadcast(this, k, v, PendingIntent.FLAG_CANCEL_CURRENT)
      try{
        NotifyTimerUtils.alarm_manager.foreach{_.cancel(pendingIntent)}
        NotifyTimerUtils.notify_timers.remove(k)
      }catch{
        case _:Exception => Unit
      }
    }
    NotifyTimerUtils.saveTimers
  }
  def onTimerCancelClick(v:View){
    Globals.global_lock.synchronized{
      cancelAndRemove
    }
    finish
  }
  def setAllTimeAlarm(){
    Globals.global_lock.synchronized{
      cancelAndRemove
      val timer_datas = timer_iter.map{case (timer_icon,timer_id,timer_tag) => {
        val vw_item = findViewById[LinearLayout](R.id.notify_timer_linear).findViewWithTag[View](timer_tag)
        val limit = try{
          vw_item.findViewById[EditText](R.id.timer_limit).getText.toString.toInt
        }catch{
          case e:java.lang.NumberFormatException => 0
        }
        val play_sound = vw_item.findViewById[CheckBox](R.id.timer_play_sound).isChecked
        val uri_tag = vw_item.findViewById[TextView](R.id.timer_sound_uri).getTag.asInstanceOf[Map[String,Uri]]
        val do_vibrate = vw_item.findViewById[CheckBox](R.id.timer_do_vibrate).isChecked
        val intent = new Intent(this, classOf[NotifyTimerReceiver])
        //action and data must be identical to cancel even after application restarted
        intent.setAction(timer_id.toString)
        intent.setData(Uri.parse("wasuramoti://timer/"+timer_id.toString))

        val limit_millis = System.currentTimeMillis() + (limit * 60 * 1000)
        intent.putExtra("timer_id",timer_id)
        intent.putExtra("elapsed",limit)
        intent.putExtra("play_sound",play_sound)
        val uri = if(uri_tag != null){
          val x:Uri = uri_tag.getOrElse("timer_uri",null)
          intent.putExtra("sound_uri",x)
          if(x==null){""}else{x.toString}
        }else{
          ""
        }
        intent.putExtra("do_vibrate",do_vibrate)
        intent.putExtra("limit_millis",limit_millis)
        intent.putExtra("timer_icon",timer_icon)
        val pendingIntent = PendingIntent.getBroadcast(this, timer_id, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        NotifyTimerUtils.alarm_manager.foreach{ manager =>
          Utils.alarmManagerSetExact(manager, AlarmManager.RTC_WAKEUP, limit_millis, pendingIntent)
          NotifyTimerUtils.notify_timers.update(timer_id,intent)
        }
        (limit,uri,play_sound,do_vibrate)
      }}
      Globals.prefs.get.edit
      .putString("timer_default_minutes",timer_datas.map{_._1}.mkString(","))
      .putString("timer_default_uris",timer_datas.map{_._2}.mkString("<:>"))
      .putString("timer_default_play_sounds",timer_datas.map{_._3.toString}.mkString(","))
      .putString("timer_default_do_vibrates",timer_datas.map{_._4.toString}.mkString(","))
      .commit
      NotifyTimerUtils.saveTimers
    }
  }
}



class NotifyTimerReceiver extends BroadcastReceiver {
  val handler = new Handler
  override def onReceive(context:Context, intent:Intent) {
    Globals.global_lock.synchronized{
      if (NotifyTimerUtils.notify_manager.isEmpty) {
        NotifyTimerUtils.notify_manager = Option(context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager])
        if (android.os.Build.VERSION.SDK_INT >= 26) {
          // Starting in API>=26, all notifications must be assigned to a channel
          //   https://developer.android.com/training/notify-user/channels
          NotifyTimerUtils.notify_manager.foreach{ manager =>
            val channel = new NotificationChannel(NotifyTimerUtils.NOTIFICATION_CHANNEL_ID,
              "Wasuramoti Timer", NotificationManager.IMPORTANCE_DEFAULT)
            channel.setDescription("Wasuramoti timer for memorization time")
            // Disable Sound
            //  https://stackoverflow.com/questions/45919392/disable-sound-from-notificationchannel
            channel.setSound(null,null)
            manager.createNotificationChannel(channel)
          }
        }
      }

      val result_intent = new Intent(context, classOf[WasuramotiActivity])
      val pending_intent = TaskStackBuilder.create(context)
        .addParentStack(classOf[WasuramotiActivity])
        .addNextIntent(result_intent)
        .getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT)

      val extras = intent.getExtras
      val timer_id = extras.getInt("timer_id")
      val icon = extras.getInt("timer_icon")
      NotifyTimerUtils.notify_timers.remove(timer_id)
      
      val message = extras.getInt("elapsed") + " " + context.getResources.getString(R.string.timer_minutes_elapsed)
      val from = context.getResources.getString(R.string.app_name)
      val notif = new NotificationCompat.Builder(context, NotifyTimerUtils.NOTIFICATION_CHANNEL_ID)
        .setContentTitle(from)
        .setContentText(message)
        .setTicker(message)
        .setSmallIcon(icon)
        .setContentIntent(pending_intent)
      if(!Globals.is_playing){
        if(extras.getBoolean("play_sound")){
          // Starting with API >= 26, notification sound seems to be managed by NotificationChannel#setSound(), and NotificationCompat.Builder#setSound is not working anymore.
          // Since it can register one sound per channel, and cannot change after createNotificationChannel,
          // we have to manage multiple channel if we want to play sound via notification.
          // Therefore, we give up using notification sound, and use RingtoneManager instead.
          val sound = Option(extras.get("sound_uri").asInstanceOf[Uri]).getOrElse(Settings.System.DEFAULT_NOTIFICATION_URI)
          Option(RingtoneManager.getRingtone(context, sound)).foreach{r=>
            r.setStreamType(AudioManager.STREAM_NOTIFICATION)
            r.play
            handler.postDelayed(new Runnable{
              override def run(){
                r.stop
              }
            }, 40*1000)
          }
        }
        if(extras.getBoolean("do_vibrate")){
          // using `notif.defaults |= Notification.DEFAULT_VIBRATE' does not work when RINGER_MODE_SILENT
          val vib = context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
          if(vib != null){
            vib.vibrate(Array.concat(Array(0L),Array.fill(3){Array(1500L,500L)}.flatten),-1)
          }else{
            Log.v("wasuramoti","WARNING: VIBRATOR_SERVICE is not supported on this device.")
          }
        }
      }
      NotifyTimerUtils.notify_manager.foreach{_.notify(timer_id, notif.build)}

      // call WasuramotiActivity.setButtonTextByState()
      context.sendBroadcast(new Intent(Globals.ACTION_NOTIFY_TIMER_FIRED))
    }
  }
}

