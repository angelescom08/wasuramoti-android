package karuta.hpnpwd.wasuramoti

import _root_.android.app.{AlertDialog,Dialog}
import _root_.android.os.Bundle
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.Button
import _root_.android.support.v4.app.DialogFragment
import _root_.android.content.DialogInterface

class MovePositionDialog extends DialogFragment{
  var numbers_to_read = 0
  var current_index = 0
  def setTitleWithNum(dialog:Option[Dialog]=None){
    val (index_s,total_s) = Utils.makeDisplayedNum(current_index,numbers_to_read)
    val title = getActivity.getString(R.string.move_position_title) + ": " + index_s + " / " + total_s
    dialog.getOrElse(getDialog).setTitle(title)
  }
  def incCurrentIndex(dx:Int){
    val offset = if(Utils.readFirstFuda){ 0 } else { 1 }
    val (n,total_s) = Utils.makeDisplayedNum(current_index+dx,numbers_to_read)
    current_index = Math.min(Math.max(n,1),total_s) + offset
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
  override def onSaveInstanceState(state:Bundle){
    super.onSaveInstanceState(state)
    state.putInt("current_index",current_index)
    state.putInt("numbers_to_read",numbers_to_read)
  }
  override def onCreateDialog(savedState:Bundle):Dialog = {
    // there seems no way to assign multiple `var`'s in scala
    if(savedState != null){
      numbers_to_read = savedState.getInt("numbers_to_read",0)
      current_index = savedState.getInt("current_index",0)
    }else{
      numbers_to_read = FudaListHelper.getOrQueryNumbersToReadAlt(getActivity)
      current_index = FudaListHelper.getOrQueryCurrentIndexWithSkip(getActivity)
    }
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.move_position,null)
    builder.setView(view)
    .setPositiveButton(getActivity.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          onOk()
        }
      })
    .setNegativeButton(getActivity.getString(android.R.string.cancel),null)
    setOnClick(view,R.id.move_button_prev, _ => onPrev )
    setOnClick(view,R.id.move_button_prev_ten, _ => onPrevTen )
    setOnClick(view,R.id.move_button_next, _ => onNext )
    setOnClick(view,R.id.move_button_next_ten, _ => onNextTen )
    val dialog = builder.create
    setTitleWithNum(Some(dialog))
    return dialog
  }
  def onOk(){
    val index = FudaListHelper.queryIndexWithSkip(getActivity,current_index)
    FudaListHelper.putCurrentIndex(getActivity,index)
    getActivity.asInstanceOf[WasuramotiActivity].refreshAndInvalidate()
  }
  def onPrev(){incCurrentIndex(-1)}
  def onPrevTen(){incCurrentIndex(-10)}
  def onNext(){incCurrentIndex(1)}
  def onNextTen(){incCurrentIndex(10)}
}

