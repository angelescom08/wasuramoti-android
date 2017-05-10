package karuta.hpnpwd.wasuramoti
import android.preference.DialogPreference
import android.content.{Context,SharedPreferences,DialogInterface}
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.os.Bundle
import android.widget.{RadioGroup,EditText,TextView}
import android.app.AlertDialog
class ReadOrderEachPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap
  val DEFAULT_VALUE = "CUR2_NEXT1"
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)


  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val group = root_view.get.findViewById(R.id.conf_read_order_each_group).asInstanceOf[RadioGroup]
      val bid = group.getCheckedRadioButtonId()
      if(bid == R.id.conf_read_order_each_custom){
        persistString(Globals.prefs.get.getString("read_order_each_custom",DEFAULT_VALUE))
      }else{
        val vw = group.findViewById(bid)
        persistString(vw.getTag.asInstanceOf[String].toUpperCase)
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
    GeneralRadioHelper.setRadioTextClickListener(group)
    val value = getPersistedString(DEFAULT_VALUE)
    val vw = group.findViewWithTag(value.toLowerCase)
    if(vw == null){
      if(value != Globals.prefs.get.getString("read_order_each_custom",DEFAULT_VALUE)){
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
        context.getResources.getString(R.string.conf_read_order_value_undefined)
      }
      v.findViewById(R.id.conf_read_order_value_custom).asInstanceOf[TextView].setText(abbr)
    }
  }

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


class ReadOrderEachCustomDialog(context:Context) extends AlertDialog(context){
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
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_each_custom,null)
    setView(view)
    setTitle(context.getString(R.string.conf_read_order_each_custom_title))
    val edit_text = view.findViewById(R.id.conf_read_order_each_custom_text).asInstanceOf[EditText]
    edit_text.setText(toOrigValue(Globals.prefs.get.getString("read_order_each_custom","")))
    val listener_ok = new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          parseCustomOrder(edit_text.getText.toString) match {
            case Left(txt:String) => {
              val edit = Globals.prefs.get.edit
              edit.putString("read_order_each_custom",txt)
              edit.commit()
              dismiss()
            }
            case Right(msg_id) => {
              // we have to re-show this dialog since BUTTON_{POSITIVE,NEGATIVE,NEUTRAL} closes the dialog
              Utils.messageDialog(context,Right(msg_id),{()=>show()})
            }
          }
        }
    }
    val listener_cancel = new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          dismiss()
        }
    }
    setButton(DialogInterface.BUTTON_POSITIVE,context.getResources.getString(android.R.string.ok),listener_ok)
    setButton(DialogInterface.BUTTON_NEGATIVE,context.getResources.getString(android.R.string.cancel),listener_cancel)
    super.onCreate(bundle)
  }
}
