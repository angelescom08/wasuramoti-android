package karuta.hpnpwd.wasuramoti

import android.view.{View,LayoutInflater}
import android.os.Bundle
import android.content.Context
import android.widget.{ArrayAdapter,ListView,EditText}
import android.text.TextUtils

class FudaSetCopyMergeDialog(context:Context)
  extends CustomAlertDialog(context) with CommonDialog.WrappableDialog{

  class FudaSetItem(val id:Long, val title:String, val set_size:Int){
    override def toString():String = {
      s"${title} (${set_size})"
    }
  }

  def addItemsToListView(view:View){
    val container_view = view.findViewById(R.id.fudasetcopymerge_container).asInstanceOf[ListView]
    val setlist = FudaListHelper.selectFudasetAll.map{ fs => new FudaSetItem(fs.id,fs.title,fs.set_size) }
    val adapter = new ArrayAdapter[FudaSetItem](context,R.layout.my_simple_list_item_multiple_choice,setlist)
    container_view.setAdapter(adapter)
  }

  override def doWhenClose():Boolean = {Globals.db_lock.synchronized {
    val title_view = findViewById(R.id.fudasetcopymerge_name).asInstanceOf[EditText]
    val title = title_view.getText.toString
    if(TextUtils.isEmpty(title)){
      CommonDialog.messageDialog(context,Right(R.string.fudasetedit_titleempty))
      return false
    }
    if(FudaListHelper.isDuplicatedFudasetTitle(title,true,None)){
      CommonDialog.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
      return false
    }
    val items = Utils.getCheckedItemsFromListView[FudaSetItem](findViewById(R.id.fudasetcopymerge_container).asInstanceOf[ListView])
    if(items.isEmpty){
      CommonDialog.messageDialog(context,Right(R.string.fudaset_copymerge_notchecked))
      return false
    }
    val newset = FudaListHelper.queryMergedFudaset(items.map{_.id})
    newset.foreach{case (body,st_size) =>
      Utils.writeFudaSetToDB(context,title,body,st_size)
      val bundle = new Bundle
      bundle.putString("tag","fudaset_copymerge_done")
      bundle.putSerializable("fudaset",new FudaSetWithSize(title,st_size))
      callbackListener.onCommonDialogCallback(bundle)
    }
  }
    return true
  }
  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset_copymerge, null)
    addItemsToListView(view)
    setTitle(R.string.fudaset_copymerge_title)
    setView(view)
    super.onCreate(state)
  }
}
