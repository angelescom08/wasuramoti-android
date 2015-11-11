package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.text.{TextUtils,Html}
import _root_.android.view.View
import _root_.android.widget.{ArrayAdapter,Spinner,EditText,TextView}

class FudaSetEditDialog(
  context:Context,
  is_add:Boolean,
  callback:FudaSetWithSize=>Unit,
  orig_title:String) extends Dialog(context) with View.OnClickListener{

  val buttonMapping = Map(
      R.id.button_cancel -> buttonCancel _,
      R.id.button_ok -> buttonOk _,
      R.id.button_fudasetedit_list -> buttonFudasetEditList _,
      R.id.fudasetedit_help_html -> helpHtmlClicked _
    )

  var data_id = None:Option[Long]

  def selectFudasetByTitle(title:String):(Long,String) = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDASETS,Array("id","body"),"title = ?",Array(title),null,null,null,null)
    cursor.moveToFirst()
    val did = cursor.getLong(0)
    val body = cursor.getString(1)
    cursor.close()
    //db.close()
    (did,body)
  }

  def makeKimarijiSetFromBodyView(body_view:LocalizationEditText):Option[(String,Int)] = {
    val PATTERN_HIRAGANA = "[あ-ん]+".r
    var text = body_view.getLocalizationText()
    text = AllFuda.replaceFudaNumPattern(text)
    TrieUtils.makeKimarijiSet(PATTERN_HIRAGANA.findAllIn(text).toList)
  }

  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    this.setContentView(R.layout.fudaset_edit)
    this.setTitle(R.string.fudasetedit_title)
    val title_view = this.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
    val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
    if(!is_add){
      title_view.setText(orig_title)
      val (did,body) = selectFudasetByTitle(orig_title)
      body_view.setLocalizationText(body)
      data_id = Some(did)
    }
    val help_view = this.findViewById(R.id.fudasetedit_help_html).asInstanceOf[TextView]
    help_view.setText(Html.fromHtml(context.getString(R.string.fudasetedit_help_html)))
    for(id <- buttonMapping.keys){
      this.findViewById(id).setOnClickListener(this)
    }
  }
  override def onClick(view:View){
    buttonMapping.get(view.getId).foreach{_(view)}
  }

  def helpHtmlClicked(view:View){
    Utils.generalHtmlDialog(context,Right(R.string.fudasetedit_fudanum_html))
  }

  def buttonOk(view:View){
   Globals.db_lock.synchronized{
      val dialog = this
      val title_view = this.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
      val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      val title = title_view.getText().toString()
      if(TextUtils.isEmpty(title)){
        Utils.messageDialog(context,Right(R.string.fudasetedit_titleempty))
        return()
      }
      val db = Globals.database.get.getReadableDatabase
      val cursor = db.query(Globals.TABLE_FUDASETS,Array("id","title"),"title = ?",Array(title),null,null,null,null)
      val count = cursor.getCount()
      cursor.moveToFirst()
      val is_duplicate =
        if(!is_add && count > 0){
          data_id.get != cursor.getLong(0)
        }else{
          is_add && count > 0
        }
      cursor.close()
      //db.close()
      if(is_duplicate){
        Utils.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
        return()
      }
      makeKimarijiSetFromBodyView(body_view) match {
      case None =>
        Utils.messageDialog(context,Right(R.string.fudasetedit_setempty) )
      case Some((kimari,st_size)) =>
        val message = context.getString(R.string.fudasetedit_confirm,new java.lang.Integer(st_size))
        Utils.confirmDialog(context,Left(message),() => {
          Utils.writeFudaSetToDB(title,kimari,st_size,is_add,orig_title)
          callback(new FudaSetWithSize(title,st_size))
          Globals.forceRefresh = true
          dialog.dismiss()
        })
      }
    }
  }

  def buttonCancel(view:View){
    dismiss()
  }

  def buttonFudasetEditList(view:View){
    val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
    val kms = makeKimarijiSetFromBodyView(body_view) match{
      case None => ""
      case Some((s,_)) => s
    }
    val d = new FudaSetEditListDialog(context,kms,{body_view.setLocalizationText(_)})
    d.show()
  }

}
