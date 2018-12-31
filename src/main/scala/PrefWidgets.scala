package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.widget.TextView
import android.support.v7.preference.{Preference,DialogPreference,PreferenceDialogFragmentCompat,PreferenceViewHolder}
import android.support.v7.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox}
import android.text.{TextUtils,Html}
import android.media.AudioManager

import scala.reflect.ClassTag
import scala.collection.mutable
import scala.util.Try

import java.util.regex.Pattern

trait CustomPref extends Preference{
  self:{ def getKey():String; def onBindViewHolder(v:PreferenceViewHolder); def notifyChanged()} =>
  abstract override def onBindViewHolder(v:PreferenceViewHolder) {
    Option(v.findViewById(R.id.conf_current_value).asInstanceOf[TextView])
      .foreach{_.setText(getAbbrValue)}
    super.onBindViewHolder(v)
  }
  def getAbbrValue():String = {
    super.getPersistedString("")
  }

  // The following methods are not public methods, so we override it to be public

  // We have to call notifyChanged() to to reflect the change to view.
  // TODO: change signature to `override def notifyChanged()`
  def notifyChangedPublic(){
    super.notifyChanged()
  }

  override def getPersistedString(default:String):String = {
    super.getPersistedString(default)
  }
  override def persistString(value:String):Boolean = {
    super.persistString(value)
  }
  override def getPersistedBoolean(default:Boolean):Boolean = {
    super.getPersistedBoolean(default)
  }
  override def persistBoolean(value:Boolean):Boolean = {
    super.persistBoolean(value)
  }
}

object PrefWidgets {
  // shuld be same as PreferenceDialogFragmentCompat.ARG_KEY
  val ARG_KEY = "key"
  def newInstance[C <: PreferenceDialogFragmentCompat](key:String)(implicit tag:ClassTag[C]):C = {
    val fragment = tag.runtimeClass.getConstructor().newInstance().asInstanceOf[C]
    val bundle = new Bundle(1)
    bundle.putString(ARG_KEY, key)
    fragment.setArguments(bundle)
    return fragment
  }
}

class ReadOrderPreferenceFragment extends PreferenceDialogFragmentCompat {
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val context = getContext
    val pref = getPreference.asInstanceOf[ReadOrderPreference]
    val helper = new GeneralRadioHelper(context,builder)
    val ar = context.getResources.getStringArray(R.array.conf_read_order_entries)
    // TODO: use View.generateViewId() for API >= 17
    val ids = context.getResources.obtainTypedArray(R.array.general_radio_helper)
    val id2key = mutable.Map[Int,String]()
    val persisted = pref.getPersistedString("SHUFFLE")
    var currentId = None:Option[Int]
    val items = ar.zipWithIndex.map{ case (x,i) =>
      val id = ids.getResourceId(i,-1)
      if(id == -1){
        throw new RuntimeException(s"index out of range for general_radio_helper[${i}]")
      }
      val Array(key,title,desc) = x.split("\\|")
      id2key += ((id,key))
      if(key == persisted){
        currentId = Some(id)
      }
      GeneralRadioHelper.Item(id,Right(title),Right(desc))
    }
    val handler = (id:Int) => {
      id2key.get(id).foreach(pref.persistString(_))
      getDialog.dismiss()
    }
    helper.setDescription(R.string.conf_read_order_desc)
    helper.addItems(items, Some(handler))
    currentId.foreach(helper.radio_group.check(_))
    builder.setPositiveButton(null,null)
    ids.recycle()
    super.onPrepareDialogBuilder(builder)
  }
  override def onDialogClosed(positiveResult:Boolean){
  }
}

class ReadOrderPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String={
    val persisted = getPersistedString("SHUFFLE")
    val ar = context.getResources.getStringArray(R.array.conf_read_order_entries)
    for(x <- ar){
      val Array(key,title,_) = x.split("\\|")
      if( key == persisted){
        return title
      }
    }
    return persisted
  }
}

class JokaOrderPreferenceFragment extends PreferenceDialogFragmentCompat {
  val DEFAULT_VALUE = "upper_1,lower_1"
  var root_view = None:Option[View]
  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[JokaOrderPreference]
    super.onCreateDialogView(context)
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_joka,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    for( t <- pref.getPersistedString(DEFAULT_VALUE).split(",")){
      view.findViewWithTag[RadioButton](t).toggle()
    }
    val enable = view.findViewById[CheckBox](R.id.joka_enable)
    enable.setChecked(Globals.prefs.get.getBoolean("joka_enable",true))
    PrefUtils.switchVisibilityByCheckBox(root_view,enable,R.id.read_order_joka_layout)
    return view
  }
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[JokaOrderPreference]
    if(positiveResult){
      val read_order_joka = Array(R.id.conf_joka_upper_num,R.id.conf_joka_lower_num).map{ rid =>
        val btn = root_view.get.findViewById[RadioGroup](rid).getCheckedRadioButtonId()
        root_view.get.findViewById[View](btn).getTag()
      }.mkString(",")
      val edit = pref.getSharedPreferences.edit
      val enable = root_view.get.findViewById[CheckBox](R.id.joka_enable)
      val (eld,roj) = if(read_order_joka == "upper_0,lower_0"){
        (false,DEFAULT_VALUE)
      }else{
        (enable.isChecked,read_order_joka)
      }
      edit.putBoolean("joka_enable",eld)
      edit.putString(pref.getKey,roj)
      edit.commit
      pref.notifyChangedPublic
    }
  }
}
class JokaOrderPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String = {
    if(Globals.prefs.get.getBoolean("joka_enable",true)){
      context.getResources.getString(R.string.intended_use_joka_on)
    }else{
      context.getResources.getString(R.string.intended_use_joka_off)
    }
  }
}

class AutoPlayPreferenceFragment extends PreferenceDialogFragmentCompat {
  var root_view = None:Option[View]
  def getWidgets(view:View) = {
    val span = view.findViewById[TextView](R.id.autoplay_span)
    val enable = view.findViewById[CheckBox](R.id.autoplay_enable)
    val repeat = view.findViewById[CheckBox](R.id.autoplay_repeat)
    val stop = view.findViewById[CheckBox](R.id.autoplay_stop)
    val stop_minutes = view.findViewById[TextView](R.id.autoplay_stop_minutes)
    (enable,span,repeat,stop,stop_minutes)
  }
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[AutoPlayPreference]
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
        pref.notifyChangedPublic
      }
    }
  }
  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[AutoPlayPreference]
    val view = LayoutInflater.from(context).inflate(R.layout.autoplay,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val (enable,span,repeat,stop,stop_minutes) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("autoplay_enable",false))
    repeat.setChecked(prefs.getBoolean("autoplay_repeat",false))
    span.setText(prefs.getLong("autoplay_span",pref.DEFAULT_VALUE).toString)
    stop.setChecked(prefs.getBoolean("autoplay_stop",false))
    stop_minutes.setText(prefs.getLong("autoplay_stop_minutes",30).toString)
    PrefUtils.switchVisibilityByCheckBox(root_view,enable,R.id.autoplay_layout)
    return view
  }
}
class AutoPlayPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref {
  val DEFAULT_VALUE = 3
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
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

class DescriptionPreferenceFragment extends PreferenceDialogFragmentCompat {
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val context = getContext
    val pref = getPreference.asInstanceOf[DescriptionPreference]
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val txtview = view.findViewById[TextView](R.id.general_scroll_body)
    txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,pref.message)))
    builder.setView(view)
    super.onPrepareDialogBuilder(builder)
  }
  override def onDialogClosed(positiveResult:Boolean){
  }
}

class DescriptionPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  // value of AttributeSet can only be acquired in constructor
  val message = attrs.getAttributeResourceValue(null,"message",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }

  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
}

class KarafudaPreferenceFragment extends PreferenceDialogFragmentCompat {
  var root_view = None:Option[View]
  def getWidgets(view:View) = {
    val rand = view.findViewById[SeekBar](R.id.karafuda_urafuda_prob)
    val num = view.findViewById[TextView](R.id.karafuda_append_num)
    val enable = view.findViewById[CheckBox](R.id.karafuda_enable)
    (enable,num,rand)
  }
  override def onCreateDialogView(context:Context):View = {
    val view = LayoutInflater.from(context).inflate(R.layout.karafuda,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val (enable,num,rand) = getWidgets(view)
    val prefs = Globals.prefs.get
    enable.setChecked(prefs.getBoolean("karafuda_enable",false))
    num.setText(prefs.getInt("karafuda_append_num",0).toString)
    rand.setProgress((prefs.getFloat("karafuda_urafuda_prob",0.5f)*rand.getMax).toInt)
    PrefUtils.switchVisibilityByCheckBox(root_view,enable,R.id.karafuda_layout)
    return view
  }
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[KarafudaPreference]
    if(positiveResult){
        val context = getContext // this will be null when call onDialogClosed was call by screen rotate
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
        pref.notifyChangedPublic
        FudaListHelper.updateSkipList(getContext)
      }

    }
  }
}

class KarafudaPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref{
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String={
    if(Globals.prefs.get.getBoolean("karafuda_enable",false)){
      Globals.prefs.get.getInt("karafuda_append_num",0).toString + context.getResources.getString(R.string.karafuda_unit)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
}

class AudioVolumePreferenceFragment extends PreferenceDialogFragmentCompat {
  var root_view = None:Option[View]
  var maybe_context = None:Option[Context]
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[AudioVolumePreference]
    PrefUtils.current_config_dialog = None
    Globals.player.foreach{ p => {
      Globals.global_lock.synchronized{
        if(Globals.is_playing){
          p.stop();
        }
      }
      }}
    if(positiveResult){
      root_view.foreach{ view =>
        val check = view.findViewById[CheckBox](R.id.volume_set_each_play)
        val new_value = if(check.isChecked){
          val seek = view.findViewById[SeekBar](R.id.volume_seek)
          Utils.formatFloat("%.2f" , seek.getProgress.toFloat / seek.getMax.toFloat)
        }else{
          Globals.audio_volume_bkup = None // do not restore audio volume
          pref.notifyChangedPublic()
          ""
        }
        pref.persistString(new_value)
      }
    }
    maybe_context.foreach{
      Utils.restoreAudioVolume(_)
    }
    Globals.player.foreach{ pl =>
      pl.set_audio_volume = true
    }
  }
  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[AudioVolumePreference]
    // we have to access to the current dialog inside KarutaPlayUtils.doAfterConfiguration()
    PrefUtils.current_config_dialog = Some(this)

    Globals.player.foreach{ pl =>
      pl.set_audio_volume = false
    }
    val view = LayoutInflater.from(context).inflate(R.layout.audio_volume,null)
    // getDialog() and getContext() returns null on onDialogClosed(), so we save those.
    root_view = Option(view)
    maybe_context = Option(getContext)

    KarutaPlayUtils.setAudioPlayButton(view,context)

    val check = view.findViewById[CheckBox](R.id.volume_set_each_play)
    check.setChecked(!TextUtils.isEmpty(pref.getPersistedString("")))

    val seek = view.findViewById[SeekBar](R.id.volume_seek)

    pref.withAudioVolume(Unit,{(audio_manager, max_volume, current_volume) =>
      Globals.audio_volume_bkup = Some(current_volume)
    })
    Utils.saveAndSetAudioVolume(context)
    pref.withAudioVolume(Unit,{(audio_manager, max_volume, current_volume) =>
      seek.setProgress(((current_volume.toFloat/max_volume.toFloat)*seek.getMax).toInt)
      seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        override def onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
          val volume = math.min(((progress.toFloat/seek.getMax.toFloat)*max_volume).toInt,max_volume)
          audio_manager.setStreamVolume(Utils.getAudioStreamType(context),volume,0)
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

class AudioVolumePreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref{
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)

  def withAudioVolume(defValue:Any,func:(AudioManager,Int,Int)=>Any):Any = {
    val audio_manager = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    if(audio_manager != null){
      val streamType = Utils.getAudioStreamType(context)
      val max_volume = audio_manager.getStreamMaxVolume(streamType)
      val current_volume = audio_manager.getStreamVolume(streamType)
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
}

class CreditsPreferenceFragment extends PreferenceDialogFragmentCompat {
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val context = getContext
    val suffix = if(Romanization.isJapanese(context)){".ja"}else{""}
    val fp = context.getAssets.open("README"+suffix)
    val pat1 = Pattern.compile(".*__BEGIN_CREDITS__",Pattern.DOTALL)
    val pat2 = Pattern.compile("__END_CREDITS__.*",Pattern.DOTALL)
    val buf = TextUtils.htmlEncode(Utils.readStream(fp))
    .replaceAll("\n","<br>\n")
    .replaceAll(" ","\u00A0")
    .replaceAll("(&lt;.*?&gt;)","<b>$1</b>")
    .replaceAll("%%(.*)","<font color='?attr/creditSpecialColor'><i>$1</i></font>")
    .replaceAll("(https?://[a-zA-Z0-9/._%-]*)","<font color='?attr/creditSpecialColor'><a href='$1'>$1</a></font>")
    fp.close
    val buf2 = pat2.matcher(pat1.matcher(buf).replaceAll("")).replaceAll("")
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val txtview = view.findViewById[TextView](R.id.general_scroll_body)
    txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,buf2)))
    builder.setView(view)
    super.onPrepareDialogBuilder(builder)
  }
  override def onDialogClosed(positiveResult:Boolean){
  }
}

class CreditsPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
}

class ReviewLinkPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
}
