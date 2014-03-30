package karuta.hpnpwd.wasuramoti
import _root_.android.preference.DialogPreference
import _root_.android.content.{Context,DialogInterface}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{SeekBar,CheckBox,Spinner,AdapterView,ArrayAdapter}
import _root_.android.os.Bundle
import _root_.android.app.AlertDialog

class YomiInfoPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom with YomiInfoPreferenceTrait{
  var root_view = None:Option[View]
  override def getAbbrValue():String = {
    val v = Globals.prefs.get.getString("show_yomi_info","None")
    val index = getIndexFromValue(context,v)
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
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf, null)
    root_view = Some(view)
    val (main,furigana,furigana_size,author,kami,simo) = getWidgets(view)
    val prefs = Globals.prefs.get
    main.setSelection(getIndexFromValue(context,prefs.getString("show_yomi_info","None")))
    furigana.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_furigana","None")))
    furigana_size.setProgress(prefs.getInt("yomi_info_furigana_width",context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)))

    author.setChecked(prefs.getBoolean("yomi_info_author",false))
    kami.setChecked(prefs.getBoolean("yomi_info_kami",true))
    simo.setChecked(prefs.getBoolean("yomi_info_simo",true))
    
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
    builder.setView(view)

    builder.setNeutralButton(R.string.button_config_detail, new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          val dlg = new YomiInfoConfigDetailDialog(context,{_:Unit=>dialog.asInstanceOf[AlertDialog].show()})
          dlg.show
        }
      })

    super.onPrepareDialogBuilder(builder)
  }
}

class YomiInfoConfigDetailDialog(context:Context,after_done:Unit=>Unit) extends AlertDialog(context) with YomiInfoPreferenceTrait{
  def getWidgets(view:View) = {
    val show_kimari = view.findViewById(R.id.yomi_info_show_bar_kimari).asInstanceOf[CheckBox]
    val show_btn = view.findViewById(R.id.yomi_info_show_info_button).asInstanceOf[CheckBox]
    val torifuda_font =  view.findViewById(R.id.yomi_info_torifuda_font).asInstanceOf[Spinner]
    val torifuda_mode =  view.findViewById(R.id.yomi_info_torifuda_mode).asInstanceOf[Spinner]
    (show_kimari,show_btn,torifuda_font,torifuda_mode)
  }
  def doWhenClose(view:View){
    val edit = Globals.prefs.get.edit
    val (show_kimari,show_btn,torifuda_font,torifuda_mode) = getWidgets(view)
    val ar = context.getResources.getStringArray(ENTRY_VALUE_ID)
    edit.putBoolean("yomi_info_show_bar_kimari",show_kimari.isChecked)
    edit.putBoolean("yomi_info_show_info_button",show_btn.isChecked)
    edit.putString("yomi_info_torifuda_font",ar(torifuda_font.getSelectedItemPosition+1))
    edit.putBoolean("yomi_info_torifuda_mode",torifuda_mode.getSelectedItemPosition == 1)
    edit.commit
    Globals.forceRestart = true
  }
    
  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf_detail, null)

    val (show_kimari,show_btn,torifuda_font,torifuda_mode) = getWidgets(view)
    val prefs = Globals.prefs.get

    show_kimari.setChecked(prefs.getBoolean("yomi_info_show_bar_kimari",true))
    show_btn.setChecked(prefs.getBoolean("yomi_info_show_info_button",true))

    val adapter = new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,
      context.getResources.getStringArray(R.array.conf_show_yomi_info_entries).tail
    )
    torifuda_font.setAdapter(adapter)
    torifuda_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_torifuda_font","Default"))-1)
    torifuda_mode.setSelection(if(prefs.getBoolean("yomi_info_torifuda_mode",false)){1}else{0})
    setView(view)
    setTitle(R.string.yomi_info_conf_detail_title)
    setButton(DialogInterface.BUTTON_POSITIVE,context.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
            override def onClick(dialog:DialogInterface,which:Int){
                doWhenClose(view)
                dismiss()
                after_done()
            }
        })
    setButton(DialogInterface.BUTTON_NEGATIVE,context.getResources.getString(android.R.string.cancel),new DialogInterface.OnClickListener(){
            override def onClick(dialog:DialogInterface,which:Int){
                dismiss()
                after_done()
            }
        })
    super.onCreate(state)
  }
}

trait YomiInfoPreferenceTrait{
  val ENTRY_VALUE_ID = R.array.conf_show_yomi_info_entryValues
  def getIndexFromValue(context:Context,value:String,id:Int=ENTRY_VALUE_ID) = {
    try{
      context.getResources.getStringArray(id).indexOf(value)
    }catch{
      case _:IndexOutOfBoundsException => 0
    }
  }
}
