package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,Notification,AlarmManager,PendingIntent,NotificationManager}
import _root_.android.media.{AudioManager,RingtoneManager,Ringtone}
import _root_.android.os.{Bundle,Vibrator,Parcelable}
import _root_.android.net.Uri
import _root_.android.view.{View,LayoutInflater}
import _root_.android.content.{Intent,Context,BroadcastReceiver}
import _root_.android.widget.{CheckBox,EditText,ImageView,LinearLayout,TextView}
import _root_.android.provider.Settings
import _root_.android.text.TextUtils

class NotifyTimerActivity extends Activity{
  var current_ringtone = None:Option[Ringtone]
  val timer_icons = List(
    (R.drawable.baby_tux,13), // icon_id, default_minutes
    (R.drawable.animals_dolphin,15)
  )
  val timer_iter = timer_icons.zipWithIndex.map{ case ((icon,_),i) => (icon,i+1,Map("timer_id"->(i+1)))}

  def getRingtoneFromUri(uri:Uri):Option[Ringtone] = {
    var u = if(uri == null){ Settings.System.DEFAULT_NOTIFICATION_URI }else{uri}
    current_ringtone.foreach{_.stop} // we have to release MediaPlayer inside Ringtone (See source of android.media.Ringtone)
    val rt = RingtoneManager.getRingtone(this,u)
    current_ringtone = if(rt==null){None}else{Some(rt)}
    current_ringtone
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent){
    if(resultCode == Activity.RESULT_OK){
      val uri:Uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
      if(uri != null){
        val item = findViewById(R.id.notify_timer_linear).asInstanceOf[LinearLayout].findViewWithTag(Map("timer_id"->requestCode))
        setTimerUri(item,uri)
      }
    }
  }

  def setTimerUri(item_view:View,uri:Uri){
    val tv = item_view.findViewById(R.id.timer_sound_uri).asInstanceOf[TextView]
    getRingtoneFromUri(uri).foreach{r=>
      tv.setText(r.getTitle(this))
      tv.setTag(Map("timer_uri"->uri))
    }
  }

  override def onCreate(savedInstanceState: Bundle){
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    setContentView(R.layout.notify_timer)
    Globals.alarm_manager = Option(getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager])
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
      vw.findViewById(R.id.timer_icon).asInstanceOf[ImageView].setImageResource(icon)
      val minute = try { timer_minutes(index).toInt } catch { case _:Exception => timer_icons(index)._2  }
      vw.findViewById(R.id.timer_limit).asInstanceOf[EditText].setText(minute.toString)
      val uri = if(timer_uris.isDefinedAt(index) && ! TextUtils.isEmpty(timer_uris(index))){Uri.parse(timer_uris(index))}else{null}
      setTimerUri(vw,uri)
      val ps = get_boolean_at(play_sounds,index)
      vw.findViewById(R.id.timer_play_sound).asInstanceOf[CheckBox].setChecked(ps)
      val dv = get_boolean_at(do_vibrates,index) 
      vw.findViewById(R.id.timer_do_vibrate).asInstanceOf[CheckBox].setChecked(dv)
      findViewById(R.id.notify_timer_linear).asInstanceOf[LinearLayout].addView(vw)
    }

    this.setVolumeControlStream(AudioManager.STREAM_ALARM)
  }

  override def onPause(){
    super.onPause()
    current_ringtone.foreach{_.stop}
    current_ringtone = None
  }

  def myfinish(){
    var intent = new Intent(this,classOf[WasuramotiActivity])
    // TODO: implement Parcelable HashMap and send the following data in one row.
    intent.putExtra("notify_timers_keys",Globals.notify_timers.keys.toArray[Int])
    intent.putExtra("notify_timers_values",Globals.notify_timers.values.toArray[Parcelable])
    setResult(Activity.RESULT_OK,intent)
    finish
  }
  def onSoundChange(v:View){
    val intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION)
    val pv = Utils.findAncestorViewById(v,R.id.timer_item).get
    val tag = pv.findViewById(R.id.timer_sound_uri).asInstanceOf[TextView].getTag
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
    val tv = p.findViewById(R.id.timer_sound_uri).asInstanceOf[TextView]
    val tag = tv.getTag
    val uri = if(tag == null){null}else{tag.asInstanceOf[Map[String,Uri]].getOrElse("timer_uri",null)}
    getRingtoneFromUri(uri).foreach{r=>
      r.setStreamType(AudioManager.STREAM_ALARM) // able to play sound even when it is silent mode.
      r.play
    }
  }
  def onTimerStartClick(v:View){
    if(Globals.alarm_manager.isEmpty){
      Utils.messageDialog(this,Right(R.string.alarm_service_not_supported))
      return
    }
    setAllTimeAlarm
    myfinish
  }
  def cancelAndRemove(){
    Globals.notify_manager.foreach{_.cancelAll}
    Globals.notify_timers.foreach{case (k,v) =>
      val pendingIntent = PendingIntent.getBroadcast(this, k, v,PendingIntent.FLAG_CANCEL_CURRENT)
      try{
        Globals.alarm_manager.foreach{_.cancel(pendingIntent)}
        Globals.notify_timers.remove(k)
      }catch{
        case _:Exception => Unit
      }
    }
  }
  def onTimerCancelClick(v:View){
    Globals.global_lock.synchronized{
      cancelAndRemove
    }
    myfinish
  }
  def setAllTimeAlarm(){
    Globals.global_lock.synchronized{
      cancelAndRemove
      val timer_datas = timer_iter.map{case (timer_icon,timer_id,timer_tag) => {
        val vw_item = findViewById(R.id.notify_timer_linear).asInstanceOf[LinearLayout].findViewWithTag(timer_tag)
        val limit = try{
          vw_item.findViewById(R.id.timer_limit).asInstanceOf[EditText].getText.toString.toInt
        }catch{
          case e:java.lang.NumberFormatException => 0
        }
        val play_sound = vw_item.findViewById(R.id.timer_play_sound).asInstanceOf[CheckBox].isChecked
        val uri_tag = vw_item.findViewById(R.id.timer_sound_uri).asInstanceOf[TextView].getTag.asInstanceOf[Map[String,Uri]]
        val do_vibrate = vw_item.findViewById(R.id.timer_do_vibrate).asInstanceOf[CheckBox].isChecked
        val intent = new Intent(this, classOf[NotifyTimerReceiver])
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
        Globals.alarm_manager.foreach{ x =>
          x.set(AlarmManager.RTC_WAKEUP, limit_millis, pendingIntent)
          Globals.notify_timers.update(timer_id,intent)
        }
        (limit,uri,play_sound,do_vibrate)
      }}
      Globals.prefs.get.edit()
      .putString("timer_default_minutes",timer_datas.map{_._1}.mkString(","))
      .putString("timer_default_uris",timer_datas.map{_._2}.mkString("<:>"))
      .putString("timer_default_play_sounds",timer_datas.map{_._3.toString}.mkString(","))
      .putString("timer_default_do_vibrates",timer_datas.map{_._4.toString}.mkString(","))
      .commit()
    }
  }
}



class NotifyTimerReceiver extends BroadcastReceiver {
  override def onReceive(context:Context, intent:Intent) {
    Globals.global_lock.synchronized{
      Globals.notify_manager = Option(context.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager])
      val contentIntent = PendingIntent.getActivity(context, 0,new Intent(), 0)
      val extras = intent.getExtras
      val timer_id = extras.getInt("timer_id")
      val icon = extras.getInt("timer_icon")
      Globals.notify_timers.remove(timer_id)
      Utils.setButtonTextByState(context)
      val message = extras.getInt("elapsed") + " " + context.getResources.getString(R.string.timer_minutes_elapsed)
      val from = context.getResources.getString(R.string.app_name)
      val notif = new Notification(icon,message, System.currentTimeMillis())
      if(!Globals.is_playing){
        if(extras.getBoolean("play_sound")){
          notif.audioStreamType = AudioManager.STREAM_ALARM // able to play sound even when it is silent mode.
          val sound = extras.get("sound_uri").asInstanceOf[Uri]
          if(sound == null){
            notif.defaults |= Notification.DEFAULT_SOUND
          }else{
            notif.sound = sound
          }
        }
        if(extras.getBoolean("do_vibrate")){
          // using `notif.defaults |= Notification.DEFAULT_VIBRATE' does not work when RINGER_MODE_SILENT
          val vib = context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
          if(vib != null){
            vib.vibrate(Array.concat(Array(0),Array.fill(3){Array(1500,500).map{_.toLong}}.flatten),-1)
          }else{
            println("WARNING: VIBRATOR_SERVICE is not supported on this device.")
          }
        }
      }
      notif.setLatestEventInfo(context, from, message, contentIntent)
      Globals.notify_manager.foreach{_.notify(timer_id, notif)}
    }
  }
}

