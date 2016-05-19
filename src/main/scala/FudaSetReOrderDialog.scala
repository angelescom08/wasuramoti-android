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

  val BAR_COLOR_ACTIVE = Color.CYAN
  val BAR_COLOR_DEFAULT = Color.argb(255,0x44,0x44,0x44)

  val TEXT_COLOR_ACTIVE = Color.GRAY
  var TEXT_COLOR_DEFAULT = None:Option[Int]

  override def doWhenClose(view:View){
    //TODO
  }

  def setBorderColor(v:View,color:Int){
    v.findViewWithTag("horizontal_rule_droppable_body").setBackgroundColor(color)
  }

  def setTextColorOrDefault(v:TextView,color:Option[Int]){
    color match {
      case Some(c) =>
        if(TEXT_COLOR_DEFAULT.isEmpty){
          TEXT_COLOR_DEFAULT = Some(v.getCurrentTextColor)
        }
        v.setTextColor(c)
      case None =>
        TEXT_COLOR_DEFAULT.foreach{ c =>
          v.setTextColor(c)
        }
    }
  }

  def addBorder(list:ViewGroup):View = {
    val v = LayoutInflater.from(context).inflate(R.layout.horizontal_rule_droppable, null)
    v.setOnDragListener(new View.OnDragListener{
        override def onDrag(v:View, event:DragEvent):Boolean = {
          event.getAction match {
            case DragEvent.ACTION_DRAG_ENTERED =>
              setBorderColor(v,BAR_COLOR_ACTIVE)
            case DragEvent.ACTION_DRAG_EXITED | DragEvent.ACTION_DRAG_ENDED =>
              setBorderColor(v,BAR_COLOR_DEFAULT)
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
          setTextColorOrDefault(v.asInstanceOf[TextView],Some(TEXT_COLOR_ACTIVE))
          true
        }
      })
      btn.setOnDragListener(new View.OnDragListener{
        override def onDrag(v:View, event:DragEvent):Boolean = {
          event.getAction match{
            case DragEvent.ACTION_DRAG_LOCATION =>
              if(event.getY <= v.getHeight / 2){
                setBorderColor(borderPrev,BAR_COLOR_ACTIVE)
                setBorderColor(borderNext,BAR_COLOR_DEFAULT)
              }else{
                setBorderColor(borderPrev,BAR_COLOR_DEFAULT)
                setBorderColor(borderNext,BAR_COLOR_ACTIVE)
              }
            case DragEvent.ACTION_DRAG_EXITED =>
              setBorderColor(borderPrev,BAR_COLOR_DEFAULT)
              setBorderColor(borderNext,BAR_COLOR_DEFAULT)
            case DragEvent.ACTION_DRAG_ENDED =>
              setTextColorOrDefault(v.asInstanceOf[TextView],None)
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

