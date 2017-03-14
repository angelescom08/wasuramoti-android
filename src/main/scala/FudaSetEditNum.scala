package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.{Context,DialogInterface}
import android.os.Bundle
import android.view.LayoutInflater

class FudaSetEditNumDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.fudasetedit_num,null)
    setView(view)
    super.onCreate(bundle)
  }
}
