package karuta.hpnpwd.wasuramoti

import android.preference.DialogPreference
import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox}
import android.media.AudioManager
import android.text.{TextUtils,Html}

import scala.collection.mutable
import scala.util.Try

class AutoPlayPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val DEFAULT_VALUE = 3
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def getWidgets(view:View) = {
    val span = view.findViewById(R.id.autoplay_span).asInstanceOf[TextView]
    val enable = view.findViewById(R.id.autoplay_enable).asInstanceOf[CheckBox]
    val repeat = view.findViewById(R.id.autoplay_repeat).asInstanceOf[CheckBox]
    val stop = view.findViewById(R.id.autoplay_stop).asInstanceOf[CheckBox]
    val stop_minutes = view.findViewById(R.id.autoplay_stop_minutes).asInstanceOf[TextView]
    (enable,span,repeat,stop,stop_minutes)
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (enable,span,repeat,stop,stop_minutes) = getWidgets(view)
        edit.putBoolean("autoplay_enable",enable.isChecked)
        edit.putBoolean("autoplay_repeat",repeat.isChecked)
        edit.putLong("autoplay_span",Math.max(1,Try{span.getText.toString.toInt}.getOrElse(1)))
        edit.putBoolean("autoplay_stop",stop.isChecked)
        edit.putLong("autoplay_stop_minutes",Math.max(1,Try{stop_minutes.getText.toString.toInt}.getOrElse(30)))
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
    val (enable,span,repeat,stop,stop_minutes) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("autoplay_enable",false))
    repeat.setChecked(prefs.getBoolean("autoplay_repeat",false))
    span.setText(prefs.getLong("autoplay_span",DEFAULT_VALUE).toString)
    stop.setChecked(prefs.getBoolean("autoplay_stop",false))
    stop_minutes.setText(prefs.getLong("autoplay_stop_minutes",30).toString)
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
class DescriptionPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  // value of AttributeSet can only be acquired in constructor
  val message = attrs.getAttributeResourceValue(null,"message",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }

  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
    txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,message)))
    builder.setView(view)
    super.onPrepareDialogBuilder(builder)
  }
}
