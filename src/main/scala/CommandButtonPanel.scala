package karuta.hpnpwd.wasuramoti
import android.content.Context
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,LinearLayout,Button}
import android.os.Bundle
import android.text.{Html,Spanned}
import android.app.Dialog

import android.support.v4.app.{DialogFragment,Fragment}
import android.support.v7.app.AlertDialog

// The constructor of Fragment must be empty since when fragment is recreated,
// The empty constructor is called.
// Therefore we have to create instance through this function.
object CommandButtonPanel{
  val PREFIX_MEMORIZE = "I.MEMORIZE"
  val PREFIX_REWIND = "K.REWIND"
  val PREFIX_REPLAY = "K.REPLAY"
  val PREFIX_NEXT = "K.NEXT"
  val PREFIX_DISPLAY = "L.DISPLAY"
  val PREFIX_SWITCH = "N.SWITCH"
  val PREFIX_KIMARIJI = "P.KIMARIJI"
  val PREFIX_POEM = "P.POEM"
  def newInstance(fudanum:Option[Int]):CommandButtonPanel = {
    val fragment = new CommandButtonPanel
    val args = new Bundle
    args.putSerializable("fudanum",fudanum)
    fragment.setArguments(args)
    return fragment
  }
  def getFudaNumAndKimari(context:Context,fudanum:Option[Int]):(String,Spanned) ={
    fudanum.map{num =>
      if(num == 0){
        (context.getResources.getString(R.string.yomi_info_joka),Html.fromHtml("---"))
      }else{
        (num.toString,Html.fromHtml(Utils.kimarijiToHtml(context,num,true)))
      }
    }.getOrElse{
      ("---", Html.fromHtml("---"))
    }
  }
}


class CommandButtonPanel extends Fragment with GetFudanum{
  def enableDisplayButton(enabled:Boolean,force:Map[String,Boolean]=Map()){
    val btnlist = getActivity.findViewById[CommandButtonList](R.id.yomi_info_button_list)
    if(btnlist != null){
      for(t<-Array("AUTHOR","KAMI","SIMO","FURIGANA")){
        val tag = CommandButtonPanel.PREFIX_DISPLAY + "_" + t
        val b = btnlist.findViewWithTag[Button](tag)
        if(b != null){
          b.setEnabled(force.getOrElse(t,enabled && haveToEnableDisplayButton(tag)))
          val img = getResources.getDrawable(Utils.getButtonDrawableId(getCurYomiInfoView,tag))
          b.setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
        }
      }
    }
  }
  def setMemorizedButton(){
    val btnlist = getActivity.findViewById[CommandButtonList](R.id.yomi_info_button_list)
    if(btnlist != null){
      val tag = CommandButtonPanel.PREFIX_MEMORIZE + "_SWITCH"
      val b = btnlist.findViewWithTag[Button](tag)
      if(b != null){
        val img = getResources.getDrawable(Utils.getButtonDrawableId(getCurYomiInfoView,tag))
        b.setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
      }
    }
  }
  def getOrigText(tag:String):String ={
    val items = getActivity.getResources.getStringArray(R.array.command_button_array).toArray
    items.find{_.startsWith(tag+"|")}.get.split("\\|")(1)
  }
  def setFudanum(fudanum:Option[Int]){
    getArguments.putSerializable("fudanum",fudanum)
    val torifuda_mode = Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false)
    val info_lang = Utils.YomiInfoLang.getDefaultLangFromPref(Globals.prefs.get)
    if(info_lang != Utils.YomiInfoLang.Japanese){
      enableDisplayButton(true,Map("FURIGANA"->false))
    }else if(torifuda_mode){
      enableDisplayButton(false)
    }else{
      enableDisplayButton(true)
    }

    setMemorizedButton()

    val tag = CommandButtonPanel.PREFIX_SWITCH+"_MODE"
    val btnlist = getActivity.findViewById[CommandButtonList](R.id.yomi_info_button_list)
    if(btnlist != null){
      val btn = btnlist.findViewWithTag[Button](tag)
      if(btn != null){
        btn.setText(getSwitchModeButtonText(tag,torifuda_mode))
      }
    }
  }
  def getCurYomiInfoView():Option[YomiInfoView] = {
    val yi = getActivity.findViewById[YomiInfoLayout](R.id.yomi_info)
    if(yi == null){ return None }
    yi.cur_view.flatMap{x:Int =>
      Option(getActivity.findViewById[YomiInfoView](x))
    }
  }
  override def onCreateView(inflater:LayoutInflater,container:ViewGroup,saved:Bundle):View = {
    val view = inflater.inflate(R.layout.command_button_panel,null)
    val btnlist = view.findViewById[CommandButtonList](R.id.yomi_info_button_list)
    var items = getActivity.getResources.getStringArray(R.array.command_button_array).toArray.filter{ x=>
      val tag = x.split("\\|")(0)
      if(tag.startsWith(CommandButtonPanel.PREFIX_DISPLAY+"_")){
        haveToEnableDisplayButton(tag)
      }else if(tag == CommandButtonPanel.PREFIX_REPLAY+"_LAST"){
        Globals.prefs.get.getBoolean("show_replay_last_button",false)
      }else if(tag == CommandButtonPanel.PREFIX_REWIND+"_PREV"){
        Globals.prefs.get.getBoolean("show_rewind_button",false)
      }else if(tag == CommandButtonPanel.PREFIX_NEXT+"_SKIP"){
        Globals.prefs.get.getBoolean("show_skip_button",false)
      }else if(tag == CommandButtonPanel.PREFIX_MEMORIZE+"_SWITCH"){
        Globals.prefs.get.getBoolean("memorization_mode",false)
      }else if(List("LANG","ROMAJI").map{ CommandButtonPanel.PREFIX_SWITCH + "_" + _ }.contains(tag) ){
        Globals.prefs.get.getBoolean("yomi_info_show_translate_button",!Romanization.isJapanese(getActivity))
      }else{
        true
      }
    }
    btnlist.setOnClickListener(new CommandButtonList.OnClickListener(){
        override def onClick(btn:View,tag:String){
          val was = getActivity.asInstanceOf[WasuramotiActivity]
          if(tag == CommandButtonPanel.PREFIX_KIMARIJI + "_LOG"){
            showKimarijiChangelogDialog()
          }else if(tag == CommandButtonPanel.PREFIX_POEM + "_DESC"){
            val dlg = PoemDescriptionDialog.newInstance(getFudanum)
            Utils.showDialogOrFallbackToStateless(getActivity.getSupportFragmentManager,dlg,"poem_description_dialog")
          }else if(tag == CommandButtonPanel.PREFIX_SWITCH + "_MODE"){
            getCurYomiInfoView.foreach{vw =>
              vw.torifuda_mode ^= true
              vw.initDrawing
              vw.invalidate
              btn.asInstanceOf[Button].setText(getSwitchModeButtonText(tag,vw.torifuda_mode))
              enableDisplayButton(!vw.torifuda_mode)
            }
          }else if(List("LANG","ROMAJI").map{ CommandButtonPanel.PREFIX_SWITCH + "_" + _ }.contains(tag)){
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
          }else if(tag == CommandButtonPanel.PREFIX_REWIND+"_PREV"){
            KarutaPlayUtils.rewind(was)
          }else if(tag == CommandButtonPanel.PREFIX_REPLAY+"_LAST"){
            KarutaPlayUtils.startReplay(was)
          }else if(tag == CommandButtonPanel.PREFIX_NEXT+"_SKIP"){
            KarutaPlayUtils.skipToNext(was)
          }else{
            val Array(prefix,postfix) = tag.split("_")
            getCurYomiInfoView.foreach{vw =>
              if(prefix == CommandButtonPanel.PREFIX_MEMORIZE){
                vw.switchMemorized
                was.setButtonTextByState(invalidateQueryCacheExceptKarafuda = true)
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
        }
      })
    val (init_torifuda_mode,init_info_lang) =
      getCurYomiInfoView.map{x=>
        (x.torifuda_mode,x.info_lang)
      }.getOrElse((
        Globals.prefs.get.getBoolean("yomi_info_torifuda_mode",false),
        Utils.YomiInfoLang.getDefaultLangFromPref(Globals.prefs.get)
      ))
    val text_convert= {(s:String,label:String) =>
      if(label == CommandButtonPanel.PREFIX_SWITCH+"_MODE"){
        getSwitchModeButtonText(label,init_torifuda_mode)
      }else if(label == CommandButtonPanel.PREFIX_REPLAY+"_LAST"){
        Utils.replayButtonText(getResources)
      }else{
        s
      }
    }
    // TODO: merge this function to enableDisplayButton() since it is duplicate code
    val have_to_enable = {(label:String) =>
      if(label == CommandButtonPanel.PREFIX_REWIND+"_PREV"){
        KarutaPlayUtils.haveToEnableRewindButton
      }else if(label == CommandButtonPanel.PREFIX_REPLAY+"_LAST"){
        KarutaPlayUtils.haveToEnableReplayButton
      }else if(label == CommandButtonPanel.PREFIX_NEXT+"_SKIP"){
        KarutaPlayUtils.haveToEnableSkipButton
      }else{
        if(init_info_lang != Utils.YomiInfoLang.Japanese){
          label != CommandButtonPanel.PREFIX_DISPLAY+"_FURIGANA"
        }else{
          !init_torifuda_mode || !label.startsWith(CommandButtonPanel.PREFIX_DISPLAY+"_")
        }
      }
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

  def getSwitchModeButtonText(tag:String,torifuda_mode:Boolean):String ={
    val orig = getOrigText(tag)
    val s = if(torifuda_mode){
      1
    }else{
      0
    }
    orig.split(";")(s)
  }

  def haveToEnableDisplayButton(tag:String):Boolean = {
    val p = Globals.prefs.get
    tag.split("_")(1) match{
      case s @ ("AUTHOR"|"KAMI"|"SIMO") => ! p.getBoolean("yomi_info_"+s.toLowerCase,false)
      case "FURIGANA" => ! p.getBoolean("yomi_info_furigana_show",false)
      case _ => true
    }
  }

  def showKimarijiChangelogDialog(){
    val dlg = new KimarijiChangelogDialog
    dlg.setArguments(getArguments)
    Utils.showDialogOrFallbackToStateless(getActivity.getSupportFragmentManager,dlg,"kimariji_log")
  }
}


class KimarijiChangelogDialog extends DialogFragment with GetFudanum{
  def addRow(table:LinearLayout,text:String){
    val context = getActivity
    val item = LayoutInflater.from(context).inflate(R.layout.kimariji_changelog_row,null)
    val v_text = item.findViewById[TextView](R.id.kimariji_changelog_row)
    v_text.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,text)))
    table.addView(item)
  }
  def addKimarijiChangelog(table:LinearLayout,fudanum:Option[Int]){
    fudanum.foreach{num =>
      if(num == 0){
        addRow(table,getActivity.getString(R.string.kimariji_changelog_joka))
        return
      }
      val msg_cur = if(!Utils.disableKimarijiLog){
        (if(Romanization.isJapanese(getActivity)){""}else{" "}) +
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
        val kimalist = AllFuda.getKimalist
        val alread_read = FudaListHelper.getAlreadyReadFromKimariji(num,kimari_cur,kimalist)
        var kima_prev = kimari_in_fudaset
        for(ar<-alread_read.inits.toArray.reverse if ar.nonEmpty ){
          val (_,read_order) = ar.last
          val kima = FudaListHelper.getKimarijiAtIndex(num,Some(read_order),kimalist)
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
    val view = LayoutInflater.from(getActivity).inflate(R.layout.kimariji_changelog,null)
    val table = view.findViewById[LinearLayout](R.id.kimariji_changelog)
    addKimarijiChangelog(table,getFudanum)
    builder
    .setTitle(getActivity.getString(R.string.kimariji_changelog_title))
    .setView(view)
    .setPositiveButton(android.R.string.ok,null)
    .create
  }
}
