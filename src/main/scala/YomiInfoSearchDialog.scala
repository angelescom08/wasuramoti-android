package karuta.hpnpwd.wasuramoti
import _root_.android.support.v4.app.DialogFragment
import _root_.android.content.{Context,Intent}
import _root_.android.view.{View,LayoutInflater,ViewGroup}
import _root_.android.widget.{TextView,LinearLayout,Button}
import _root_.android.os.Bundle
import _root_.android.net.Uri
import _root_.android.text.{Html,Spanned}
import _root_.android.app.{AlertDialog,SearchManager,Dialog}
import scala.collection.mutable

// The constructor of Fragment must be empty since when fragment is recreated,
// The empty constructor is called.
// Therefore we have to create instance through this function.
object YomiInfoSearchDialog{
  val PREFIX_DISPLAY = "A.DISPLAY"
  val PREFIX_KIMARIJI = "B.KIMARIJI"
  val PREFIX_SWITCH = "B.SWITCH"
  val PREFIX_SEARCH = "C.SEARCH"
  def newInstance(is_dialog:Boolean,fudanum:Int):YomiInfoSearchDialog = {
    val fragment = new YomiInfoSearchDialog()
    val args = new Bundle()
    args.putInt("fudanum",fudanum)
    args.putBoolean("is_dialog",is_dialog)
    fragment.setArguments(args)
    return fragment
  }
  def getFudaNumAndKimari(context:Context,fudanum:Int):(String,Spanned) ={
    if(fudanum == 0){
      (context.getResources.getString(R.string.yomi_info_joka),Html.fromHtml("---"))
    }else{
      val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(context,fudanum)
      val k_b = kimari_all.substring(kimari_cur.length,kimari_in_fudaset.length)
      val k_c = kimari_all.substring(kimari_in_fudaset.length)
      val html = s"""<font color="#90EE90">$kimari_cur</font><font color="#FFFFFF">$k_b</font><font color="#999999">$k_c</font>"""
      (fudanum.toString,Html.fromHtml(html))
    }
  }
}

class YomiInfoSearchDialog extends DialogFragment{
  def enableDisplayButton(enabled:Boolean,force:Map[String,Boolean]=Map()){
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      for(t<-Array("AUTHOR","KAMI","SIMO","FURIGANA")){
        val tag = YomiInfoSearchDialog.PREFIX_DISPLAY + "_" + t
        val b = btnlist.findViewWithTag(tag)
        if(b != null){
          b.setEnabled(force.getOrElse(t,enabled && haveToEnableButton(tag)))
        }
      }
    }
  }
  def getOrigText(tag:String):String ={
    var items = getActivity.getResources.getStringArray(R.array.yomi_info_search_array).toArray
    items.find{_.startsWith(tag+"|")}.get.split("\\|")(1)
  }
  def setFudanum(fudanum:Int){
    getArguments.putInt("fudanum",fudanum)
    val torifuda_mode = Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false)
    val english_mode = !Globals.prefs.get.getBoolean("yomi_info_default_lang_is_jpn",true)
    if(english_mode){
      enableDisplayButton(true,Map("FURIGANA"->false))
    }else if(torifuda_mode){
      enableDisplayButton(false)
    }else{
      enableDisplayButton(true)
    }
    val tag = YomiInfoSearchDialog.PREFIX_SWITCH+"_MODE"
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      val btn = btnlist.findViewWithTag(tag).asInstanceOf[Button]
      if(btn != null){
        btn.setText(getSwitchModeButtonText(tag,torifuda_mode))
      }
    }
  }
  def doWebSearch(fudanum:Int,mode:String){
    val query = if(mode == "TEXT"){
      AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.list_full)(fudanum))
    }else{
      AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.author)(fudanum)).replace(" ","") +
        " " + getActivity.getString(R.string.search_text_author)
    }
    val f1 = {_:Unit =>
      val intent = new Intent(Intent.ACTION_WEB_SEARCH)
      intent.putExtra(SearchManager.QUERY,query)
      Left(intent)
    }
    val f2 = {_:Unit => 
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.setData(Uri.parse("http://www.google.com/search?q="+Uri.encode(query)))
      Left(intent)
    }
    val f3 = {_:Unit => 
      Right({ _:Unit =>
        Utils.messageDialog(getActivity,Right(R.string.browser_not_found))
      })
    }
    // scala.util.control.Breaks.break does not work (why?)
    // Therefore we use `exists` in Traversable trait instead
    Seq(f1,f2,f3) exists {f=>
        f() match {
          case Left(intent) =>
            try{
              startActivity(intent)
              true
            }catch{
              case _:android.content.ActivityNotFoundException => false
            }
          case Right(g) => {g();true}
        }
      }
  }
  def getCurYomiInfoView():Option[YomiInfoView] = {
    val yi = getActivity.findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yi == null){ return None }
    yi.cur_view.flatMap{x:Int =>
      Option(getActivity.findViewById(x).asInstanceOf[YomiInfoView])
    }
  }
  override def onCreateView(inflater:LayoutInflater,container:ViewGroup,saved:Bundle):View = {
    if(!getArguments.getBoolean("is_dialog")){
      genContentView()
    }else{
      super.onCreateView(inflater,container,saved)
    }
  }

  def getSwitchModeButtonText(tag:String,torifuda_mode:Boolean):String ={
    val orig = getOrigText(tag)
    val s = if(torifuda_mode){
      1
    }else{
      0
    }
    orig.split(";")(s)
  }

  def haveToEnableButton(tag:String):Boolean = {
    getCurYomiInfoView.map{vw =>
      tag.split("_")(1) match{
        case "AUTHOR" => ! vw.show_author
        case "KAMI" => ! vw.show_kami
        case "SIMO" => ! vw.show_simo
        case "FURIGANA" => ! vw.show_furigana
        case _ => true
      }
    }.getOrElse{
      val p = Globals.prefs.get
      tag.split("_")(1) match{
        case s @ ("AUTHOR"|"KAMI"|"SIMO") => ! p.getBoolean("yomi_info_"+s.toLowerCase,false)
        case "FURIGANA" => ! p.getBoolean("yomi_info_furigana_show",false)
        case _ => true
      }
    }
  }

  def genContentView():View = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_dialog,null) 
    val btnlist = view.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    var items = getActivity.getResources.getStringArray(R.array.yomi_info_search_array).toArray.filter{ x=>
      val tag = x.split("\\|")(0)
      if(tag.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")){
        haveToEnableButton(tag)
      }else if(tag == YomiInfoSearchDialog.PREFIX_SWITCH + "_LANG"){
        Globals.prefs.get.getBoolean("yomi_info_show_translate_button",!Romanization.is_japanese(getActivity))
      }else{
        true
      }
    }
    btnlist.setOnClickListener(new YomiInfoButtonList.OnClickListener(){
        override def onClick(btn:View,tag:String){
          if(tag == YomiInfoSearchDialog.PREFIX_KIMARIJI + "_LOG"){
            showYomiInfoDetailDialog()
          }else if(tag == YomiInfoSearchDialog.PREFIX_SWITCH + "_MODE"){
            getCurYomiInfoView.foreach{vw =>
              vw.english_mode = false
              vw.torifuda_mode ^= true
              vw.initDrawing
              vw.invalidate
              btn.asInstanceOf[Button].setText(getSwitchModeButtonText(tag,vw.torifuda_mode))
              enableDisplayButton(!vw.torifuda_mode)
            }
          }else if(tag == YomiInfoSearchDialog.PREFIX_SWITCH + "_LANG"){
            getCurYomiInfoView.foreach{vw =>
              vw.english_mode ^= true
              vw.initDrawing
              vw.invalidate
              enableDisplayButton(vw.english_mode || !vw.torifuda_mode)
            }
          }else if(tag.startsWith(YomiInfoSearchDialog.PREFIX_SEARCH+"_")){
            val fudanum = getArguments.getInt("fudanum",0)
            doWebSearch(fudanum,tag.split("_")(1))
          }else{
            getCurYomiInfoView.foreach{vw =>
              tag.split("_")(1) match{
                case "AUTHOR" => vw.show_author = true
                case "KAMI" => vw.show_kami = true
                case "SIMO" => vw.show_simo = true
                case "FURIGANA" => vw.show_furigana = true
              }
              vw.invalidate
              btn.setEnabled(false)
            }
          }
          if(getArguments.getBoolean("is_dialog")){
            dismiss()
          }
        }
      })
    val (init_torifuda_mode,init_english_mode) =
      getCurYomiInfoView.map{x=>
        (x.torifuda_mode,x.english_mode)
      }.getOrElse((
        Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false),
        !Globals.prefs.get.getBoolean("yomi_info_default_lang_is_jpn",true)
      ))
    val text_convert= {(s:String,label:String) =>
      if(label == YomiInfoSearchDialog.PREFIX_SWITCH+"_MODE"){
        getSwitchModeButtonText(label,init_torifuda_mode)
      }else{
        s
      }
    }
    if(init_torifuda_mode && getArguments.getBoolean("is_dialog")){
      items = items.filterNot{_.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")}
    }
    // TODO: merge this function to enableDisplayButton() since it is duplicate code
    val have_to_enable = {(label:String) =>
      val r = if(init_english_mode){
        label != YomiInfoSearchDialog.PREFIX_DISPLAY+"_FURIGANA"
      }else{
        !init_torifuda_mode || !label.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")
      }
      r
    }

    btnlist.addButtons(getActivity,items
      .groupBy(_.split("\\|").head.split("\\.").head).values.toArray.sortBy{_.head}
      .map{v=>if(v.length%2==0){v}else{v++Array("|")}}.flatten.toArray
      .map{_.split("\\|") match {
        case Array(l,t) => (text_convert(t,l),l,have_to_enable(l))
        case _ => ("","",false)
      }})
    view
  }
  def showYomiInfoDetailDialog(){
    val dlg = new YomiInfoDetailDialog()
    dlg.setArguments(getArguments)
    dlg.show(getActivity.getSupportFragmentManager,"yomi_info_detail")
  }
  override def onCreateDialog(saved:Bundle):Dialog = {
    if(!getArguments.getBoolean("is_dialog")){
      return super.onCreateDialog(saved)
    }
    val fudanum = getArguments.getInt("fudanum",0)
    val builder = new AlertDialog.Builder(getActivity)
    val (fudanum_s,kimari) = YomiInfoSearchDialog.getFudaNumAndKimari(getActivity,fudanum)
    val title_view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_title,null) 
    title_view.findViewById(R.id.yomi_info_search_poem_num).asInstanceOf[TextView].setText(fudanum_s)
    title_view.findViewById(R.id.yomi_info_search_kimariji).asInstanceOf[TextView].setText(kimari)
    val body_view = genContentView()
    builder
    .setView(body_view)
    .setCustomTitle(title_view)
    .setNegativeButton(android.R.string.cancel,null)
    .create()
  }
}


class YomiInfoDetailDialog extends DialogFragment{
  def addRow(table:LinearLayout,text:String){
    val item = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_detail_row,null) 
    val v_text = item.findViewById(R.id.kimariji_changelog_row).asInstanceOf[TextView]
    v_text.setText(Html.fromHtml(text))
    table.addView(item)
  }
  def addKimarijiChangelog(table:LinearLayout,fudanum:Int){
    if(fudanum == 0){
      addRow(table,getActivity.getString(R.string.kimariji_changelog_joka))
      return
    }
    val msg_cur = (if(Romanization.is_japanese(getActivity)){""}else{" "}) + 
    getActivity.getString(R.string.kimariji_changelog_current)
    val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(getActivity,fudanum)
    addRow(table,getActivity.getString(R.string.kimariji_changelog_init,kimari_all)+
      (if(kimari_all == kimari_cur){msg_cur}else{""})
    )
    if(kimari_all != kimari_in_fudaset){
      addRow(table,getActivity.getString(R.string.kimariji_changelog_fudaset,kimari_in_fudaset)+
      (if(kimari_in_fudaset == kimari_cur){msg_cur}else{""})
      )
    }else if(FudaListHelper.getOrQueryNumbersToRead(getActivity) < 100 &&
      FudaListHelper.getOrQueryNumbersOfKarafuda(getActivity) == 0
    ){
      addRow(table,getActivity.getString(R.string.kimariji_changelog_sameas))
    }
    if(kimari_in_fudaset != kimari_cur){
      val alread_read = FudaListHelper.getAlreadyReadFromKimariji(getActivity,fudanum,kimari_cur)
      var kima_prev = kimari_in_fudaset
      for(ar<-alread_read.inits.toArray.reverse if ! ar.isEmpty ){
        val (_,read_order) = ar.last
        val kima = FudaListHelper.getKimarijiAtIndex(getActivity,fudanum,Some(read_order))
        if(kima_prev != kima){
          val fuda_buf = ar.map{_._1}.filter{_.startsWith(kima)}
          addRow(table,getActivity.getString(R.string.kimariji_changelog_changed,fuda_buf.mkString(", "),kima)+
            (if(kima == kimari_cur){msg_cur}else{""})
          )
          kima_prev = kima
        }
      }
    }
  }
  override def onCreateDialog(saved:Bundle):Dialog = {
    val fudanum = getArguments.getInt("fudanum",0)
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_detail,null) 
    val table = view.findViewById(R.id.kimariji_changelog).asInstanceOf[LinearLayout]
    addKimarijiChangelog(table,fudanum)
    builder
    .setTitle(getActivity.getString(R.string.yomi_info_search_detail_title))
    .setView(view)
    .setPositiveButton(android.R.string.ok,null)
    .create
  }
}
