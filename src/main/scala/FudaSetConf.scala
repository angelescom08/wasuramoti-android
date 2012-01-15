package tami.pen.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.ContentValues
import _root_.android.content.{Context,DialogInterface}
import _root_.android.os.Bundle
import _root_.android.preference.DialogPreference
import _root_.android.preference.PreferenceActivity
import _root_.android.util.AttributeSet
import _root_.android.view.View
import _root_.android.widget.{ArrayAdapter,Spinner,EditText}
import _root_.java.util.ArrayList

import scala.util.matching.Regex

class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var listItems = new ArrayList[String]()
  var adapter = None:Option[ArrayAdapter[String]]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onCreateDialogView():View = {
    val view = super.onCreateDialogView()
    adapter = Some(new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,listItems))
    val vw = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    vw.setAdapter(adapter.get)
    val db = Globals.database.get.getReadableDatabase
    var cursor = db.query(Globals.TABLE_FUDASETS,Array("title"),null,null,null,null,null,null)
    cursor.moveToFirst()
    println("Spinner Count: " + cursor.getCount)
    listItems.clear()
    for( i <- 0 to cursor.getCount - 1){
      val title = cursor.getString(0)
      listItems.add(title)
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
  def addFudaSet(view:View){
    val context = this
    val root_view = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val adapter = root_view.getAdapter().asInstanceOf[ArrayAdapter[String]]
    val dialog = new Dialog(this)
    dialog.setContentView(R.layout.fudaset_edit)
    dialog.setTitle(R.string.fudasetedit_title)
    dialog.findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val title = dialog.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText].getText().toString()
        val body = dialog.findViewById(R.id.fudasetedit_text).asInstanceOf[EditText].getText().toString()
        println(title)
        println(body)
        var pat = new Regex("[あ-ん]+")
        val trie = CreateTrie.makeTrie(AllFuda.list)
        var st = Set[String]()
        for ( m <- pat.findAllIn(body) ){
          val list = trie.traversePrefix(m)
          st ++= list
        }
        if(st.isEmpty){
          
        }else{
          val excl = AllFuda.list.toSet -- st
          val kimari = trie.traverseWithout(excl.toSeq).toList.sort(AllFuda.compareMusumefusahose).reduceLeft(_ + " " + _) 
          val template = getResources().getString(R.string.fudasetedit_confirm)
          val message = template.format(st.size)
          Globals.confirmDialog(context,message, () => {
            val cv = new ContentValues()
            val db = Globals.database.get.getWritableDatabase
            cv.put("title",title)
            cv.put("body",body)
            db.insert(Globals.TABLE_FUDASETS,null,cv)
            db.close()
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
    dialog.show()
  }
}

// vim: set ts=2 sw=2 et:
