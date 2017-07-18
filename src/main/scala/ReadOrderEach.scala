package karuta.hpnpwd.wasuramoti
import android.support.v7.preference.{DialogPreference,PreferenceDialogFragmentCompat}
import android.content.{Context,SharedPreferences,DialogInterface}
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.os.Bundle
import android.widget.{RadioGroup,EditText,TextView}
import android.support.v7.app.AlertDialog

class ReadOrderEachPreferenceFragment extends PreferenceDialogFragmentCompat with SharedPreferences.OnSharedPreferenceChangeListener {
  var root_view = None:Option[View]
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[ReadOrderEachPreference]
    if(positiveResult){
      val group = root_view.get.findViewById(R.id.conf_read_order_each_group).asInstanceOf[RadioGroup]
      val bid = group.getCheckedRadioButtonId()
      if(bid == R.id.conf_read_order_each_custom){
        pref.persistString(Globals.prefs.get.getString("read_order_each_custom",pref.DEFAULT_VALUE))
      }else{
        val vw = group.findViewById(bid)
        pref.persistString(vw.getTag.asInstanceOf[String].toUpperCase)
      }
    }
  }

  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[ReadOrderEachPreference]
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_each,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    val group = view.findViewById(R.id.conf_read_order_each_group).asInstanceOf[RadioGroup]
    GeneralRadioHelper.setRadioTextClickListener(group)
    val value = pref.getPersistedString(pref.DEFAULT_VALUE)
    val vw = group.findViewWithTag(value.toLowerCase)
    if(vw == null){
      if(value != Globals.prefs.get.getString("read_order_each_custom",pref.DEFAULT_VALUE)){
        // migrate from abolished option
        val edit = Globals.prefs.get.edit
        edit.putString("read_order_each_custom",value)
        edit.commit()
      }
      group.check(R.id.conf_read_order_each_custom)
    }else{
      group.check(vw.getId())
    }

    // Android has a bug RadioGroup.OnCheckedChangeListener is called twice when calling RadioGroup.check().
    // Therefore we use View.OnClickListener instead.
    //   https://issuetracker.google.com/code/p/android/issues/detail?id=4785
    val custom = view.findViewById(R.id.conf_read_order_each_custom)
    custom.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        new ReadOrderEachCustomDialog(context).show()
      }
    })
    val custom_text = view.findViewById(R.id.conf_read_order_value_custom)
    custom_text.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        // group.check() does not trigger OnClickListener, so we have to show dialog after it.
        group.check(R.id.conf_read_order_each_custom)
        new ReadOrderEachCustomDialog(context).show()
      }
    })

    updateCustomCurrent()
    return view
  }

  def updateCustomCurrent(){
    val pref = getPreference.asInstanceOf[ReadOrderEachPreference]
    root_view.foreach{ v =>
      val prefs = Globals.prefs.get
      val abbr = if(prefs.contains("read_order_each_custom")){
        pref.toAbbrValue(prefs.getString("read_order_each_custom",pref.DEFAULT_VALUE))
      }else{
        getContext.getResources.getString(R.string.conf_read_order_value_undefined)
      }
      v.findViewById(R.id.conf_read_order_value_custom).asInstanceOf[TextView].setText(abbr)
    }
  }

  override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
    if(key == "read_order_each_custom"){
      updateCustomCurrent()
    }
  }

  override def onResume(){
    super.onResume()
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(this)
  }
  override def onPause(){
    super.onPause()
    Globals.prefs.get.unregisterOnSharedPreferenceChangeListener(this)
  }
}

class ReadOrderEachPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref{
  val DEFAULT_VALUE = "CUR2_NEXT1"
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def toAbbrValue(value:String):String = {
    val res = context.getResources
    val ar = res.getStringArray(R.array.conf_read_order_abbr_presets)
    ar.find(_.startsWith(value+":")).map(_.split(":")(1)).getOrElse(
      value.replaceFirst("NEXT","/").collect{
        case '1' => res.getString(R.string.read_order_abbr_1st)
        case '2' => res.getString(R.string.read_order_abbr_2nd)
        case '/' => "/"
      }.mkString("-").replace("-/-","/")
    )
  }
  override def getAbbrValue():String = {
    val value = getPersistedString(DEFAULT_VALUE)
    toAbbrValue(value)
  }


}


class ReadOrderEachCustomDialog(context:Context) extends CustomAlertDialog(context){
  def parseCustomOrder(str:String):Either[String,Int] = {
    val s = str.split(Array('/','.',',')).zip(Array("CUR","NEXT")).map{case (s,t) => s.filter("12".contains(_)).map(t+_)}.flatten.mkString("_")
    if(s.startsWith("CUR")){
      Left(s)
    }else{
      Right(R.string.conf_read_order_each_custom_must_include_cur)
    }
  }
  def toOrigValue(value:String):String = {
    value.replaceFirst("NEXT","/").filter("12/".contains(_))
  }

  override def doWhenClose():Boolean = {
    val edit_text = findViewById(R.id.conf_read_order_each_custom_text).asInstanceOf[EditText]
    parseCustomOrder(edit_text.getText.toString) match {
      case Left(txt:String) => {
        val edit = Globals.prefs.get.edit
        edit.putString("read_order_each_custom",txt)
        edit.commit()
        true
      }
      case Right(msg_id) => {
        CommonDialog.messageDialog(context,Right(msg_id))
        false
      }
    }
  }
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_each_custom,null)
    setView(view)
    setTitle(context.getString(R.string.conf_read_order_each_custom_title))
    val edit_text = view.findViewById(R.id.conf_read_order_each_custom_text).asInstanceOf[EditText]
    edit_text.setText(toOrigValue(Globals.prefs.get.getString("read_order_each_custom","")))
    super.onCreate(bundle)
  }
}
