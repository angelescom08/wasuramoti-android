package karuta.hpnpwd.wasuramoti
import _root_.android.support.v4.app.DialogFragment
import _root_.android.content.{Context,DialogInterface,Intent}
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
      ((if(Romanization.is_japanese(context)){"序歌"}else{"Joka"}),Html.fromHtml("---"))
    }else{
      val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(context,fudanum)
      val k_b = kimari_all.substring(kimari_cur.length,kimari_in_fudaset.length)
      val k_c = kimari_all.substring(kimari_in_fudaset.length)
      val html = s"""<font color="#DDA0DD">$kimari_cur</font><font color="#FFFFFF">$k_b</font><font color="#999999">$k_c</font>"""
      (fudanum.toString,Html.fromHtml(html))
    }
  }
}

class YomiInfoSearchDialog extends DialogFragment{
  def setFudanum(fudanum:Int){
    getArguments.putInt("fudanum",fudanum)
    val btnlist = getActivity.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    if(btnlist != null){
      for(t<-Array("AUTHOR","KAMI","SIMO","FURIGANA")){
        val b = btnlist.findViewWithTag("A.DISPLAY_" + t).asInstanceOf[Button]
        if(b != null){
          b.setEnabled(true)
        }
      }
    }

  }
  def doWebSearch(fudanum:Int,mode:String){
    val query = if(mode == "TEXT"){
      AllFuda.removeInsideParens(AllFuda.list_full(fudanum))
    }else{
      AllFuda.removeInsideParens(AllFuda.author(fudanum)).replace(" ","") + " 歌人"
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
        Utils.messageDialog(getActivity,Left("Application for Web search not found on this device."))
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
  def genContentView():View = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.yomi_info_search_dialog,null) 
    val btnlist = view.findViewById(R.id.yomi_info_button_list).asInstanceOf[YomiInfoButtonList]
    val items = getActivity.getResources.getStringArray(R.array.yomi_info_search_array).toArray.filter{ x=>
      val tag = x.split("\\|")(0)
      if(tag.startsWith("A.DISPLAY_")){
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
            case "FURIGANA" => p.getString("yomi_info_furigana","None") == "None"
            case _ => true
          }
        }
      }else if(tag=="C.KIMARIJI_LOG"){
        ! getArguments.getBoolean("is_dialog")
      }else{
        true
      }
    }
    btnlist.setOnClickListener(new YomiInfoButtonList.OnClickListener(){
        override def onClick(btn:Button,tag:String){
          if(tag == "C.KIMARIJI_LOG"){
            showYomiInfoDetailDialog()
          }else if(tag.startsWith("B.SEARCH_")){
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
            }
          }
          if(getArguments.getBoolean("is_dialog")){
            dismiss()
          }else if(tag.startsWith("A.DISPLAY_")){
            btn.setEnabled(false)
          }
        }
      })

    btnlist.addButtons(getActivity,items
      .groupBy(_.split("\\|").head.split("_").head).values.toArray.sortBy{_.head}
      .map{v=>if(v.length%2==0){v}else{v++Array("|")}}.flatten.toArray
      .map{_.split("\\|") match {
        case Array(l,t) => (t,l)
        case _ => ("","")
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
    .setNeutralButton(R.string.yomi_info_search_detail_title,new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          showYomiInfoDetailDialog()
       }
      })
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
// このファイルはutf-8で日本語を含んでいます
