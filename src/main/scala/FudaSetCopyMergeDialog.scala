package karuta.hpnpwd.wasuramoti

import _root_.android.view.{View,LayoutInflater}
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.widget.{ArrayAdapter,ListView}
import _root_.android.app.AlertDialog

class FudaSetCopyMergeDialog(context:Context) extends AlertDialog(context) with CustomAlertDialogTrait{

  class FudaSetItem(val title:String, val set_size:Int){
    override def toString():String = {
      s"${title} (${set_size})"
    }
  }

  def addItemsToListView(view:View){
    val container_view = view.findViewById(R.id.fudasetcopymerge_container).asInstanceOf[ListView]
    val setlist = FudaListHelper.selectFudasetAll.map{ x => new FudaSetItem(x._1,x._2) }
    val adapter = new ArrayAdapter[FudaSetItem](context,R.layout.my_simple_list_item_multiple_choice,setlist)
    container_view.setAdapter(adapter)
  }

  override def doWhenClose(view:View){
    // TODO: implement this
  }
  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset_copymerge, null)
    addItemsToListView(view)
    // TODO implement this
    setTitle(R.string.fudaset_copymerge_title)
    setViewAndButton(view)
    super.onCreate(state)
  }
}
