package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.ContentValues
import _root_.android.content.Context
import _root_.android.preference.DialogPreference
import _root_.android.text.{TextUtils,Html}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{AdapterView,ArrayAdapter,Spinner,EditText,TextView}
import _root_.java.util.ArrayList

class FudaSetWithSize(val title:String, val num:Int){
  override def toString():String = {
    title + " (" + num + ")"
  }
  def equals(obj:FudaSetWithSize):Boolean = {
    title.equals(obj.title)
  }

}
class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var listItems = new ArrayList[FudaSetWithSize]()
  var adapter = None:Option[ArrayAdapter[FudaSetWithSize]]
  var spinner = None:Option[Spinner]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val pos = spinner.get.getSelectedItemPosition()
      val title = try{
        listItems.get(pos).title
      }catch{
        case _:IndexOutOfBoundsException => return
      }
      persistString(title)
      FudaListHelper.updateSkipList(title)
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    // Using XML Attribute ``android:dialogLayout="@layout/fudaset"'' with ``android:onClick="..."'' does not work in Android 3.x.
    // That is because it creates each button with context instanciated from ContextThemeWrapper, and causes
    // ``java.lang.IllegalStateException: Could not find a method ... for onClick handler on view class android.widget.Button''
    // Therefore we set the layout here.
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset, null)

    var persisted = getPersistedString("")
    adapter = Some(new ArrayAdapter[FudaSetWithSize](context,android.R.layout.simple_spinner_item,listItems))
    // adapter.get.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    val spin = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    spin.setAdapter(adapter.get)
    spinner =  Some(spin)
    val db = Globals.database.get.getReadableDatabase
    var cursor = db.query(Globals.TABLE_FUDASETS,Array("title","set_size"),null,null,null,null,null,null)
    cursor.moveToFirst()
    listItems.clear()
    for( i <- 0 until cursor.getCount){
      val title = cursor.getString(0)
      val num = cursor.getInt(1)
      listItems.add(new FudaSetWithSize(title,num))
      if(title == persisted){
        spin.setSelection(i)
      }
      cursor.moveToNext()
    }
    cursor.close()
    db.close()
    adapter.get.notifyDataSetChanged()
    return view
  }
}

trait FudaSetTrait{
  this:Context =>
  def getSpinnerSelected(view:View):(ArrayAdapter[FudaSetWithSize],Int) = {
    val spinner = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val adapter = spinner.getAdapter().asInstanceOf[ArrayAdapter[FudaSetWithSize]]
    val pos = spinner.getSelectedItemPosition()
    return (adapter,pos)
  }
  def makeKimarijiSetFromBodyView(body_view:LocalizationEditText):Option[(String,Int)] = {
    val PATTERN_HIRAGANA = "[あ-ん]+".r
    var text = body_view.getLocalizationText()
    text = AllFuda.replaceFudaNumPattern(text)
    AllFuda.makeKimarijiSet(PATTERN_HIRAGANA.findAllIn(text).toList)
  }
  def editFudaSetBase(view:View,is_add:Boolean,orig_fs:FudaSetWithSize=null){
    val context = this
    val orig_title = if( orig_fs == null ){ "" }else{orig_fs.title}
    val (adapter,pos) = getSpinnerSelected(view)
    val dialog = new Dialog(this)
    dialog.setContentView(R.layout.fudaset_edit)
    dialog.setTitle(R.string.fudasetedit_title)
    val title_view = dialog.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
    val body_view = dialog.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
    var data_id = -1L
    if(!is_add){
      title_view.setText(orig_title)
      val db = Globals.database.get.getReadableDatabase
      var cursor = db.query(Globals.TABLE_FUDASETS,Array("id","title","body"),"title = ?",Array(orig_title),null,null,null,null)
      cursor.moveToFirst()
      data_id = cursor.getLong(0)
      val body = cursor.getString(2)
      cursor.close()
      db.close()
      body_view.setLocalizationText(body)
    }
    val help_view = dialog.findViewById(R.id.fudasetedit_help_html).asInstanceOf[TextView]
    help_view.setText(Html.fromHtml(getString(R.string.fudasetedit_help_html)))
    help_view.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        Utils.generalHtmlDialog(dialog.getContext,R.string.fudasetedit_fudanum_html)
      }
    })
    dialog.findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val title = title_view.getText().toString()
        if(TextUtils.isEmpty(title)){
          Utils.messageDialog(context,Right(R.string.fudasetedit_titleempty))
          return()
        }
        val db = Globals.database.get.getReadableDatabase
        var cursor = db.query(Globals.TABLE_FUDASETS,Array("id","title"),"title = ?",Array(title),null,null,null,null)
        val count = cursor.getCount()
        cursor.moveToFirst()
        val is_duplicate =
          if(!is_add && count > 0){
            data_id != cursor.getLong(0)
          }else{
            is_add && count > 0
          }
        cursor.close()
        db.close()
        if(is_duplicate){
          Utils.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
          return()
        }
        val body =
        makeKimarijiSetFromBodyView(body_view) match {
        case None =>
          Utils.messageDialog(context,Right(R.string.fudasetedit_setempty) )
        case Some((kimari,st_size)) =>
          val message = getResources().getString(R.string.fudasetedit_confirm,new java.lang.Integer(st_size))
          Utils.confirmDialog(context,Left(message),_ => {
            val cv = new ContentValues()
            val db = Globals.database.get.getWritableDatabase
            cv.put("title",title)
            cv.put("body",kimari)
            cv.put("set_size",new java.lang.Integer(st_size))
            if(is_add){
              db.insert(Globals.TABLE_FUDASETS,null,cv)
            }else{
              db.update(Globals.TABLE_FUDASETS,cv,"title = ?",Array(orig_title))
            }
            db.close()
            if(is_add){
              adapter.add(new FudaSetWithSize(title,st_size))
            }else{
              adapter.remove(orig_fs)
              adapter.insert(new FudaSetWithSize(title,st_size),pos)
            }
            dialog.dismiss()
          })
        }
      }
    })
    dialog.findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
         dialog.dismiss()
      }
    })
    dialog.findViewById(R.id.button_fudasetedit_list).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val kms = makeKimarijiSetFromBodyView(body_view) match{
          case None => ""
          case Some((s,_)) => s
        }
        val d = new FudaSetEditListDialog(context,kms,{body_view.setLocalizationText(_)})
        d.show()
      }
    })
    dialog.show()
  }
  def addFudaSet(view:View){
    editFudaSetBase(view, true)
  }

  def editFudaSet(view: View){
    val (adapter,pos) = getSpinnerSelected(view)
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.getItem(pos)
    editFudaSetBase(view, false, fs)
  }
  def deleteFudaSet(view: View){
    val (adapter,pos) = getSpinnerSelected(view)
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.getItem(pos)
    val message = getResources().getString(R.string.fudaset_confirmdelete,fs.title)
    Utils.confirmDialog(this,Left(message), _ => {
      val db = Globals.database.get.getWritableDatabase
      db.delete(Globals.TABLE_FUDASETS,"title = ?", Array(fs.title))
      db.close()
      adapter.remove(fs)
    })
  }
}
