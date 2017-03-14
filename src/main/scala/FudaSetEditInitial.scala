package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.{Context,DialogInterface}
import android.os.Bundle

class FudaSetEditInitialDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.fudasetedit_initial)
  }

}
