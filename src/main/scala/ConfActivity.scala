package karuta.hpnpwd.wasuramoti

import _root_.android.preference.{PreferenceActivity,DialogPreference}
import _root_.android.os.Bundle
import _root_.android.app.{PendingIntent,AlarmManager}
import _root_.android.media.AudioManager
import _root_.android.view.{View,LayoutInflater}
import _root_.android.text.TextUtils
import _root_.android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox}
import _root_.android.util.AttributeSet
import _root_.android.content.{Context,SharedPreferences,Intent}
import _root_.android.preference.{Preference,PreferenceManager,EditTextPreference,ListPreference,PreferenceGroup}

trait PreferenceCustom extends Preference{
  self:{ def getKey():String; def onBindView(v:View); def notifyChanged()} =>
  abstract override def onBindView(v:View) {
    v.findViewWithTag("conf_current_value").asInstanceOf[TextView].setText(getAbbrValue())
    super.onBindView(v)
  }
  def getAbbrValue():String = {
    // I don't now why we cannot use getPersistedString() here
    Globals.prefs.get.getString(getKey(),"")
  }
  // We have to call notifyChanged() to to reflect the change to view.
  // However, notifyChanged() is protected method. Therefore we use this method.
  def notifyChangedPublic(){
    super.notifyChanged()
  }
}
class EditTextPreferenceCustom(context:Context,aset:AttributeSet) extends EditTextPreference(context,aset) with PreferenceCustom{
  // value of AttributeSet can only be acquired in constructor
  val unit = aset.getAttributeResourceValue(null,"unit",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }
  override def getAbbrValue():String = {
    getPersistedString("") + unit
  }
}
class ListPreferenceCustom(context:Context,aset:AttributeSet) extends ListPreference(context,aset) with PreferenceCustom{
  override def getAbbrValue():String = {
    val key = getKey()
    val value = getValue()
    val resources = context.getResources
    val get_entry_from_value = (values:Int,entries:Int) => {
      try{
        val index = resources.getStringArray(values).indexOf(value)
        resources.getStringArray(entries)(index)
      }catch{
        // Upgrading from older version causes this exception since value is empty.
        case _:IndexOutOfBoundsException => ""
      }
    }
    key match{
      case "read_order" => get_entry_from_value(R.array.conf_read_order_entryValues,R.array.conf_read_order_entries)
      case "read_order_each" => get_entry_from_value(R.array.conf_read_order_each_entryValues,R.array.conf_read_order_each_entries_abbr)
      case "audio_track_mode" => get_entry_from_value(R.array.conf_audio_track_entryValues,R.array.conf_audio_track_entries)
      case "show_yomi_info" => get_entry_from_value(R.array.conf_show_yomi_info_entryValues,R.array.conf_show_yomi_info_entries_abbr)
      case _ => value
    }
  }
}

class JokaOrderPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  // We can set defaultValue="..." in src/main/res/xml/conf.xml
  // and reflect it to actual preference by calling:
  //   PreferenceManager.setDefaultValues(context, R.xml.conf, true)
  // As for custom DialogPreference, we have to override
  // onSetInitialValue() and onGetDefaultValue() to get it to work.
  // See 'Building a Custom Preference' in android developer document:
  //   http://developer.android.com/guide/topics/ui/settings.html#Custom
  // However this solution does not seem to work in Android 4.x+
  // Therefore we use this old dirty hack.
  val DEFAULT_VALUE = "upper_1,lower_1"
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      persistString(Array(R.id.conf_joka_upper_num,R.id.conf_joka_lower_num).map{ rid =>
        val btn = root_view.get.findViewById(rid).asInstanceOf[RadioGroup].getCheckedRadioButtonId()
        root_view.get.findViewById(btn).getTag()
      }.mkString(","))
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_joka,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    for( t <- getPersistedString(DEFAULT_VALUE).split(",")){
      view.findViewWithTag(t).asInstanceOf[RadioButton].toggle()
    }
    return view
  }
  override def getAbbrValue():String = {
    getPersistedString(DEFAULT_VALUE).split(",").map{ x =>{
      val Array(ul,num) = x.split("_")
      context.getResources.getString(ul match{
        case "upper" => R.string.conf_upper_abbr
        case "lower" => R.string.conf_lower_abbr
      }) + num
    }}.mkString("")
  }
}
class AutoPlayPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val DEFAULT_VALUE = 5
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def getWidgets(view:View) = {
    val span = view.findViewById(R.id.autoplay_span).asInstanceOf[TextView]
    val enable = view.findViewById(R.id.autoplay_enable).asInstanceOf[CheckBox]
    (enable,span)
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (enable,span) = getWidgets(view)
        edit.putBoolean("autoplay_enable",enable.isChecked)
        edit.putLong("autoplay_span",Math.max(1,try{
            span.getText.toString.toInt
          }catch{
            case _:NumberFormatException => 1
          }))
        edit.commit
        notifyChangedPublic
      }
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.autoplay,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val (enable,span) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("autoplay_enable",false))
    span.setText(prefs.getLong("autoplay_span",DEFAULT_VALUE).toString)
    return view
  }
  override def getAbbrValue():String = {
    if(Globals.prefs.get.getBoolean("autoplay_enable",false)){
      Globals.prefs.get.getLong("autoplay_span",DEFAULT_VALUE) + context.getResources.getString(R.string.conf_unit_second)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
}


class KarafudaPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var root_view = None:Option[View]
  def getWidgets(view:View) = {
    val rand = view.findViewById(R.id.karafuda_urafuda_prob).asInstanceOf[SeekBar]
    val num = view.findViewById(R.id.karafuda_append_num).asInstanceOf[TextView]
    val enable = view.findViewById(R.id.karafuda_enable).asInstanceOf[CheckBox]
    (enable,num,rand)
  }
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)

  override def getAbbrValue():String={
    if(Globals.prefs.get.getBoolean("karafuda_enable",false)){
      Globals.prefs.get.getInt("karafuda_append_num",0).toString + context.getResources.getString(R.string.karafuda_unit)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (enable,num,rand) = getWidgets(view)
        edit.putBoolean("karafuda_enable",enable.isChecked)
        edit.putInt("karafuda_append_num",try{
            num.getText.toString.toInt
          }catch{
            case _:NumberFormatException => 0
          })
        edit.putFloat("karafuda_urafuda_prob",rand.getProgress.toFloat/rand.getMax.toFloat)
        edit.commit
        notifyChangedPublic
        FudaListHelper.updateSkipList()
      }

    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.karafuda,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val (enable,num,rand) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("karafuda_enable",false))
    num.setText(prefs.getInt("karafuda_append_num",0).toString)
    rand.setProgress((prefs.getFloat("karafuda_urafuda_prob",0.5f)*rand.getMax).toInt)
    return view
  }

}

class AudioVolumePreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var root_view = None:Option[View]
  var default_volume = None:Option[Int]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)

  def withAudioVolume(defValue:Any,func:(AudioManager,Int,Int)=>Any):Any = {
    val audio_manager = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    if(audio_manager != null){
      val max_volume = audio_manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
      val current_volume = audio_manager.getStreamVolume(AudioManager.STREAM_MUSIC)
      func(audio_manager,max_volume,current_volume)
    }else{
      defValue
    }
  }

  override def getAbbrValue():String={
    val volume = getPersistedString("")
    if(TextUtils.isEmpty(volume)){
      withAudioVolume("",{(audio_manager,max_volume,current_volume) =>
        (100 * (current_volume.toFloat / max_volume.toFloat)).toInt
      }).toString
    }else{
      "* " + (100 * volume.toFloat).toInt.toString + " *"
    }
  }


  override def onDialogClosed(positiveResult:Boolean){
    Globals.player.foreach{ p => {
      Globals.global_lock.synchronized{
        if(Globals.is_playing){
          p.stop();
        }
      }
      }}
    if(positiveResult){
      root_view.foreach{ view =>
        val check = view.findViewById(R.id.volume_set_each_play).asInstanceOf[CheckBox]
        val new_value = if(check.isChecked){
          val seek = view.findViewById(R.id.volume_seek).asInstanceOf[SeekBar]
          "%.2f".format(seek.getProgress.toFloat / seek.getMax.toFloat)
        }else{
          Globals.audio_volume_bkup = None // do not restore audio volume
          notifyChangedPublic()
          ""
        }
        persistString(new_value)
      }
    }
    Utils.restoreAudioVolume(context)
    Globals.player.foreach{ pl =>
      pl.set_audio_volume = true
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    Globals.player.foreach{ pl =>
      pl.set_audio_volume = false
    }
    val view = LayoutInflater.from(context).inflate(R.layout.audio_volume,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)

    Utils.setAudioPlayButton(view,context)

    val check = view.findViewById(R.id.volume_set_each_play).asInstanceOf[CheckBox]
    check.setChecked(!TextUtils.isEmpty(getPersistedString("")))

    val seek = view.findViewById(R.id.volume_seek).asInstanceOf[SeekBar]

    withAudioVolume(Unit,{(audio_manager, max_volume, current_volume) =>
      Globals.audio_volume_bkup = Some(current_volume)
    })
    Utils.saveAndSetAudioVolume(context)
    withAudioVolume(Unit,{(audio_manager, max_volume, current_volume) =>
      seek.setProgress(((current_volume.toFloat/max_volume.toFloat)*seek.getMax).toInt)
      seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        override def onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
          val volume = math.min(((progress.toFloat/seek.getMax.toFloat)*max_volume).toInt,max_volume)
          audio_manager.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0)
        }
        override def onStartTrackingTouch(bar:SeekBar){
        }
        override def onStopTrackingTouch(bar:SeekBar){
        }
      })
    })
    return view
  }
}

class ConfActivity extends PreferenceActivity with FudaSetTrait with WasuramotiBaseTrait {
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    addPreferencesFromResource(R.xml.conf)
    listener = Some(new SharedPreferences.OnSharedPreferenceChangeListener{
      override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
        if(Array("read_order_each","reader_path","read_order_joka","wav_span_simokami",
                 "wav_threashold","wav_fadeout_simo","wav_fadein_kami").contains(key)){
          Globals.forceRefresh = true
        }
        if(key == "show_yomi_info"){
          Globals.forceResetContentView = true
        }
        if(key == "hardware_accelerate"){
          // Restart Application
          val start_activity = new Intent(context,classOf[WasuramotiActivity])
          val pending_id = 271828
          val pending_intent = PendingIntent.getActivity(context, pending_id, start_activity, PendingIntent.FLAG_CANCEL_CURRENT)
          val mgr = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
          if(mgr != null){
            Utils.confirmDialog(context,Right(R.string.conf_hardware_accelerate_restart_confirm),
              { Unit =>
                mgr.set(AlarmManager.RTC, System.currentTimeMillis+100, pending_intent)
                System.exit(0)
              })
          }
        }
        val pref = findPreference(key)
        if(pref != null && classOf[PreferenceCustom].isAssignableFrom(pref.getClass)){
          pref.asInstanceOf[Preference with PreferenceCustom].notifyChangedPublic()
        }
      }
    })
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(listener.get)
    findPreference("init_fudaset").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        Utils.confirmDialog(context,Right(R.string.confirm_init_fudaset), _ => {
          Globals.database.foreach{ db =>
            DbUtils.initializeFudaSets(getApplicationContext,db.getWritableDatabase,true)
          }
          finish()
        })
        return false
      }
    })
    findPreference("init_preference").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        Utils.confirmDialog(context,Right(R.string.confirm_init_preference), _ => {
          Globals.prefs.foreach{ p =>
            val ed = p.edit
            ed.clear
            ed.commit
          }
          PreferenceManager.setDefaultValues(getApplicationContext(),R.xml.conf,true)
          ReaderList.setDefaultReader(getApplicationContext())
          finish()
        })
        return false
      }
    })
  }
  override def onDestroy(){
    super.onDestroy()
    listener.foreach{ l =>
      Globals.prefs.foreach{ p =>
        p.unregisterOnSharedPreferenceChangeListener(l)
        listener = None
      }
    }
  }
}

