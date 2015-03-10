package karuta.hpnpwd.wasuramoti
import _root_.android.preference.DialogPreference
import _root_.android.content.{Context,SharedPreferences}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{RadioGroup,RadioButton,Button,EditText}
import _root_.android.app.AlertDialog
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
