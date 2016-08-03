package karuta.hpnpwd.wasuramoti
import android.support.v4.app.DialogFragment
import android.content.{Context,Intent}
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,LinearLayout,Button}
import android.os.Bundle
import android.net.Uri
import android.text.{Html,Spanned}
import android.app.{AlertDialog,SearchManager,Dialog}
import scala.collection.mutable

// The constructor of Fragment must be empty since when fragment is recreated,
// The empty constructor is called.
// Therefore we have to create instance through this function.
object YomiInfoSearchDialog{
  val PREFIX_MEMORIZE = "I.MEMORIZE"
  val PREFIX_REPLAY = "K.REPLAY"
  val PREFIX_DISPLAY = "L.DISPLAY"
  val PREFIX_SWITCH = "N.SWITCH"
  val PREFIX_KIMARIJI = "N.KIMARIJI"
  val PREFIX_SEARCH = "P.SEARCH"
  def newInstance(is_dialog:Boolean,fudanum:Option[Int]):YomiInfoSearchDialog = {
    val fragment = new YomiInfoSearchDialog
    val args = new Bundle
    args.putSerializable("fudanum",fudanum)
    args.putBoolean("is_dialog",is_dialog)
    fragment.setArguments(args)
    return fragment
  }
  def getFudaNumAndKimari(context:Context,fudanum:Option[Int]):(String,Spanned) ={
    fudanum.map{num =>
      if(num == 0){
        (context.getResources.getString(R.string.yomi_info_joka),Html.fromHtml("---"))
      }else{
        val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(num)
        val k_b = kimari_all.substring(kimari_cur.length,kimari_in_fudaset.length)
        val k_c = kimari_all.substring(kimari_in_fudaset.length)
        val html = s"""<font color="#90EE90">$kimari_cur</font><font color="#F0FFFF">$k_b</font><font color="#555555">$k_c</font>"""
        (num.toString,Html.fromHtml(html))
      }
    }.getOrElse{
      ("---", Html.fromHtml("---"))
    }
  }
}

trait GetFudanum {
  self:DialogFragment =>
  def getFudanum():Option[Int] = {
    Option(self.getArguments.getSerializable("fudanum").asInstanceOf[Option[Int]]).flatten
  }
}

class YomiInfoSearchDialog extends DialogFragment with GetFudanum{
  def enableDisplayButton(enabled:Boolean,force:Map[String,Boolean]=Map()){
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      for(t<-Array("AUTHOR","KAMI","SIMO","FURIGANA")){
        val tag = YomiInfoSearchDialog.PREFIX_DISPLAY + "_" + t
        val b = btnlist.findViewWithTag(tag).asInstanceOf[Button]
        if(b != null){
          b.setEnabled(force.getOrElse(t,enabled && haveToEnableButton(tag)))
          val img = getResources.getDrawable(Utils.getButtonDrawableId(getCurYomiInfoView,tag))
          b.setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
        }
      }
    }
  }
  def setMemorizedButton(){
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      val tag = YomiInfoSearchDialog.PREFIX_MEMORIZE + "_SWITCH"
      val b = btnlist.findViewWithTag(tag).asInstanceOf[Button]
      if(b != null){
        val img = getResources.getDrawable(Utils.getButtonDrawableId(getCurYomiInfoView,tag))
        b.setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
      }
    }
  }
  def getOrigText(tag:String):String ={
    val items = getActivity.getResources.getStringArray(R.array.yomi_info_search_array).toArray
    items.find{_.startsWith(tag+"|")}.get.split("\\|")(1)
  }
  def setFudanum(fudanum:Option[Int]){
    getArguments.putSerializable("fudanum",fudanum)
    val torifuda_mode = Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false)
    val info_lang = Utils.YomiInfoLang.withName(Globals.prefs.get.getString("yomi_info_default_lang",Utils.YomiInfoLang.Japanese.toString))
    if(info_lang != Utils.YomiInfoLang.Japanese){
      enableDisplayButton(true,Map("FURIGANA"->false))
    }else if(torifuda_mode){
      enableDisplayButton(false)
    }else{
      enableDisplayButton(true)
    }

    setMemorizedButton()

    val tag = YomiInfoSearchDialog.PREFIX_SWITCH+"_MODE"
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      val btn = btnlist.findViewWithTag(tag).asInstanceOf[Button]
      if(btn != null){
        btn.setText(getSwitchModeButtonText(tag,torifuda_mode))
      }
    }
  }
  def doWebSearch(fudanum:Option[Int],mode:String){
    val query = if(mode == "TEXT"){
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.list_full)(num))
      }.getOrElse{
        getActivity.getString(R.string.search_text_default)
      }
    }else{
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.author)(num)).replace(" ","")
      }.getOrElse{
        getActivity.getString(R.string.search_text_default)
      } + " " + getActivity.getString(R.string.search_text_author)
    }
    val f1 = {() =>
      val intent = new Intent(Intent.ACTION_WEB_SEARCH)
      intent.putExtra(SearchManager.QUERY,query)
      Left(intent)
    }
    val f2 = {() =>
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.setData(Uri.parse("http://www.google.com/search?q="+Uri.encode(query)))
      Left(intent)
    }
    val f3 = {() =>
      Right({() =>
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
    val p = Globals.prefs.get
    tag.split("_")(1) match{
      case s @ ("AUTHOR"|"KAMI"|"SIMO") => ! p.getBoolean("yomi_info_"+s.toLowerCase,false)
      case "FURIGANA" => ! p.getBoolean("yomi_info_furigana_show",false)
      case _ => true
    }
  }

  def genContentView():View = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_dialog,null)
    val btnlist = view.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    var items = getActivity.getResources.getStringArray(R.array.yomi_info_search_array).toArray.filter{ x=>
      val tag = x.split("\\|")(0)
      if(tag.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")){
        haveToEnableButton(tag)
      }else if(tag.startsWith(YomiInfoSearchDialog.PREFIX_REPLAY+"_")){
        Globals.prefs.get.getBoolean("show_replay_last_button",false)
      }else if(tag.startsWith(YomiInfoSearchDialog.PREFIX_MEMORIZE+"_")){
        Globals.prefs.get.getBoolean("memorization_mode",false)
      }else if(List("LANG","ROMAJI").map{ YomiInfoSearchDialog.PREFIX_SWITCH + "_" + _ }.contains(tag) ){
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
              vw.torifuda_mode ^= true
              vw.initDrawing
              vw.invalidate
              btn.asInstanceOf[Button].setText(getSwitchModeButtonText(tag,vw.torifuda_mode))
              enableDisplayButton(!vw.torifuda_mode)
            }
          }else if(List("LANG","ROMAJI").map{ YomiInfoSearchDialog.PREFIX_SWITCH + "_" + _ }.contains(tag)){
            getCurYomiInfoView.foreach{vw =>
              vw.info_lang = if(tag.endsWith("ROMAJI")){
                if(vw.torifuda_mode || vw.info_lang != Utils.YomiInfoLang.Romaji){
                  Utils.YomiInfoLang.Romaji
                }else{
                  Utils.YomiInfoLang.Japanese
                }
              }else{
                if(vw.torifuda_mode || vw.info_lang != Utils.YomiInfoLang.English){
                  Utils.YomiInfoLang.English
                }else{
                  Utils.YomiInfoLang.Japanese
                }
              }
              vw.torifuda_mode = false

              vw.initDrawing
              vw.invalidate
              enableDisplayButton(true)
            }
          }else if(tag.startsWith(YomiInfoSearchDialog.PREFIX_SEARCH+"_")){
            doWebSearch(getFudanum,tag.split("_")(1))
          }else if(tag == YomiInfoSearchDialog.PREFIX_REPLAY+"_LAST"){
            KarutaPlayUtils.startReplay(getActivity.asInstanceOf[WasuramotiActivity])
          }else{
            val Array(prefix,postfix) = tag.split("_")
            getCurYomiInfoView.foreach{vw =>
              if(prefix == YomiInfoSearchDialog.PREFIX_MEMORIZE){
                vw.switchMemorized
                Utils.setButtonTextByState(getActivity.getApplicationContext, invalidateQueryCacheExceptKarafuda = true)
              }else{
                postfix match{
                  case "AUTHOR" => vw.show_author ^= true
                  case "KAMI" => vw.show_kami ^= true
                  case "SIMO" => vw.show_simo ^= true
                  case "FURIGANA" => vw.show_furigana ^= true
                }
              }
              vw.invalidate
              val img = getResources.getDrawable(Utils.getButtonDrawableId(getCurYomiInfoView,tag))
              btn.asInstanceOf[Button].setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
            }
          }
          if(getArguments.getBoolean("is_dialog")){
            dismiss()
          }
        }
      })
    val (init_torifuda_mode,init_info_lang) =
      getCurYomiInfoView.map{x=>
        (x.torifuda_mode,x.info_lang)
      }.getOrElse((
        Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false),
        Utils.YomiInfoLang.withName(Globals.prefs.get.getString("yomi_info_default_lang",Utils.YomiInfoLang.Japanese.toString))
      ))
    val text_convert= {(s:String,label:String) =>
      if(label == YomiInfoSearchDialog.PREFIX_SWITCH+"_MODE"){
        getSwitchModeButtonText(label,init_torifuda_mode)
      }else if(label == YomiInfoSearchDialog.PREFIX_REPLAY+"_LAST"){
        Utils.replayButtonText(getResources)
      }else{
        s
      }
    }
    if(init_torifuda_mode && getArguments.getBoolean("is_dialog")){
      items = items.filterNot{_.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")}
    }
    // TODO: merge this function to enableDisplayButton() since it is duplicate code
    val have_to_enable = {(label:String) =>
      val r = if(init_info_lang != Utils.YomiInfoLang.Japanese){
        label != YomiInfoSearchDialog.PREFIX_DISPLAY+"_FURIGANA"
      }else{
        !init_torifuda_mode || !label.startsWith(YomiInfoSearchDialog.PREFIX_DISPLAY+"_")
      }
      r
    }

    btnlist.addButtons(getActivity, getCurYomiInfoView, items
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
    val builder = new AlertDialog.Builder(getActivity)
    val (fudanum_s,kimari) = YomiInfoSearchDialog.getFudaNumAndKimari(getActivity,getFudanum)
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


class YomiInfoDetailDialog extends DialogFragment with GetFudanum{
  def addRow(table:LinearLayout,text:String){
    val item = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_detail_row,null)
    val v_text = item.findViewById(R.id.kimariji_changelog_row).asInstanceOf[TextView]
    v_text.setText(Html.fromHtml(text))
    table.addView(item)
  }
  def addKimarijiChangelog(table:LinearLayout,fudanum:Option[Int]){
    fudanum.foreach{num =>
      if(num == 0){
        addRow(table,getActivity.getString(R.string.kimariji_changelog_joka))
        return
      }
      val msg_cur = if(!Utils.disableKimarijiLog){
        (if(Romanization.is_japanese(getActivity)){""}else{" "}) +
        getActivity.getString(R.string.kimariji_changelog_current)
      }else{
        ""
      }
      val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(num)
      addRow(table,getActivity.getString(R.string.kimariji_changelog_init,kimari_all)+
        (if(kimari_all == kimari_cur){msg_cur}else{""})
      )
      if(kimari_all != kimari_in_fudaset){
        addRow(table,getActivity.getString(R.string.kimariji_changelog_fudaset,kimari_in_fudaset)+
        (if(kimari_in_fudaset == kimari_cur){msg_cur}else{""})
        )
      }else if(FudaListHelper.isBoundedByFudaset &&
        FudaListHelper.getOrQueryNumbersOfKarafuda() == 0
      ){
        addRow(table,getActivity.getString(R.string.kimariji_changelog_sameas))
      }
      if(kimari_in_fudaset != kimari_cur){
        val alread_read = FudaListHelper.getAlreadyReadFromKimariji(num,kimari_cur)
        var kima_prev = kimari_in_fudaset
        for(ar<-alread_read.inits.toArray.reverse if ar.nonEmpty ){
          val (_,read_order) = ar.last
          val kima = FudaListHelper.getKimarijiAtIndex(num,Some(read_order))
          if(kima_prev != kima){
            val fuda_buf = ar.map{_._1}.filter{_.startsWith(kima)}
            addRow(table,getActivity.getString(R.string.kimariji_changelog_changed,fuda_buf.mkString(", "),kima)+
              (if(kima == kimari_cur){msg_cur}else{""})
            )
            kima_prev = kima
          }
        }
      }else if(Utils.disableKimarijiLog){
        addRow(table,getActivity.getString(R.string.kimariji_changelog_disabled))
      }
    }
  }
  override def onCreateDialog(saved:Bundle):Dialog = {
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_detail,null)
    val table = view.findViewById(R.id.kimariji_changelog).asInstanceOf[LinearLayout]
    addKimarijiChangelog(table,getFudanum)
    builder
    .setTitle(getActivity.getString(R.string.yomi_info_search_detail_title))
    .setView(view)
    .setPositiveButton(android.R.string.ok,null)
    .create
  }
}
