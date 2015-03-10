package karuta.hpnpwd.wasuramoti
import _root_.android.preference.DialogPreference
import _root_.android.content.{Context,SharedPreferences}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox,Button,EditText}
import _root_.android.media.AudioManager
import _root_.android.app.AlertDialog
import _root_.android.text.TextUtils
import scala.collection.mutable

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


class ReadOrderEachPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap
  val DEFAULT_VALUE = "CUR2_NEXT1"
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def parseCustomOrder(str:String):Option[String] = {
    val s = str.split("/").zip(Array("CUR","NEXT")).map{case (s,t) => s.filter(x => x =='1' || x == '2').map(t+_)}.flatten.mkString("_")
    if(s.startsWith("CUR")){
      Some(s)
    }else{
      None
    }
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val group = root_view.get.findViewById(R.id.conf_read_order_each_group).asInstanceOf[RadioGroup]
      val bid = group.getCheckedRadioButtonId()
      if(bid == R.id.conf_read_order_each_custom){
        if(!Globals.prefs.get.contains("read_order_each_custom")){
          //TODO: show alert dialog
        }else{
          persistString(Globals.prefs.get.getString("read_order_each_custom",DEFAULT_VALUE))
        }
      }else{
        val vw = group.findViewById(bid)
        val idx = group.indexOfChild(vw)
        val ar = context.getResources.getStringArray(R.array.conf_read_order_each_entryValues)
        persistString(ar(idx))
      }
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_each,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val group = view.findViewById(R.id.conf_read_order_each_group).asInstanceOf[RadioGroup]
    val ar = context.getResources.getStringArray(R.array.conf_read_order_each_entryValues)
    val value = getPersistedString(DEFAULT_VALUE)
    val idx = ar.indexOf(value)
    if(idx == -1){
      group.check(R.id.conf_read_order_each_custom)
    }else{
      val vw = group.getChildAt(idx)
      group.check(vw.getId())
    }

    val custom =  (builder:AlertDialog.Builder) => {
      val edit_text = new EditText(context)
      edit_text.setRawInputType(android.text.InputType.TYPE_CLASS_PHONE)
      edit_text.setFilters(Array(new android.text.InputFilter.LengthFilter(7)))
      edit_text.setId(R.id.conf_read_order_each_custom_text)
      builder.setView(edit_text)
    }
    val on_yes = (dialog:AlertDialog) => {
      val edit_text = dialog.findViewById(R.id.conf_read_order_each_custom_text).asInstanceOf[EditText]
      parseCustomOrder(edit_text.getText.toString) match {
        case None => {
          // TODO: warn that text is invalid
        }
        case Some(txt:String) => {
          val edit = Globals.prefs.get.edit
          edit.putString("read_order_each_custom",txt)
          edit.commit()
        }
      }
      ()
    }

    view.findViewById(R.id.conf_read_order_each_custom_edit).asInstanceOf[Button].setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        Utils.confirmDialogAlt(context,Right(R.string.conf_read_order_each_custom_description),on_yes,custom = custom)
      }
    })

    listener = Some(new SharedPreferences.OnSharedPreferenceChangeListener{
            override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
              if(key == "read_order_each_custom"){
                updateCustomCurrent()
              }
            }})
    updateCustomCurrent()
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(listener.get)

    return view
  }

  def updateCustomCurrent(){
    root_view.foreach{ v => 
      val prefs = Globals.prefs.get
      val abbr = if(prefs.contains("read_order_each_custom")){
        toAbbrValue(prefs.getString("read_order_each_custom",DEFAULT_VALUE))
      }else{
        context.getResources.getString(R.string.conf_read_order_each_custom_undefined)
      }
      v.findViewById(R.id.conf_read_order_each_custom).asInstanceOf[RadioButton].setText(
        context.getResources.getString(R.string.conf_read_order_each_custom) + " (" + abbr + ")"
      )
    }
  }

  def toAbbrValue(value:String):String = {
    val res = context.getResources
    value.replaceFirst("NEXT","/").collect{
      case '1' => res.getString(R.string.read_order_abbr_1st)
      case '2' => res.getString(R.string.read_order_abbr_2nd)
      case '/' => "/"
    }.mkString("-").replace("-/-","/")
  }
  override def getAbbrValue():String = {
    val value = getPersistedString(DEFAULT_VALUE)
    toAbbrValue(value)
  }
}


class AutoPlayPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val DEFAULT_VALUE = 5
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def getWidgets(view:View) = {
    val span = view.findViewById(R.id.autoplay_span).asInstanceOf[TextView]
    val enable = view.findViewById(R.id.autoplay_enable).asInstanceOf[CheckBox]
    val repeat = view.findViewById(R.id.autoplay_repeat).asInstanceOf[CheckBox]
    val play_after_swipe = view.findViewById(R.id.play_after_swipe).asInstanceOf[CheckBox]
    (enable,span,repeat,play_after_swipe)
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (enable,span,repeat,play_after_swipe) = getWidgets(view)
        edit.putBoolean("autoplay_enable",enable.isChecked)
        edit.putBoolean("autoplay_repeat",repeat.isChecked)
        edit.putBoolean("play_after_swipe",play_after_swipe.isChecked)
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
    val (enable,span,repeat,play_after_swipe) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("autoplay_enable",false))
    repeat.setChecked(prefs.getBoolean("autoplay_repeat",false))
    play_after_swipe.setChecked(prefs.getBoolean("play_after_swipe",false))
    span.setText(prefs.getLong("autoplay_span",DEFAULT_VALUE).toString)
    switchVisibilityByCheckBox(root_view,enable,R.id.autoplay_layout)
    return view
  }
  override def getAbbrValue():String = {
    val p = Globals.prefs.get
    val r = context.getResources
    val auto = p.getBoolean("autoplay_enable",false)
    val play_after_swipe = p.getBoolean("play_after_swipe",false)
    if(auto || play_after_swipe){
      val rr = new mutable.MutableList[String]
      if(auto){
        rr += p.getLong("autoplay_span",DEFAULT_VALUE) + r.getString(R.string.conf_unit_second) +
          (if(p.getBoolean("autoplay_repeat",false)){
            "\u21a9" // U+21A9 leftwards arrow with hook
          }else{""})
      }
      if(play_after_swipe){
        rr += "\u2194" // left right arrow
      }
      rr.mkString("/")
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
    switchVisibilityByCheckBox(root_view,enable,R.id.karafuda_layout)
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
      "* " + (100 * Utils.parseFloat(volume)).toInt.toString + " *"
    }
  }


  override def onDialogClosed(positiveResult:Boolean){
    Globals.current_config_dialog = None
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
          Utils.formatFloat("%.2f" , seek.getProgress.toFloat / seek.getMax.toFloat)
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
    // we have to access to the current dialog inside KarutaPlayUtils.doAfterConfiguration()
    Globals.current_config_dialog = Some(this)

    Globals.player.foreach{ pl =>
      pl.set_audio_volume = false
    }
    val view = LayoutInflater.from(context).inflate(R.layout.audio_volume,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)

    KarutaPlayUtils.setAudioPlayButton(view,context)

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
