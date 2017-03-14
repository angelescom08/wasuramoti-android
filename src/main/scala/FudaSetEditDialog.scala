package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.os.Bundle
import android.content.{Context,DialogInterface}
import android.text.{TextUtils,Html}
import android.view.{View,LayoutInflater}
import android.widget.{EditText,TextView}

class FudaSetEditDialog(
  context:Context,
  is_add:Boolean,
  callback:FudaSetWithSize=>Unit,
  orig_title:String) extends AlertDialog(context) with View.OnClickListener with DialogInterface.OnClickListener{

  val buttonMapping = Map(
      R.id.button_fudasetedit_list -> buttonFudasetEditList _,
      R.id.button_fudasetedit_initial -> buttonFudasetEditInitial _,
      R.id.button_fudasetedit_num -> buttonFudasetEditNum _,
      R.id.fudasetedit_help_html -> helpHtmlClicked _
    )

  override def onClick(view:View){
    buttonMapping.get(view.getId).foreach{_()}
  }

  override def onClick(dialog:DialogInterface,which:Int){
    which match {
      case DialogInterface.BUTTON_POSITIVE => {
        buttonOk()
      }
      case DialogInterface.BUTTON_NEGATIVE => {
        dialog.dismiss()
      }
    }
  }

  var data_id = None:Option[Long]

  def makeKimarijiSetFromBodyView(body_view:LocalizationEditText):Option[(String,Int)] = {
    val PATTERN_HIRAGANA = "[あ-ん]+".r
    var text = body_view.getLocalizationText()
    text = AllFuda.replaceFudaNumPattern(text)
    TrieUtils.makeKimarijiSet(PATTERN_HIRAGANA.findAllIn(text).toList)
  }

  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_title)
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset_edit,null)
    val title_view = view.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
    val body_view = view.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
    if(!is_add){
      title_view.setText(orig_title)
      val fs = FudaListHelper.selectFudasetByTitle(orig_title)
      fs.foreach{ f => body_view.setLocalizationText(f.body) }
      data_id = fs.map{_.id}
    }
    val help_view = view.findViewById(R.id.fudasetedit_help_html).asInstanceOf[TextView]
    help_view.setText(Html.fromHtml(context.getString(R.string.fudasetedit_help_html)))
    for(id <- buttonMapping.keys){
      view.findViewById(id).setOnClickListener(this)
    }
    setButton(DialogInterface.BUTTON_POSITIVE,context.getResources.getString(android.R.string.ok),this)
    setButton(DialogInterface.BUTTON_NEGATIVE,context.getResources.getString(android.R.string.cancel),this)
    setView(view)
    super.onCreate(bundle)
  }

  def helpHtmlClicked(){
    Utils.generalHtmlDialog(context,Right(R.string.fudasetedit_fudanum_html))
  }

  def buttonOk(){
   Globals.db_lock.synchronized{
      val dialog = this
      val title_view = this.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
      val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      val title = title_view.getText.toString
      if(TextUtils.isEmpty(title)){
        Utils.messageDialog(context,Right(R.string.fudasetedit_titleempty))
        return
      }
      if(FudaListHelper.isDuplicatedFudasetTitle(title,is_add,data_id)){
        Utils.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
        return
      }
      makeKimarijiSetFromBodyView(body_view) match {
      case None =>
        Utils.messageDialog(context,Right(R.string.fudasetedit_setempty) )
      case Some((kimari,st_size)) =>
        val message = context.getString(R.string.fudasetedit_confirm,new java.lang.Integer(st_size))
        Utils.confirmDialog(context,Left(message),() => {
          Utils.writeFudaSetToDB(context,title,kimari,st_size,if(is_add){None}else{Some(orig_title)})
          callback(new FudaSetWithSize(title,st_size))
          Globals.forceRefresh = true
          dialog.dismiss()
        })
      }
    }
  }

  def buttonFudasetEditList(){
    val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
    val kms = makeKimarijiSetFromBodyView(body_view) match{
      case None => ""
      case Some((s,_)) => s
    }
    val d = new FudaSetEditListDialog(context,kms,{body_view.setLocalizationText(_)})
    d.show()
  }

  def buttonFudasetEditInitial(){
    val dialog = new FudaSetEditInitialDialog(context)
    dialog.show()
  }
  def buttonFudasetEditNum(){
    val dialog = new FudaSetEditNumDialog(context)
    dialog.show()
  }
}
