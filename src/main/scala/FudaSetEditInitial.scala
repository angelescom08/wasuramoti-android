package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.{Context,DialogInterface}
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton}
import android.view.LayoutInflater

class FudaSetEditInitialDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_initial_title)
    val view = LayoutInflater.from(context).inflate(R.layout.fudasetedit_initial,null)
    val container = view.findViewById(R.id.fudasetedit_initial_list).asInstanceOf[LinearLayout]
    val sar = context.getResources.getStringArray(R.array.fudasetedit_initials)
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        btn.setText(c.toString)
        btn.setTextOn(c.toString)
        btn.setTextOff(c.toString)
        row.addView(btn)
      }
      container.addView(row)
    }
    setView(view)
    super.onCreate(bundle)
  }

}
