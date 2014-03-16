package karuta.hpnpwd.wasuramoti
import _root_.android.preference.DialogPreference
import _root_.android.content.Context
import _root_.android.util.AttributeSet
import _root_.android.text.Html
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox,Spinner,AdapterView}
import _root_.android.media.AudioManager
import _root_.android.text.TextUtils

class YomiInfoPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val ENTRY_VALUE_ID = R.array.conf_show_yomi_info_entryValues
  var root_view = None:Option[View]
  override def getAbbrValue():String = {
    val v = Globals.prefs.get.getString("show_yomi_info","None")
    val index = getIndexFromValue(v)
    val res = context.getResources
    var base = res.getStringArray(R.array.conf_show_yomi_info_entries_abbr)(index)
    if(v != "None"){
      val ex = new StringBuilder()
      if(Globals.prefs.get.getBoolean("yomi_info_author",false)){
        ex.append(res.getString(R.string.yomi_info_abbrev_author))
      }
      val kami = Globals.prefs.get.getBoolean("yomi_info_kami",true)
      val simo = Globals.prefs.get.getBoolean("yomi_info_simo",true)
      (kami,simo) match {
        case (true,true) => ex.append(res.getString(R.string.yomi_info_abbrev_both))
        case (true,false) => ex.append(res.getString(R.string.yomi_info_abbrev_kami))
        case (false,true) => ex.append(res.getString(R.string.yomi_info_abbrev_simo))
        case _ => Unit
      }
      if(Globals.prefs.get.getString("yomi_info_furigana","None") != "None"){
        ex.append(res.getString(R.string.yomi_info_abbrev_furigana))
      }
      if(ex.length > 0){
        base += "/" + ex.toString
      }
    }
    base
  }
  def getWidgets(view:View) = {
    val main = view.findViewById(R.id.yomi_info_main).asInstanceOf[Spinner]
    val furigana = view.findViewById(R.id.yomi_info_furigana).asInstanceOf[Spinner]
    val furigana_size = view.findViewById(R.id.yomi_info_furigana_width).asInstanceOf[SeekBar]
    val author = view.findViewById(R.id.yomi_info_author).asInstanceOf[CheckBox]
    val kami = view.findViewById(R.id.yomi_info_kami).asInstanceOf[CheckBox]
    val simo = view.findViewById(R.id.yomi_info_simo).asInstanceOf[CheckBox]
    (main,furigana,furigana_size,author,kami,simo)
  }
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def getIndexFromValue(value:String,id:Int=ENTRY_VALUE_ID) = {
    try{
      context.getResources.getStringArray(id).indexOf(value)
    }catch{
      case _:IndexOutOfBoundsException => 0
    }
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (main,furigana,furigana_size,author,kami,simo) = getWidgets(view)
        val ar = context.getResources.getStringArray(ENTRY_VALUE_ID)
        edit.putString("show_yomi_info",ar(main.getSelectedItemPosition))
        edit.putString("yomi_info_furigana",ar(furigana.getSelectedItemPosition))
        edit.putInt("yomi_info_furigana_width",furigana_size.getProgress)
        edit.putBoolean("yomi_info_author",author.isChecked)
        edit.putBoolean("yomi_info_kami",kami.isChecked)
        edit.putBoolean("yomi_info_simo",simo.isChecked)
        edit.commit
        notifyChangedPublic
      }
      Globals.forceRestart = true
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf, null)
    root_view = Some(view)
    val (main,furigana,furigana_size,author,kami,simo) = getWidgets(view)
    val prefs = Globals.prefs.get
    main.setSelection(getIndexFromValue(prefs.getString("show_yomi_info","None")))
    furigana.setSelection(getIndexFromValue(prefs.getString("yomi_info_furigana","None")))
    furigana_size.setProgress(prefs.getInt("yomi_info_furigana_width",context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)))

    author.setChecked(prefs.getBoolean("yomi_info_author",false))
    kami.setChecked(prefs.getBoolean("yomi_info_kami",true))
    simo.setChecked(prefs.getBoolean("yomi_info_simo",true))
    
    val usage_view = view.findViewById(R.id.yomi_info_usage).asInstanceOf[TextView]
    usage_view.setText(Html.fromHtml(context.getResources.getString(R.string.yomi_info_usage)))

    // switch visibility when spinner changed
    val layout = view.findViewById(R.id.yomi_info_conf_layout)
    val f = (pos:Int) => {
      layout.setVisibility(if(pos==0){
        View.INVISIBLE
      }else{
        View.VISIBLE
      })
    }
    f(main.getSelectedItemPosition)
    main.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        override def onItemSelected(parent:AdapterView[_],view:View,pos:Int,id:Long){
          f(pos)
        }
        override def onNothingSelected(parent:AdapterView[_]){
        }
    })

    return view
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
    val repeat = view.findViewById(R.id.autoplay_repeat).asInstanceOf[CheckBox]
    (enable,span,repeat)
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (enable,span,repeat) = getWidgets(view)
        edit.putBoolean("autoplay_enable",enable.isChecked)
        edit.putBoolean("autoplay_repeat",repeat.isChecked)
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
    val (enable,span,repeat) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("autoplay_enable",false))
    repeat.setChecked(prefs.getBoolean("autoplay_repeat",false))
    span.setText(prefs.getLong("autoplay_span",DEFAULT_VALUE).toString)
    switchVisibilityByCheckBox(root_view,enable,R.id.autoplay_layout)
    return view
  }
  override def getAbbrValue():String = {
    val p = Globals.prefs.get
    val r = context.getResources
    if(p.getBoolean("autoplay_enable",false)){
      p.getLong("autoplay_span",DEFAULT_VALUE) + r.getString(R.string.conf_unit_second) +
      (if(p.getBoolean("autoplay_repeat",false)){
        "/" + r.getString(R.string.autoplay_repeat_abbrev)
      }else{""})
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
      "* " + (100 * volume.toFloat).toInt.toString + " *"
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

