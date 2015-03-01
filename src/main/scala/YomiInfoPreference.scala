package karuta.hpnpwd.wasuramoti
import _root_.android.preference.DialogPreference
import _root_.android.content.{Context,DialogInterface,SharedPreferences}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{SeekBar,CheckBox,Spinner,AdapterView,ArrayAdapter,Button}
import _root_.android.os.Bundle
import _root_.android.app.{AlertDialog,Dialog}
import _root_.android.support.v4.app.DialogFragment

class YomiInfoPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom with YomiInfoPreferenceTrait{
  var root_view = None:Option[View]
  override def getAbbrValue():String = {
    val v = YomiInfoUtils.getPoemTextFont 
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
      if(Globals.prefs.get.getBoolean("yomi_info_furigana_show",false)){
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
    val furigana_font = view.findViewById(R.id.yomi_info_furigana_font).asInstanceOf[Spinner]
    val furigana_size = view.findViewById(R.id.yomi_info_furigana_width).asInstanceOf[SeekBar]
    val furigana_show = view.findViewById(R.id.yomi_info_furigana_show).asInstanceOf[CheckBox]
    val author = view.findViewById(R.id.yomi_info_author).asInstanceOf[CheckBox]
    val kami = view.findViewById(R.id.yomi_info_kami).asInstanceOf[CheckBox]
    val simo = view.findViewById(R.id.yomi_info_simo).asInstanceOf[CheckBox]
    (main,furigana_font,furigana_size,furigana_show,author,kami,simo)
  }
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (main,furigana_font,furigana_size,furigana_show,author,kami,simo) = getWidgets(view)
        val ar = context.getResources.getStringArray(ENTRY_VALUE_ID)
        YomiInfoUtils.setPoemTextFont(edit,ar(main.getSelectedItemPosition))
        edit.putString("yomi_info_furigana_font",ar(furigana_font.getSelectedItemPosition))
        edit.putInt("yomi_info_furigana_width",furigana_size.getProgress)
        edit.putBoolean("yomi_info_furigana_show",furigana_show.isChecked)
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
    val (main,furigana_font,furigana_size,furigana_show,author,kami,simo) = getWidgets(view)
    val prefs = Globals.prefs.get
    main.setSelection(getIndexFromValue(context,YomiInfoUtils.getPoemTextFont))
    val adapter = new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,
      Array(context.getResources.getString(R.string.yomi_info_sameas_kanji))
      ++ context.getResources.getStringArray(R.array.conf_show_yomi_info_entries).tail
    )
    furigana_font.setAdapter(adapter)
    furigana_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_furigana_font","None")))
    furigana_size.setProgress(prefs.getInt("yomi_info_furigana_width",context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)))

    furigana_show.setChecked(prefs.getBoolean("yomi_info_furigana_show",false))
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

    val btn_detail = view.findViewById(R.id.yomi_info_conf_button_detail).asInstanceOf[Button]
    btn_detail.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          val dlg = new YomiInfoConfigDetailDialog(context)
          dlg.show
        }
    })
    val btn_trans = view.findViewById(R.id.yomi_info_conf_button_translation).asInstanceOf[Button]
    btn_trans.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          val dlg = new YomiInfoConfigTranslateDialog(context)
          dlg.show
        }
    })


    builder.setView(view)

    super.onPrepareDialogBuilder(builder)
  }
}

class YomiInfoConfigDetailDialog(context:Context) extends AlertDialog(context) with YomiInfoPreferenceTrait with YomiInfoPreferenceSubDialogTrait{
  def getWidgets(view:View) = {
    val show_kimari = view.findViewById(R.id.yomi_info_show_bar_kimari).asInstanceOf[CheckBox]
    val show_btn = view.findViewById(R.id.yomi_info_show_info_button).asInstanceOf[CheckBox]
    val torifuda_font =  view.findViewById(R.id.yomi_info_torifuda_font).asInstanceOf[Spinner]
    val torifuda_mode =  view.findViewById(R.id.yomi_info_torifuda_mode).asInstanceOf[Spinner]
    (show_kimari,show_btn,torifuda_font,torifuda_mode)
  }
  override def doWhenClose(view:View){
    val edit = Globals.prefs.get.edit
    val (show_kimari,show_btn,torifuda_font,torifuda_mode) = getWidgets(view)
    val ar = context.getResources.getStringArray(ENTRY_VALUE_ID)
    edit.putBoolean("yomi_info_show_bar_kimari",show_kimari.isChecked)
    edit.putBoolean("yomi_info_show_info_button",show_btn.isChecked)
    edit.putString("yomi_info_torifuda_font",ar(torifuda_font.getSelectedItemPosition))
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
      Array(context.getResources.getString(R.string.yomi_info_sameas_yomifuda))
      ++ context.getResources.getStringArray(R.array.conf_show_yomi_info_entries).tail
    )
    torifuda_font.setAdapter(adapter)
    torifuda_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_torifuda_font","None")))
    torifuda_mode.setSelection(if(prefs.getBoolean("yomi_info_torifuda_mode",false)){1}else{0})
    setTitle(R.string.yomi_info_conf_detail_title)
    setViewAndButton(view)
    super.onCreate(state)
  }
}

class YomiInfoConfigTranslateDialog(context:Context) extends AlertDialog(context) with YomiInfoPreferenceTrait with YomiInfoPreferenceSubDialogTrait{
  def getWidgets(view:View) = {
    val english_font =  view.findViewById(R.id.yomi_info_english_font).asInstanceOf[Spinner]
    val default_lang =  view.findViewById(R.id.yomi_info_default_language).asInstanceOf[Spinner]
    val show_button = view.findViewById(R.id.yomi_info_show_translate_button).asInstanceOf[CheckBox]
    (english_font,default_lang,show_button)
  }
  override def doWhenClose(view:View){
    val edit = Globals.prefs.get.edit
    val (english_font,default_lang,show_button) = getWidgets(view)
    val ar = context.getResources.getStringArray(R.array.yomi_info_english_fonts_values)
    edit.putString("yomi_info_english_font",ar(english_font.getSelectedItemPosition))
    edit.putBoolean("yomi_info_show_translate_button",show_button.isChecked)
    edit.putBoolean("yomi_info_default_lang_is_jpn",default_lang.getSelectedItemPosition == 0)
    edit.commit
    Globals.forceRestart = true
  }
    
  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf_translation, null)

    val (english_font,default_lang,show_button) = getWidgets(view)
    val prefs = Globals.prefs.get

    show_button.setChecked(prefs.getBoolean("yomi_info_show_translate_button",!Romanization.is_japanese(context)))
    english_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_english_font","Serif"),R.array.yomi_info_english_fonts_values))
    default_lang.setSelection(if(prefs.getBoolean("yomi_info_default_lang_is_jpn",true)){0}else{1})

    setTitle(R.string.yomi_info_conf_translation_title)
    setViewAndButton(view)
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

trait YomiInfoPreferenceSubDialogTrait{
  self:AlertDialog =>
  def doWhenClose(view:View)
  def setViewAndButton(view:View){
    self.setView(view)
    self.setButton(DialogInterface.BUTTON_POSITIVE,self.getContext.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        doWhenClose(view)
        dismiss()
      }
    })
    self.setButton(DialogInterface.BUTTON_NEGATIVE,self.getContext.getResources.getString(android.R.string.cancel),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        dismiss()
      }
    })
  }
}
class QuickConfigDialog extends DialogFragment{
  override def onCreateDialog(saved:Bundle):Dialog = {
    val listener = new DialogInterface.OnClickListener{
      override def onClick(dialog:DialogInterface,which:Int){
        val edit = Globals.prefs.get.edit
        which match{
          case 0 =>
            // Hide Poem Text
            YomiInfoUtils.hidePoemText(edit)
          case 1 =>
            // Full Poem Text
            YomiInfoUtils.showFull(edit)
          case 2 =>
            // Fuda Nagashi
            YomiInfoUtils.setPoemTextVisibility(edit,true)
            edit.putBoolean("yomi_info_torifuda_mode",true)
            edit.putBoolean("yomi_info_show_bar_kimari",false)
            edit.putBoolean("yomi_info_default_lang_is_jpn",true)
          case 3 =>
            // Only 1st Half
            YomiInfoUtils.showOnlyFirst(edit)
          case 4 =>
            // English Mode
            YomiInfoUtils.setPoemTextVisibility(edit,true)
            edit.putBoolean("yomi_info_kami",true)
            edit.putBoolean("yomi_info_simo",true)
            edit.putBoolean("yomi_info_default_lang_is_jpn",false)
        }
        edit.commit
        dismiss
        Utils.restartActivity(getActivity)
      }
    }
    val builder = new AlertDialog.Builder(getActivity)
    builder
    .setTitle(getActivity.getString(R.string.menu_quick_conf))
    .setItems(R.array.quick_conf_list,listener)
    .setNegativeButton(android.R.string.cancel,null)
    .create
  }
}

object YomiInfoUtils{
  def getShowYomiInfo(pref:SharedPreferences):Array[String] = {
    pref.getString("show_yomi_info","None").split(";")
  }
  def showPoemText():Boolean = {
    Globals.prefs.exists{getShowYomiInfo(_).head != "None"}
  }
  def getPoemTextFont():String = {
    Globals.prefs.map{getShowYomiInfo(_).head}.getOrElse("None")
  }
  def setPoemTextFont(edit:SharedPreferences.Editor,str:String){
    val old = getPoemTextFont
    edit.putString("show_yomi_info",str + ";" + old)
  }
  def setPoemTextVisibility(edit:SharedPreferences.Editor,show:Boolean){
    var ar = getShowYomiInfo(Globals.prefs.get).filter{_ != "None"}
    if(ar.isEmpty){
      ar = Array("asset:tfont-kaisho.ttf")
    }
    val (n,old) = if(show){
      (ar.head,"None")
    }else{
      ("None",ar.head)
    }
    edit.putString("show_yomi_info",n + ";" + old)
  }
  def hidePoemText(edit:SharedPreferences.Editor){
    setPoemTextVisibility(edit,false) 
  }
  def showFull(edit:SharedPreferences.Editor){
    setPoemTextVisibility(edit,true)
    edit.putBoolean("yomi_info_torifuda_mode",false)
    edit.putBoolean("yomi_info_show_bar_kimari",true)
    edit.putBoolean("yomi_info_kami",true)
    edit.putBoolean("yomi_info_simo",true)
    edit.putBoolean("yomi_info_author",true)
    edit.putBoolean("yomi_info_furigana_show",true)
    edit.putBoolean("yomi_info_default_lang_is_jpn",true)
  }
  def showOnlyFirst(edit:SharedPreferences.Editor){
    setPoemTextVisibility(edit,true)
    edit.putBoolean("yomi_info_torifuda_mode",false)
    edit.putBoolean("yomi_info_show_bar_kimari",true)
    edit.putBoolean("yomi_info_kami",true)
    edit.putBoolean("yomi_info_simo",false)
    edit.putBoolean("yomi_info_author",false)
    edit.putBoolean("yomi_info_furigana_show",true)
    edit.putBoolean("yomi_info_default_lang_is_jpn",true)
  }
}
