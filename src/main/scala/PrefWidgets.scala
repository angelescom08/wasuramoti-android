package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.widget.TextView
import android.support.v7.preference.{Preference,DialogPreference,PreferenceDialogFragmentCompat,PreferenceViewHolder}
import android.support.v7.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,RadioGroup,RadioButton,SeekBar,CheckBox,CompoundButton,LinearLayout}

import scala.reflect.ClassTag
import scala.collection.mutable
import scala.util.Try

object PrefUtils {
  def switchVisibilityByCheckBox(root_view:Option[View],checkbox:CheckBox,layout_id:Int){
    // layout_id must be <LinearLayout android:layout_height="wrap_content" ..>
    val f = (isChecked:Boolean) => {
      root_view.foreach{ root =>
        val layout = root.findViewById(layout_id)
        if(layout != null){
          val lp = layout.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
          if(isChecked){
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layout.setVisibility(View.VISIBLE)
          }else{
            lp.height = 0
            layout.setVisibility(View.INVISIBLE)
          }
          layout.setLayoutParams(lp)
        }
      }
    }
    f(checkbox.isChecked)
    checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
        override def onCheckedChanged(btn:CompoundButton,isChecked:Boolean){
          f(isChecked)
        }
      })
  }
}

trait CustomPref extends Preference{
  self:{ def getKey():String; def onBindViewHolder(v:PreferenceViewHolder); def notifyChanged()} =>
  abstract override def onBindViewHolder(v:PreferenceViewHolder) {
    v.findViewById(R.id.conf_current_value).asInstanceOf[TextView].setText(getAbbrValue())
    super.onBindViewHolder(v)
  }
  def getAbbrValue():String = {
    super.getPersistedString("")
  }
  // We have to call notifyChanged() to to reflect the change to view.
  // However, notifyChanged() is protected method. Therefore we use this method instead.
  def notifyChangedPublic(){
    super.notifyChanged()
  }

  override def getPersistedString(default:String):String = {
    super.getPersistedString(default)
  }
  override def persistString(value:String):Boolean = {
    super.persistString(value)
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
      view.findViewWithTag(t).asInstanceOf[RadioButton].toggle()
    }
    val enable = view.findViewById(R.id.joka_enable).asInstanceOf[CheckBox]
    enable.setChecked(Globals.prefs.get.getBoolean("joka_enable",true))
    PrefUtils.switchVisibilityByCheckBox(root_view,enable,R.id.read_order_joka_layout)
    return view
  }
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[JokaOrderPreference]
    if(positiveResult){
      val read_order_joka = Array(R.id.conf_joka_upper_num,R.id.conf_joka_lower_num).map{ rid =>
        val btn = root_view.get.findViewById(rid).asInstanceOf[RadioGroup].getCheckedRadioButtonId()
        root_view.get.findViewById(btn).getTag()
      }.mkString(",")
      val edit = pref.getSharedPreferences.edit
      val enable = root_view.get.findViewById(R.id.joka_enable).asInstanceOf[CheckBox]
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
    val span = view.findViewById(R.id.autoplay_span).asInstanceOf[TextView]
    val enable = view.findViewById(R.id.autoplay_enable).asInstanceOf[CheckBox]
    val repeat = view.findViewById(R.id.autoplay_repeat).asInstanceOf[CheckBox]
    val stop = view.findViewById(R.id.autoplay_stop).asInstanceOf[CheckBox]
    val stop_minutes = view.findViewById(R.id.autoplay_stop_minutes).asInstanceOf[TextView]
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
