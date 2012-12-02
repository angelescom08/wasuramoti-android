package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.view.View
import _root_.android.widget.{ArrayAdapter,ListView}

class FudaSetEditListDialog(context:Context,kimarijis:String,onOk:String=>Unit) extends Dialog(context){

  class FudaListItem(val str:String, val fudanum:Int) {
    override def toString():String = Romanization.jap_to_local(context,str)
  }
  
  def addItemsToListView(container_view:ListView){
    val have_to_read = AllFuda.makeHaveToRead(kimarijis)
    val fudalist = AllFuda.list.zipWithIndex.sortBy{ _._1 }.map{ _ match { case (s,i) => new FudaListItem(s,i+1)} }
    val adapter = new ArrayAdapter[FudaListItem](context,android.R.layout.simple_list_item_multiple_choice,fudalist)
    container_view.setAdapter(adapter)
    for( (s,pos) <- fudalist.zipWithIndex ){
      if(have_to_read.contains(s.str)){
        container_view.setItemChecked(pos,true)
      }
    }
  }

  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.fudasetedit_list)
    setTitle(R.string.button_fudasetedit_list)
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[ListView]
    addItemsToListView(container_view)

    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        dismiss()
      }
    })
    findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val poss = container_view.getCheckedItemPositions()
        val adapter = container_view.getAdapter().asInstanceOf[ArrayAdapter[FudaListItem]]
        val num_list = (0 until poss.size()).filter{poss.valueAt(_)}.map{ poss.keyAt(_) }.map{ adapter.getItem(_).fudanum }.toList
        val body = AllFuda.makeKimarijiSetFromNumList(num_list) match {
          case None => ""
          case Some((s,_)) => s
        }
        onOk(body)
        dismiss()
      }
    })
  }
}
