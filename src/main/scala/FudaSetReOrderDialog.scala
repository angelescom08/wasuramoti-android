package karuta.hpnpwd.wasuramoti

import android.view.{View,LayoutInflater,ViewGroup,DragEvent}
import android.os.Bundle
import android.content.{ClipData,Context}
import android.widget.{ArrayAdapter,ListView,EditText,TextView}
import android.app.AlertDialog
import android.text.TextUtils
import android.widget.Button
import android.graphics.Color

class FudaSetReOrderDialog(
  context:Context
  ) extends AlertDialog(context) with CustomAlertDialogTrait{

  val ACTIVE_COLOR = Color.argb(255,0xFF,0xFF,0x00)
  val DEFAULT_COLOR = Color.argb(255,0x44,0x44,0x44)

  override def doWhenClose(view:View){
    //TODO
  }

  def setBorderColor(v:View,color:Int){
    v.findViewWithTag("horizontal_rule_droppable_body").setBackgroundColor(color)
  }

  def addBorder(list:ViewGroup):View = {
    val v = LayoutInflater.from(context).inflate(R.layout.horizontal_rule_droppable, null)
    v.setOnDragListener(new View.OnDragListener{
        override def onDrag(v:View, event:DragEvent):Boolean = {
          event.getAction match {
            case DragEvent.ACTION_DRAG_ENTERED =>
              setBorderColor(v,ACTIVE_COLOR)
            case DragEvent.ACTION_DRAG_EXITED | DragEvent.ACTION_DRAG_ENDED =>
              setBorderColor(v,DEFAULT_COLOR)
            case _ => 
          }
          true
    }})
    list.addView(v)
    v
  }

  override def onCreate(state:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset_reorder, null)
    val list = view.findViewById(R.id.fudaset_list).asInstanceOf[ViewGroup]
    var lastBorder = addBorder(list)
    for(fs <- FudaListHelper.selectFudasetAll){
      val btn = new Button(context)
      btn.setText(fs.title)
      list.addView(btn)
      val borderPrev = lastBorder // have to trap variable in closure
      val borderNext = addBorder(list)
      btn.setOnLongClickListener(new View.OnLongClickListener(){
        override def onLongClick(v:View):Boolean = {
          val data = ClipData.newPlainText("text",fs.title)
          v.startDrag(data, new View.DragShadowBuilder(v), v, 0)
          true
        }
      })
      btn.setOnDragListener(new View.OnDragListener{
        override def onDrag(v:View, event:DragEvent):Boolean = {
          event.getAction match{
            case DragEvent.ACTION_DRAG_LOCATION =>
              if(event.getY <= v.getHeight / 2){
                setBorderColor(borderPrev,ACTIVE_COLOR)
                setBorderColor(borderNext,DEFAULT_COLOR)
              }else{
                setBorderColor(borderPrev,DEFAULT_COLOR)
                setBorderColor(borderNext,ACTIVE_COLOR)
              }
            case DragEvent.ACTION_DRAG_EXITED =>
              setBorderColor(borderPrev,DEFAULT_COLOR)
              setBorderColor(borderNext,DEFAULT_COLOR)
            case _ =>
          }
          true
        }
      })
      lastBorder = borderNext
    }
    setViewAndButton(view) 
    super.onCreate(state)
  }

}

