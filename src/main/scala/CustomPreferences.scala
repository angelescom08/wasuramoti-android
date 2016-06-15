package karuta.hpnpwd.wasuramoti
import android.preference.DialogPreference
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox,Button}
import android.media.AudioManager
import android.text.{TextUtils,Html}
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
      val read_order_joka = Array(R.id.conf_joka_upper_num,R.id.conf_joka_lower_num).map{ rid =>
        val btn = root_view.get.findViewById(rid).asInstanceOf[RadioGroup].getCheckedRadioButtonId()
        root_view.get.findViewById(btn).getTag()
      }.mkString(",")
      val edit = getEditor
      val enable = root_view.get.findViewById(R.id.joka_enable).asInstanceOf[CheckBox]
      val (eld,roj) = if(read_order_joka == "upper_0,lower_0"){
        (false,DEFAULT_VALUE)
      }else{
        (enable.isChecked,read_order_joka)
      }
      edit.putBoolean("joka_enable",eld)
      edit.putString(getKey,roj)
      edit.commit
      notifyChangedPublic
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
    val enable = view.findViewById(R.id.joka_enable).asInstanceOf[CheckBox]
    enable.setChecked(Globals.prefs.get.getBoolean("joka_enable",true))
    switchVisibilityByCheckBox(root_view,enable,R.id.read_order_joka_layout)
    return view
  }
  override def getAbbrValue():String = {
    if(Globals.prefs.get.getBoolean("joka_enable",true)){
      context.getResources.getString(R.string.intended_use_joka_on)
    }else{
      context.getResources.getString(R.string.intended_use_joka_off)
    }
  }
}

class MemorizationPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String={
    if(getPersistedBoolean(false)){
      context.getResources.getString(R.string.message_enabled)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
  def getWidgets(view:View) = {
    val enable = view.findViewById(R.id.memorization_mode_enable).asInstanceOf[CheckBox]
    enable
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = getEditor
        val enable = getWidgets(view)
        val is_c = enable.isChecked
        edit.putBoolean(getKey,is_c)
        if(is_c){
          var changed = false
          if(!YomiInfoUtils.showPoemText){
            YomiInfoUtils.setPoemTextVisibility(edit,true)
            changed = true
          }
          if(!Globals.prefs.get.getBoolean("yomi_info_show_info_button",true)){
            edit.putBoolean("yomi_info_show_info_button",true)
            changed = true
          }
          if(changed){
            Utils.messageDialog(context,Right(R.string.memorization_warn_yomi_info_view))
          }
        }
        edit.commit
        notifyChangedPublic
        Globals.forceRestart = true
        FudaListHelper.updateSkipList(context)
      }
    }
    super.onDialogClosed(positiveResult)
  }

  def genPanel(onlyInFudaset:Boolean):View = {
    val (count,count_not,reset_cond,title) = if(onlyInFudaset){
      val c = FudaListHelper.queryNumbersToRead("= 2")
      val cn = FudaListHelper.queryNumbersToRead("= 0")
      val rc = "WHERE skip = 2"
      val tt = "** " + Globals.prefs.get.getString("fudaset","") + " **"
      (c,cn,rc,tt)
    }else{
      val c = FudaListHelper.countNumbersInFudaList("memorized = 1 AND num > 0")
      val cn = FudaListHelper.countNumbersInFudaList("memorized = 0 AND num >0")
      val rc = ""
      val tt = context.getResources.getString(R.string.memorization_all_poem)
      (c,cn,rc,tt)
    }

    val panel = LayoutInflater.from(context).inflate(R.layout.memorization_panel,null)
    val count_view = panel.findViewWithTag("memorized_count").asInstanceOf[TextView]
    count_view.setText(count.toString)
    val count_view_not = panel.findViewWithTag("not_memorized_count").asInstanceOf[TextView]
    count_view_not.setText(count_not.toString)
    val title_view = panel.findViewWithTag("memorization_panel_title").asInstanceOf[TextView]
    title_view.setText(title)

    val btn_reset = panel.findViewWithTag("reset_memorized").asInstanceOf[Button]
    btn_reset.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          Utils.confirmDialog(context,Right(R.string.memorization_mode_reset_confirm), {()=>
            FudaListHelper.resetMemorized(reset_cond)
            FudaListHelper.updateSkipList(context)
            Utils.messageDialog(context,Right(R.string.memorization_mode_reset_done))
          })
        }
    })
    panel
  }

  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.memorization_conf,null)
    root_view = Some(view)
    val enable = getWidgets(view)
    enable.setChecked(getPersistedBoolean(false))

    val container = view.findViewById(R.id.memorization_panel_container).asInstanceOf[ViewGroup]
    if(FudaListHelper.isBoundedByFudaset){
      container.addView(genPanel(true))
    }
    container.addView(genPanel(false))
    view
  }

}

class AutoPlayPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val DEFAULT_VALUE = 3
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
    val auto = p.getBoolean("autoplay_enable",false)
    if(auto){
      p.getLong("autoplay_span",DEFAULT_VALUE) + r.getString(R.string.conf_unit_second) +
        (if(p.getBoolean("autoplay_repeat",false)){
          "\u21a9" // U+21A9 leftwards arrow with hook
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
        FudaListHelper.updateSkipList(context)
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
      val max_volume = audio_manager.getStreamMaxVolume(Utils.getAudioStreamType)
      val current_volume = audio_manager.getStreamVolume(Utils.getAudioStreamType)
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
          audio_manager.setStreamVolume(Utils.getAudioStreamType,volume,0)
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
