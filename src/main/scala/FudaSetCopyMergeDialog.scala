package karuta.hpnpwd.wasuramoti

import _root_.android.view.{View,LayoutInflater}
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.app.AlertDialog

class FudaSetCopyMergeDialog(context:Context) extends AlertDialog(context) with CustomAlertDialogTrait{
  override def doWhenClose(view:View){
    // TODO: implement this
  }
  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset_copymerge, null)
    // TODO: implement this
    setTitle(R.string.fudaset_copymerge_title)
    setViewAndButton(view)
    super.onCreate(state)
  }
}
