package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.view.View
import android.content.DialogInterface
import android.support.v4.app.Fragment

trait GetFudanum {
  self:Fragment =>
  def getFudanum():Option[Int] = {
    Option(self.getArguments.getSerializable("fudanum").asInstanceOf[Option[Int]]).flatten
  }
}

trait CustomAlertDialogTrait{
  self:AlertDialog =>
  def doWhenClose(view:View)
  def setViewAndButton(view:View){
    self.setView(view)
    self.setButton(DialogInterface.BUTTON_POSITIVE,self.getContext.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        doWhenClose(view)
        dismiss()
      }
    })
    self.setButton(DialogInterface.BUTTON_NEGATIVE,self.getContext.getResources.getString(android.R.string.cancel),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        dismiss()
      }
    })
  }
}

trait ButtonListener extends View.OnClickListener{
  self => 
  def buttonMapping():Map[Int,()=>Unit]
  override def onClick(view:View){
    buttonMapping.get(view.getId).foreach{_()}
  }
  def setButtonMapping(view:View){
    for(id <- buttonMapping.keys){
      view.findViewById(id).setOnClickListener(self)
    }
  }
}
