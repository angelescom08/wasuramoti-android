package karuta.hpnpwd.wasuramoti
import android.preference.DialogPreference
import android.content.{Context,DialogInterface,SharedPreferences}
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.widget.{SeekBar,CheckBox,Spinner,Button,CompoundButton}
import android.os.Bundle
import android.app.{AlertDialog,Dialog,Activity}
import android.support.v4.app.DialogFragment

class YomiInfoPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom with YomiInfoPreferenceTrait{
  var root_view = None:Option[View]
  override def getAbbrValue():String = {
    val res = context.getResources
    if(YomiInfoUtils.showPoemText){
      val index = getIndexFromValue(context,YomiInfoUtils.getPoemTextFont)
      val base = res.getStringArray(R.array.conf_yomi_info_japanese_fonts_abbr)(index)
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
        base + "/" + ex.toString
      }else{
        base
      }
    }else{
      res.getString(R.string.yomi_info_abbrev_none)
    }
  }
  def getWidgets(view:View) = {
    val main = view.findViewById(R.id.yomi_info_show_text).asInstanceOf[CheckBox]
    val furigana_size = view.findViewById(R.id.yomi_info_furigana_width).asInstanceOf[SeekBar]
    val furigana_show = view.findViewById(R.id.yomi_info_furigana_show).asInstanceOf[CheckBox]
    val author = view.findViewById(R.id.yomi_info_author).asInstanceOf[CheckBox]
    val kami = view.findViewById(R.id.yomi_info_kami).asInstanceOf[CheckBox]
    val simo = view.findViewById(R.id.yomi_info_simo).asInstanceOf[CheckBox]
    (main,furigana_size,furigana_show,author,kami,simo)
  }
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = Globals.prefs.get.edit
        val (main,furigana_size,furigana_show,author,kami,simo) = getWidgets(view)
        YomiInfoUtils.setPoemTextVisibility(edit,main.isChecked)
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
    val (main,furigana_size,furigana_show,author,kami,simo) = getWidgets(view)
    val prefs = Globals.prefs.get
    main.setChecked(YomiInfoUtils.showPoemText)
    furigana_size.setProgress(prefs.getInt("yomi_info_furigana_width",context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)))

    furigana_show.setChecked(prefs.getBoolean("yomi_info_furigana_show",false))
    author.setChecked(prefs.getBoolean("yomi_info_author",false))
    kami.setChecked(prefs.getBoolean("yomi_info_kami",true))
    simo.setChecked(prefs.getBoolean("yomi_info_simo",true))

    // switch visibility when spinner changed
    val layout = view.findViewById(R.id.yomi_info_conf_layout)
    val f = (flag:Boolean) => {
      layout.setVisibility(if(flag){
        View.VISIBLE
      }else{
        View.INVISIBLE
      })
    }
    f(main.isChecked)
    main.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
        override def onCheckedChanged(parent:CompoundButton,checked:Boolean){
          f(checked)
        }
    })

    val btn_detail = view.findViewById(R.id.yomi_info_conf_button_detail).asInstanceOf[Button]
    btn_detail.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          val dlg = new YomiInfoConfigDetailDialog(context)
          dlg.show
        }
    })
    val btn_font = view.findViewById(R.id.yomi_info_conf_button_font).asInstanceOf[Button]
    btn_font.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          new YomiInfoConfigFontDialog(context).show()
        }
    })


    builder.setView(view)

    super.onPrepareDialogBuilder(builder)
  }
}

class YomiInfoConfigDetailDialog(context:Context) extends AlertDialog(context) with YomiInfoPreferenceTrait with CustomAlertDialogTrait{
  def getWidgets(view:View) = {
    val show_kimari = view.findViewById(R.id.yomi_info_show_bar_kimari).asInstanceOf[CheckBox]
    val show_trans = view.findViewById(R.id.yomi_info_show_translate_button).asInstanceOf[CheckBox]
    val default_lang =  view.findViewById(R.id.yomi_info_default_language).asInstanceOf[Spinner]
    val torifuda_mode =  view.findViewById(R.id.yomi_info_torifuda_mode).asInstanceOf[Spinner]
    (show_kimari,show_trans,default_lang,torifuda_mode)
  }
  override def doWhenClose(view:View){
    val edit = Globals.prefs.get.edit
    val (show_kimari,show_trans,default_lang,torifuda_mode) = getWidgets(view)
    edit.putBoolean("yomi_info_show_bar_kimari",show_kimari.isChecked)
    edit.putBoolean("yomi_info_show_translate_button",show_trans.isChecked)
    edit.putString("yomi_info_default_lang",Utils.YomiInfoLang(default_lang.getSelectedItemPosition).toString)
    edit.putBoolean("yomi_info_torifuda_mode",torifuda_mode.getSelectedItemPosition == 1)
    edit.commit
    Globals.forceRestart = true
  }

  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf_detail, null)

    val (show_kimari,show_trans,default_lang,torifuda_mode) = getWidgets(view)
    val prefs = Globals.prefs.get

    show_kimari.setChecked(prefs.getBoolean("yomi_info_show_bar_kimari",true))
    show_trans.setChecked(prefs.getBoolean("yomi_info_show_translate_button",!Romanization.is_japanese(context)))

    val lang = Utils.YomiInfoLang.withName(prefs.getString("yomi_info_default_lang",Utils.YomiInfoLang.Japanese.toString))
    default_lang.setSelection(lang.id)

    torifuda_mode.setSelection(if(prefs.getBoolean("yomi_info_torifuda_mode",false)){1}else{0})
    setTitle(R.string.yomi_info_conf_detail_title)
    setViewAndButton(view)
    super.onCreate(state)
  }
}

class YomiInfoConfigFontDialog(context:Context) extends AlertDialog(context) with YomiInfoPreferenceTrait with CustomAlertDialogTrait{
  def getWidgets(view:View) = {
    val japanese_font = view.findViewById(R.id.yomi_info_japanese_font).asInstanceOf[Spinner]
    val furigana_font = view.findViewById(R.id.yomi_info_furigana_font).asInstanceOf[Spinner]
    val torifuda_font =  view.findViewById(R.id.yomi_info_torifuda_font).asInstanceOf[Spinner]
    val english_font =  view.findViewById(R.id.yomi_info_english_font).asInstanceOf[Spinner]
    (japanese_font,furigana_font,torifuda_font,english_font)
  }
  override def doWhenClose(view:View){
    val edit = Globals.prefs.get.edit
    val (japanese_font,furigana_font,torifuda_font,english_font) = getWidgets(view)
    val ar_en = context.getResources.getStringArray(R.array.yomi_info_english_fonts_values)
    val ar_ja = context.getResources.getStringArray(JP_FONT_VALUES_ID)
    YomiInfoUtils.setPoemTextFont(edit,ar_ja(japanese_font.getSelectedItemPosition))
    edit.putString("yomi_info_furigana_font",ar_ja(furigana_font.getSelectedItemPosition))
    edit.putString("yomi_info_torifuda_font",ar_ja(torifuda_font.getSelectedItemPosition))
    edit.putString("yomi_info_english_font",ar_en(english_font.getSelectedItemPosition))
    edit.commit
    Globals.forceRestart = true
  }

  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.yomi_info_conf_font, null)

    val (japanese_font,furigana_font,torifuda_font,english_font) = getWidgets(view)
    val prefs = Globals.prefs.get

    japanese_font.setSelection(getIndexFromValue(context,YomiInfoUtils.getPoemTextFont))
    furigana_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_furigana_font",YomiInfoUtils.DEFAULT_FONT)))
    torifuda_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_torifuda_font",YomiInfoUtils.DEFAULT_FONT)))
    english_font.setSelection(getIndexFromValue(context,prefs.getString("yomi_info_english_font","Serif"),R.array.yomi_info_english_fonts_values))

    setTitle(R.string.yomi_info_conf_font_title)
    setViewAndButton(view)
    super.onCreate(state)
  }
}

trait YomiInfoPreferenceTrait{
  val JP_FONT_VALUES_ID = R.array.conf_yomi_info_japanese_fonts_values
  def getIndexFromValue(context:Context,value:String,id:Int=JP_FONT_VALUES_ID) = {
    try{
      context.getResources.getStringArray(id).indexOf(value)
    }catch{
      case _:IndexOutOfBoundsException => 0
    }
  }
}

class QuickConfigDialog extends DialogFragment{
  override def onCreateDialog(saved:Bundle):Dialog = {
    val listener = new DialogInterface.OnClickListener{
      override def onClick(dialog:DialogInterface,which:Int){
        val act = getActivity.asInstanceOf[WasuramotiActivity]
        which match{
          case 5 =>
            // Change Intended Use
            dismiss
            act.changeIntendedUse(false)
            return
          case 4 =>
            // Translation
            val activity = getActivity // calling getActivity() inside switch_lang raises NullPointerException
            val switch_lang = (lang:Utils.YomiInfoLang.YomiInfoLang) => {
              () => {
                val edit = Globals.prefs.get.edit
                YomiInfoUtils.showPoemTextAndTitleBar(edit)
                edit.putString("yomi_info_default_lang",lang.toString)
                if(lang == Utils.YomiInfoLang.Japanese){
                  edit.putBoolean("yomi_info_show_translate_button",!Romanization.is_japanese(activity))
                }else{
                  edit.putBoolean("yomi_info_show_translate_button",true)
                  edit.putBoolean("yomi_info_author",false)
                }
                edit.commit
                act.reloadFragment
              }
            }
            dismiss
            Utils.listDialog(getActivity,
              R.string.quicklang_title,
              R.array.yomi_info_default_languages,
              Utils.YomiInfoLang.values.toArray.map(switch_lang(_)))
            return
          case _ =>
            None
        }
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
          case 3 =>
            // Only 1st Half
            YomiInfoUtils.showOnlyFirst(edit)
        }
        edit.commit
        dismiss
        act.reloadFragment
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
  val DEFAULT_FONT = "asset:tfont-kaisho.ttf"
  def showPoemText():Boolean = {
    Globals.prefs.exists(_.getBoolean("yomi_info_show_text",false))
  }
  def getPoemTextFont():String = {
    Globals.prefs.map{_.getString("yomi_info_japanese_font",DEFAULT_FONT)}.getOrElse(DEFAULT_FONT)
  }
  def setPoemTextFont(edit:SharedPreferences.Editor,str:String){
    edit.putString("yomi_info_japanese_font",str)
  }
  def setPoemTextVisibility(edit:SharedPreferences.Editor,show:Boolean){
    edit.putBoolean("yomi_info_show_text",show)
  }
  def showPoemTextAndTitleBar(edit:SharedPreferences.Editor){
    setPoemTextVisibility(edit,true)
    edit.putBoolean("yomi_info_torifuda_mode",false)
    edit.putBoolean("yomi_info_show_bar_kimari",true)
  }
  def hidePoemText(edit:SharedPreferences.Editor){
    setPoemTextVisibility(edit,false)
  }
  def showFull(edit:SharedPreferences.Editor){
    showPoemTextAndTitleBar(edit)
    edit.putBoolean("yomi_info_kami",true)
    edit.putBoolean("yomi_info_simo",true)
    edit.putBoolean("yomi_info_author",true)
    edit.putBoolean("yomi_info_furigana_show",true)
  }
  def showOnlyFirst(edit:SharedPreferences.Editor){
    showPoemTextAndTitleBar(edit)
    edit.putBoolean("yomi_info_kami",true)
    edit.putBoolean("yomi_info_simo",false)
    edit.putBoolean("yomi_info_author",false)
    edit.putBoolean("yomi_info_furigana_show",true)
  }
}
