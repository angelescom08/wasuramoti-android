package karuta.hpnpwd.wasuramoti

import _root_.android.app.{AlertDialog,Dialog}
import _root_.android.os.{Bundle,Handler}
import _root_.android.view.{View,LayoutInflater,MotionEvent}
import _root_.android.widget.{TextView,Button}
import _root_.android.support.v4.app.DialogFragment
import _root_.android.content.DialogInterface

import java.lang.Runnable

class MovePositionDialog extends DialogFragment{
  var current_index = 0 // displayed number in TextView differs from real index
  var numbers_to_read = 0
  def setCurrentIndex(text:TextView){
    val (index_s,_) = Utils.makeDisplayedNum(current_index,numbers_to_read)
    text.setText(index_s.toString)
  }
  def incCurrentIndex(text:TextView,dx:Int):Boolean = {
    val offset = if(Utils.readFirstFuda){ 0 } else { 1 }
    val (n,total_s) = Utils.makeDisplayedNum(current_index+dx,numbers_to_read)
    val prev_index = current_index
    current_index = Math.min(Math.max(n,1),total_s) + offset
    if(prev_index != current_index){
      setCurrentIndex(text)
      true
    }else{
      false
    }
  }
  def setOnClick(view:View,id:Int,func:()=>Unit){
    view.findViewById(id).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener(){
        override def onClick(v:View){
          func()
        }
      })
  }

  abstract class RunnableWithCounter(var counter:Int) extends Runnable{
  }

  def startIncrement(handler:Handler,text:TextView,is_inc:Boolean){
    lazy val runnable:RunnableWithCounter = new RunnableWithCounter(0){
      override def run(){
        runnable.counter += 1
        val (dx,delay) = if(runnable.counter > 15){
          (3,200)
        }else if(runnable.counter > 7){
          (2,200)
        }else if(runnable.counter > 2){
          (1,300)
        }else{
          (1,500)
        }

        if(incCurrentIndex(text,if(is_inc){dx}else{-dx})){
          handler.postDelayed(runnable,delay)
        }
      }
    }
    runnable.run()
  }

  def endIncrement(handler:Handler){
    handler.removeCallbacksAndMessages(null)
  }

  def setIncrementWhilePressed(handler:Handler,view:View,text:TextView,id:Int,is_inc:Boolean){
    view.findViewById(id).asInstanceOf[Button].setOnTouchListener(
      new View.OnTouchListener(){
        override def onTouch(v:View, event:MotionEvent):Boolean = {
          event.getAction match {
            case MotionEvent.ACTION_DOWN => startIncrement(handler,text,is_inc)
            case MotionEvent.ACTION_UP => endIncrement(handler)
            case _ =>
          }
          false
        }
      }
    )
  }

  override def onSaveInstanceState(state:Bundle){
    super.onSaveInstanceState(state)
    state.putInt("current_index",current_index)
    state.putInt("numbers_to_read",numbers_to_read)
  }
  override def onCreateDialog(savedState:Bundle):Dialog = {
    if(savedState != null){
      numbers_to_read = savedState.getInt("numbers_to_read",0)
      current_index = savedState.getInt("current_index",0)
    }else{
      numbers_to_read = FudaListHelper.getOrQueryNumbersToReadAlt()
      current_index = FudaListHelper.getOrQueryCurrentIndexWithSkip(getActivity)
    }
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.move_position,null)
    val text = view.findViewById(R.id.move_position_index).asInstanceOf[TextView]
    builder.setView(view)
    .setTitle(R.string.move_position_title)
    .setNegativeButton(android.R.string.cancel,null)
    val handler = new Handler()
    setIncrementWhilePressed(handler,view,text,R.id.move_button_next,true)
    setIncrementWhilePressed(handler,view,text,R.id.move_button_prev,false)
    setOnClick(view,R.id.move_button_goto_num, {() => onOk() })
   
    val (index_s,total_s) = Utils.makeDisplayedNum(current_index, numbers_to_read)
    view.findViewById(R.id.move_position_total).asInstanceOf[TextView].setText(total_s.toString)
    setCurrentIndex(text)
    return builder.create
  }
  def onOk(){
    FudaListHelper.updateCurrentIndexWithSkip(getActivity,Some(current_index))
    getActivity.asInstanceOf[WasuramotiActivity].refreshAndInvalidate()
    getDialog.dismiss()
  }
  def onPrev(text:TextView){incCurrentIndex(text,-1)}
  def onNext(text:TextView){incCurrentIndex(text,1)}
}

