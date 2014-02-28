package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.os.Bundle
import _root_.android.view.{View,ViewGroup,LayoutInflater}
import _root_.android.widget.Button
import _root_.android.support.v4.app.DialogFragment

class MovePositionDialog extends DialogFragment{
  val numbers_to_read = FudaListHelper.getOrQueryNumbersToRead(getActivity)
  var current_index = FudaListHelper.getOrQueryCurrentIndexWithSkip(getActivity)
  def setTitleWithNum(){
    val title = getActivity.getResources().getString(R.string.move_position_title) + ": " + current_index + " / " + numbers_to_read
    getDialog.setTitle(title)
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
  def setOnClick(view:View,id:Int,func:Unit=>Unit){
    view.findViewById(id).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener(){
        override def onClick(v:View){
          func()
        }
      })
  }
  override def onCreateView(inflater:LayoutInflater,container:ViewGroup,bundle:Bundle):View = {
    val view = inflater.inflate(R.layout.move_position,container,false)
    setTitleWithNum()
    setOnClick(view,R.id.button_ok, _ => onOk )
    setOnClick(view,R.id.button_cancel, _ => onCancel )
    setOnClick(view,R.id.move_button_prev, _ => onPrev )
    setOnClick(view,R.id.move_button_prev_ten, _ => onPrevTen )
    setOnClick(view,R.id.move_button_next, _ => onNext )
    setOnClick(view,R.id.move_button_next_ten, _ => onNextTen )
    view
  }
  def onOk(){
    val index = FudaListHelper.queryIndexWithSkip(getActivity,current_index)
    FudaListHelper.putCurrentIndex(getActivity,index)
    getActivity.asInstanceOf[WasuramotiActivity].refreshAndInvalidate()
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

