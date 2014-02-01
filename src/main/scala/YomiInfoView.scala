package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.View
import _root_.android.graphics.{Canvas,Typeface,Paint,Color,Rect}
import _root_.android.util.AttributeSet
class YomiInfoView(context:Context, attrs:AttributeSet) extends View(context, attrs) {
  val MARGIN_TOP = Array(0.03,0.06,0.09,0.045,0.075) // from left to right
  val MARGIN_BOTTOM = 0.05
  val MARGIN_LR = 0.1
  val SPACE_H = 0.03
  val SPACE_V = 0.02

  def calcVerticalBound(str:String,paint:Paint):(Float,Float) = {
    var w = 0
    var h = 0
    for(s <- str){
      val r = new Rect()
      paint.getTextBounds(s.toString,0,1,r)
      w = Math.max(w, r.right - r.left)
      h += r.bottom - r.top
    }
    (w,h)
  }

  // Typeface of paint must be set before calling this function
  def calculateTextSize(text_array:Array[String],paint:Paint):Int ={
    val width = getWidth
    val height = getHeight
    val bounds = (for(t<-text_array)yield(calcVerticalBound(t,paint)))
    val r1 = (for((((w,h),m),t) <- bounds.zip(MARGIN_TOP).zip(text_array)) yield (((1-m-MARGIN_BOTTOM-SPACE_V*t.length)*height/h))).min
    val r2 = (1-MARGIN_LR*2-text_array.length*SPACE_H)*width/bounds.map{case(w,h)=>w}.sum
    (Math.min(r1,r2)*paint.getTextSize).toInt
  }
  def verticalText(paint:Paint,canvas:Canvas,startx:Int,starty:Int,text:String){
    var y = starty
    for(t <- text){
      val r = new Rect()
      paint.getTextBounds(t.toString,0,1,r)
      paint.setTextAlign(Paint.Align.CENTER)
      val xx = startx.toInt
      val yy = (y - r.top).toInt
      canvas.drawText(t.toString,xx,yy,paint)
      y += r.bottom - r.top + (getHeight*SPACE_V).toInt
    }
  }

  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)
    // TODO: can we cache tho drawing to somewhere?
    Globals.player.foreach{player =>
      val conf = Globals.prefs.get.getString("show_yomi_info","None")
      val typeface = if(conf.startsWith("asset:")){
        try{
          Typeface.createFromAsset(context.getAssets,conf.substring(6))
        }catch{
          case _:Throwable => Typeface.DEFAULT
        }
      }else{
        Typeface.DEFAULT
      }
      val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
      val num = if(Globals.is_playing){player.next_num}else{player.cur_num}
      if(num >= 1){
        val text_array = AllFuda.list_full(num-1).split(" ")
        paint.setTypeface(typeface)
        val text_size = calculateTextSize(text_array,paint).toInt
        paint.setTextSize(text_size)
        paint.setColor(Color.WHITE)
        var startx = (getWidth/2 + ((SPACE_H*getWidth+text_size)*(text_array.length+1))/2).toInt // center of line
        for((t,m) <- text_array.zip(MARGIN_TOP)){
          val starty = (canvas.getHeight * m).toInt
          startx -= (getWidth*SPACE_H+text_size).toInt
          verticalText(paint,canvas,startx,starty,t)
        }
      }
    }
  }
}
