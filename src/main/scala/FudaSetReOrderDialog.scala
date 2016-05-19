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

  override def onCreate(state:Bundle){
    val root = LayoutInflater.from(context).inflate(R.layout.fudaset_reorder, null)
    val list = root.findViewById(R.id.fudaset_list).asInstanceOf[ViewGroup]

    def getPrevNextBorder(v:View):(View,View) = {
      val index = list.indexOfChild(v)
      (list.getChildAt(index-1),list.getChildAt(index+1))
    }

    def doWhenDrop(v:View,data:ClipData){
      val index_from = data.getItemAt(0).getText.toString.toInt
      val index_temp = list.indexOfChild(v)
      if((index_temp - index_from).abs == 1){
        return
      }

      val v_btn = list.getChildAt(index_from)
      val v_bar = list.getChildAt(index_from+1)
      list.removeView(v_btn)
      list.removeView(v_bar)

      val index_to = list.indexOfChild(v)
      list.addView(v_btn,index_to)
      list.addView(v_bar,index_to)
    }

    val borderDragListener = new View.OnDragListener{
          override def onDrag(v:View, event:DragEvent):Boolean = {
            event.getAction match {
              case DragEvent.ACTION_DRAG_ENTERED =>
                setBorderColor(v,BAR_COLOR_ACTIVE)
              case DragEvent.ACTION_DRAG_EXITED | DragEvent.ACTION_DRAG_ENDED =>
                setBorderColor(v,BAR_COLOR_DEFAULT)
              case DragEvent.ACTION_DROP =>
                doWhenDrop(v,event.getClipData)
              case _ => 
            }
            true
      }}

    val buttonDragListener = new View.OnDragListener{
          override def onDrag(v:View, event:DragEvent):Boolean = {
            event.getAction match{
              case DragEvent.ACTION_DRAG_LOCATION =>
                val (borderPrev,borderNext) = getPrevNextBorder(v) // TODO cache these variables ?
                if(event.getY <= v.getHeight / 2){
                  setBorderColor(borderPrev,BAR_COLOR_ACTIVE)
                  setBorderColor(borderNext,BAR_COLOR_DEFAULT)
                }else{
                  setBorderColor(borderPrev,BAR_COLOR_DEFAULT)
                  setBorderColor(borderNext,BAR_COLOR_ACTIVE)
                }
              case DragEvent.ACTION_DRAG_EXITED =>
                val (borderPrev,borderNext) = getPrevNextBorder(v)
                setBorderColor(borderPrev,BAR_COLOR_DEFAULT)
                setBorderColor(borderNext,BAR_COLOR_DEFAULT)
              case DragEvent.ACTION_DRAG_ENDED =>
                setTextColorOrDefault(v.asInstanceOf[TextView],None)
              case DragEvent.ACTION_DROP =>
                val (borderPrev,borderNext) = getPrevNextBorder(v)
                if(event.getY <= v.getHeight / 2){
                  doWhenDrop(borderPrev,event.getClipData)
                }else{
                  doWhenDrop(borderNext,event.getClipData)
                }

              case _ =>
            }
            true
          }
        }

    val buttonLongClickListener = new View.OnLongClickListener(){
          override def onLongClick(v:View):Boolean = {
            val data = ClipData.newPlainText("child_index",list.indexOfChild(v).toString)
            v.startDrag(data, new View.DragShadowBuilder(v), v, 0)
            setTextColorOrDefault(v.asInstanceOf[TextView],Some(TEXT_COLOR_ACTIVE))
            true
          }
        }
    def addBorder() = {
      val v = LayoutInflater.from(context).inflate(R.layout.horizontal_rule_droppable, null)
      v.setOnDragListener(borderDragListener)
      list.addView(v)
    }
    addBorder()
    for(fs <- FudaListHelper.selectFudasetAll){
      val btn = new Button(context)
      btn.setText(fs.title)
      list.addView(btn)
      btn.setOnLongClickListener(buttonLongClickListener)
      btn.setOnDragListener(buttonDragListener)
      addBorder()
    }
    setViewAndButton(root) 
    super.onCreate(state)
  }


}
