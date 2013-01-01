package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Dialog,AlertDialog}
import _root_.android.content.ContentValues
import _root_.android.content.{Context,DialogInterface}
import _root_.android.os.Bundle
import _root_.android.preference.DialogPreference
import _root_.android.preference.PreferenceActivity
import _root_.android.text.{TextUtils,Html}
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{AdapterView,ArrayAdapter,Spinner,EditText,TextView}
import _root_.java.util.ArrayList

class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var listItems = new ArrayList[String]()
  var adapter = None:Option[ArrayAdapter[String]]
  var spinner = None:Option[Spinner]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val pos = spinner.get.getSelectedItemPosition()
      val title = try{
        listItems.get(pos)
      }catch{
        case e:ArrayIndexOutOfBoundsException => return
      }
      persistString(title)
      val db = Globals.database.get.getWritableDatabase
      var cursor = db.query(Globals.TABLE_FUDASETS,Array("title","body"),"title = ?",Array(title),null,null,null,null)
      var body = ""
      if( cursor.getCount > 0 ){
        cursor.moveToFirst()
        body = cursor.getString(1)
      }
      cursor.close()
      db.close()
      val haveto_read = AllFuda.makeHaveToRead(body)
      val skip = AllFuda.list.toSet -- haveto_read
      val dbw = Globals.database.get.getWritableDatabase
      Utils.withTransaction(dbw, ()=>
        for((ss,flag) <- Array((haveto_read,0),(skip,1))){
          for( s <- ss ){
            val cv = new ContentValues()
            cv.put("skip",new java.lang.Integer(flag))
            val num = AllFuda.getFudaNum(s)
            dbw.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array(num.toString))
          }
        })
      dbw.close()
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
    adapter = Some(new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,listItems))
    // adapter.get.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    val spin = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    spin.setAdapter(adapter.get)
    spinner =  Some(spin)
    val db = Globals.database.get.getReadableDatabase
    var cursor = db.query(Globals.TABLE_FUDASETS,Array("title"),null,null,null,null,null,null)
    cursor.moveToFirst()
    listItems.clear()
    for( i <- 0 until cursor.getCount){
      val title = cursor.getString(0)
      listItems.add(title)
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
  def getSpinnerSelected(view:View):(ArrayAdapter[String],Int) = {
    val spinner = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val adapter = spinner.getAdapter().asInstanceOf[ArrayAdapter[String]]
    val pos = spinner.getSelectedItemPosition()
    return (adapter,pos)
  }
  def makeKimarijiSetFromBodyView(body_view:LocalizationEditText):Option[(String,Int)] = {
    val PATTERN_HIRAGANA = "[あ-ん]+".r
    var text = body_view.getLocalizationText()
    text = AllFuda.replaceFudaNumPattern(text)
    AllFuda.makeKimarijiSet(PATTERN_HIRAGANA.findAllIn(text).toList)
  }
  def editFudaSetBase(view:View,is_add:Boolean,orig_title:String=""){
    val context = this
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
    dialog.findViewById(R.id.fudasetedit_tap).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val builder= new AlertDialog.Builder(dialog.getContext)
        val view = LayoutInflater.from(dialog.getContext).inflate(R.layout.fudasetedit_fudanum,null)
        view.findViewById(R.id.fudasetedit_fudanum_text).asInstanceOf[TextView].setText(Html.fromHtml(getString(R.string.fudasetedit_fudanum_html)))
        builder.setView(view)
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
            override def onClick(interface:DialogInterface,which:Int){
            }
          });
        builder.create.show()
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
            if(is_add){
              db.insert(Globals.TABLE_FUDASETS,null,cv)
            }else{
              db.update(Globals.TABLE_FUDASETS,cv,"title = ?",Array(orig_title))
            }
            db.close()
            if(is_add){
              adapter.add(title)
            }else{
              adapter.remove(orig_title)
              adapter.insert(title,pos)
            }
            dialog.dismiss()},
            _ => {
              body_view.setLocalizationText(kimari)
            }
          )
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
    val title = adapter.getItem(pos)
    editFudaSetBase(view, false, title)
  }
  def deleteFudaSet(view: View){
    val (adapter,pos) = getSpinnerSelected(view)
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val title = adapter.getItem(pos)
    val message = getResources().getString(R.string.fudaset_confirmdelete,title)
    Utils.confirmDialog(this,Left(message), _ => {
      val db = Globals.database.get.getWritableDatabase
      db.delete(Globals.TABLE_FUDASETS,"title = ?", Array(title))
      db.close()
      adapter.remove(title)
    })
  }
}
