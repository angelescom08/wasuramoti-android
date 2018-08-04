package karuta.hpnpwd.wasuramoti

import android.view.{View,LayoutInflater,ViewGroup,DragEvent}
import android.os.Bundle
import android.content.{ClipData,Context,ContentValues}
import android.widget.TextView
import android.widget.Button

@KeepConstructor
class FudaSetReOrderDialog(context:Context) extends CustomAlertDialog(context)
  with CommonDialog.WrappableDialog{

  val BAR_COLOR_ACTIVE =  Utils.attrColor(context,R.attr.reorderBarActiveColor)
  val BAR_COLOR_DEFAULT = Utils.attrColor(context,R.attr.reorderBarDefaultColor)

  val TEXT_COLOR_ACTIVE = Utils.attrColor(context,R.attr.reorderTextActiveColor)
  var TEXT_COLOR_DEFAULT = None:Option[Int]


  override def doWhenClose():Boolean = {
    val list = findViewById[ViewGroup](R.id.fudaset_reorder_list)
    val fudaset_ids = (0 until list.getChildCount).map{ i=>
      Option(list.getChildAt(i).getTag(R.id.tag_fudaset_id))
    }.flatten
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, ()=>{
      for((fudaset_id,index) <- fudaset_ids.zipWithIndex){
        val cv = new ContentValues
        cv.put("set_order",new java.lang.Integer(index+1))
        db.update(Globals.TABLE_FUDASETS,cv,"id = ?", Array(fudaset_id.toString))
      }
    })
    db.close()
    val bundle = new Bundle
    bundle.putString("tag","fudaset_reorder_done")
    callbackListener.onCommonDialogCallback(bundle)
    return true
  }

  def setBorderColor(v:View,color:Int){
    v.findViewById[View](R.id.horizontal_rule_droppable_body).setBackgroundColor(color)
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
    val list = root.findViewById[ViewGroup](R.id.fudaset_reorder_list)

    def getPrevNextBorder(v:View):(View,View) = {
      val index = list.indexOfChild(v)
      (list.getChildAt(index-1),list.getChildAt(index+1))
    }

    def doWhenDrop = (v:View,data:ClipData) => {
      setBorderColor(v,BAR_COLOR_DEFAULT)
      val index_from = data.getItemAt(0).getText.toString.toInt
      doWhenDropAlt(v,index_from)
    }

    def doWhenDropAlt(v:View,index_from:Int){
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

    lazy val borderDragListener = new View.OnDragListener{
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

    lazy val buttonDragListener = new View.OnDragListener{
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

    lazy val buttonLongClickListener = new View.OnLongClickListener(){
          override def onLongClick(v:View):Boolean = {
            val data = ClipData.newPlainText("child_index",list.indexOfChild(v).toString)
            v.startDrag(data, new View.DragShadowBuilder(v), v, 0)
            setTextColorOrDefault(v.asInstanceOf[TextView],Some(TEXT_COLOR_ACTIVE))
            true
          }
        }

    // only for API < 11
    var startSwapFrom = None:Option[Int]
    lazy val buttonClickListener = new View.OnClickListener(){
          override def onClick(v:View) = {
            startSwapFrom match {
              case Some(n) =>
                setTextColorOrDefault(list.getChildAt(n).asInstanceOf[TextView],None)
                val (borderPrev,borderNext) = getPrevNextBorder(v)
                val cur = list.indexOfChild(v)
                if(cur < n){
                  doWhenDropAlt(borderPrev,n)
                }else{
                  doWhenDropAlt(borderNext,n)
                }
                startSwapFrom = None
              case None =>
                setTextColorOrDefault(v.asInstanceOf[TextView],Some(Utils.attrColor(context,R.attr.reorderTextDefaultColor)))
                startSwapFrom = Some(list.indexOfChild(v))
            }
          }
    }

    def addBorder() = {
      val v = LayoutInflater.from(context).inflate(R.layout.horizontal_rule_droppable, null)
      if(android.os.Build.VERSION.SDK_INT >= 11){
        v.setOnDragListener(borderDragListener)
      }
      list.addView(v)
    }
    addBorder()
    for(fs <- FudaListHelper.selectFudasetAll){
      val btn = new Button(context)
      btn.setText(fs.title)
      if(android.os.Build.VERSION.SDK_INT >= 11){
        btn.setOnLongClickListener(buttonLongClickListener)
        btn.setOnDragListener(buttonDragListener)
      }else{
        btn.setOnClickListener(buttonClickListener)
      }
      btn.setTag(R.id.tag_fudaset_id,fs.id)
      list.addView(btn)
      addBorder()
    }
    setTitle(R.string.fudaset_reorder_title)
    setView(root) 
    super.onCreate(state)
  }


}
