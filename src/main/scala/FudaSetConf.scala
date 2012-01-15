package tami.pen.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.ContentValues
import _root_.android.content.{Context,DialogInterface}
import _root_.android.os.Bundle
import _root_.android.preference.DialogPreference
import _root_.android.preference.PreferenceActivity
import _root_.android.util.AttributeSet
import _root_.android.view.View
import _root_.android.widget.{AdapterView,ArrayAdapter,Spinner,EditText}
import _root_.java.util.ArrayList

import scala.util.matching.Regex

class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var listItems = new ArrayList[String]()
  var adapter = None:Option[ArrayAdapter[String]]
  var spinner = None:Option[Spinner]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val pos = spinner.get.getSelectedItemPosition()
      persistString(listItems.get(pos))
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    val view = super.onCreateDialogView()
    var persisted = getPersistedString("")
    adapter = Some(new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,listItems))
    val spin = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    spin.setAdapter(adapter.get)
    spinner =  Some(spin)
    val db = Globals.database.get.getReadableDatabase
    var cursor = db.query(Globals.TABLE_FUDASETS,Array("title"),null,null,null,null,null,null)
    cursor.moveToFirst()
    listItems.clear()
    for( i <- 0 to cursor.getCount - 1){
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


class FudaConfActivity extends PreferenceActivity{
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.fudaconf)
  }
  def getSpinnerSelected(view:View):(ArrayAdapter[String],Int) = {
    val spinner = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val adapter = spinner.getAdapter().asInstanceOf[ArrayAdapter[String]]
    val pos = spinner.getSelectedItemPosition()
    return (adapter,pos)
  }
  def editFudaSetBase(view:View,is_add:Boolean,orig_title:String=""){
    val context = this
    val (adapter,pos) = getSpinnerSelected(view)
    val dialog = new Dialog(this)
    dialog.setContentView(R.layout.fudaset_edit)
    dialog.setTitle(R.string.fudasetedit_title)
    val title_view = dialog.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
    val body_view = dialog.findViewById(R.id.fudasetedit_text).asInstanceOf[EditText]
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
      body_view.setText(body)
    }
    dialog.findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val title = title_view.getText().toString()
        if(title.isEmpty){
          Globals.messageDialog(context,Right(R.string.fudasetedit_titleempty))
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
          Globals.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
          return()
        }
        val body = body_view.getText().toString()
        var pat = new Regex("[あ-ん]+")
        val trie = CreateTrie.makeTrie(AllFuda.list)
        var st = Set[String]()
        for ( m <- pat.findAllIn(body) ){
          val list = trie.traversePrefix(m)
          st ++= list
        }
        if(st.isEmpty){
          Globals.messageDialog(context,Right(R.string.fudasetedit_setempty) )
        }else{
          val excl = AllFuda.list.toSet -- st
          val kimari = trie.traverseWithout(excl.toSeq).toList.sort(AllFuda.compareMusumefusahose).reduceLeft(_ + " " + _) 
          val template = getResources().getString(R.string.fudasetedit_confirm)
          val message = template.format(st.size)
          Globals.confirmDialog(context,Left(message), () => {
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
            () => {
              body_view.setText(kimari)
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
    val template = getResources().getString(R.string.fudaset_confirmdelete)
    val message = template.format(title)
    println(template)
    println(title)
    Globals.confirmDialog(this,Left(message), () => {
      val db = Globals.database.get.getWritableDatabase
      db.delete(Globals.TABLE_FUDASETS,"title = ?", Array(title))
      db.close()
      adapter.remove(title)
    })
  }
}

// vim: set ts=2 sw=2 et:
