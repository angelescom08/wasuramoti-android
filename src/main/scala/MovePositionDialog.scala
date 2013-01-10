package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.Context
import _root_.android.os.Bundle
import _root_.android.view.View
import _root_.android.widget.Button

class MovePositionDialog(context:Context,doWhenOk:Unit=>Unit) extends Dialog(context){
  val numbers_to_read = FudaListHelper.getOrQueryNumbersToRead(context)
  var current_index = FudaListHelper.getOrQueryCurrentIndexWithSkip(context)
  def setTitleWithNum(){
    val title = context.getResources().getString(R.string.move_position_title) + ": " + current_index + " / " + numbers_to_read
    setTitle(title)
  }
  def incCurrentIndex(dx:Int){
    val n = current_index + dx
    current_index = if( n < 1 ){
      1
    }else if( n > numbers_to_read){
      numbers_to_read
    }else{
      n
    }
    setTitleWithNum()
  }
  def setOnClick(id:Int,func:Unit=>Unit){
    findViewById(id).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener(){
        override def onClick(v:View){
          func()
        }
      })
  }
  override def onCreate(bundle:Bundle){
    setContentView(R.layout.move_position)
    setTitleWithNum()
    setOnClick(R.id.button_ok, _ => onOk )
    setOnClick(R.id.button_cancel, _ => onCancel )
    setOnClick(R.id.move_button_prev, _ => onPrev )
    setOnClick(R.id.move_button_prev_ten, _ => onPrevTen )
    setOnClick(R.id.move_button_next, _ => onNext )
    setOnClick(R.id.move_button_next_ten, _ => onNextTen )
  }
  def onOk(){
    val index = FudaListHelper.queryIndexWithSkip(context,current_index)
    FudaListHelper.putCurrentIndex(context,index)
    doWhenOk()
    dismiss()
  }
  def onCancel(){
    dismiss()
  }
  def onPrev(){incCurrentIndex(-1)}
  def onPrevTen(){incCurrentIndex(-10)}
  def onNext(){incCurrentIndex(1)}
  def onNextTen(){incCurrentIndex(10)}
}

