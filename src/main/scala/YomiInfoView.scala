package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.View
import _root_.android.widget.HorizontalScrollView
import _root_.android.graphics.{Canvas,Typeface,Paint,Color,Rect}
import _root_.android.util.AttributeSet

class YomiInfoLayout(context:Context, attrs:AttributeSet) extends HorizontalScrollView(context, attrs){
  override def onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
    super.onSizeChanged(w,h,oldw,oldh)
    post(new Runnable(){
      override def run(){
        for(i <- Array(R.id.yomi_info_view_next,R.id.yomi_info_view_cur)){
          val v = findViewById(i)
          if(v!=null){
            val prop = v.getLayoutParams
            prop.width = w
            v.setLayoutParams(prop)
          }
        }
      }
      requestLayout()
    })
  }
  def invalidateAndScroll(scroll:Option[Int]=Some(View.FOCUS_RIGHT),have_to_hide:Boolean){
    for(i <- Array(R.id.yomi_info_view_next,R.id.yomi_info_view_cur)){
      val v = findViewById(i).asInstanceOf[YomiInfoView]
      if(v!=null){
        v.hide = (have_to_hide && i == R.id.yomi_info_view_next)
        v.invalidate
      }
    }
    scroll.foreach{ x =>
      setSmoothScrollingEnabled(false)
      fullScroll(x)
    }
  }

  def scrollToNext(){
    val v = findViewById(R.id.yomi_info_view_next).asInstanceOf[YomiInfoView]
    if(v != null && v.hide){
      v.hide = false
      v.invalidate
    }
    setSmoothScrollingEnabled(Utils.readCurNext)
    fullScroll(View.FOCUS_LEFT)
  }
}

class YomiInfoView(context:Context, attrs:AttributeSet) extends View(context, attrs) {
  val MARGIN_TOP = Array(0.04,0.08,0.12,0.06,0.10) // from left to right
  val MARGIN_BOTTOM = 0.05
  val MARGIN_LR = 0.08
  val SPACE_H = 0.03
  val SPACE_V = 0.02
  // According to http://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  // However, our calculateTextSize() is quite inaccurate when paint's text size is not a default value,
  // we save the default text size here.
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Color.WHITE)
  val DEFAULT_TEXT_SIZE = paint.getTextSize
  var cur_num = -1
  var hide = false

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
  // Also text size must be default before calling this function ( I don't know why, maybe it's a bug ? )
  def calculateTextSize(text_array:Array[String],paint:Paint):Int ={
    val width = getWidth
    val height = getHeight
    val ar = text_array.map{AllFuda.removeInsideParens(_)}
    val bounds = (for(t<-ar)yield(calcVerticalBound(t,paint)))
    val r1 = (for((((w,h),m),t) <- bounds.zip(MARGIN_TOP).zip(ar)) yield (((1-m-MARGIN_BOTTOM-SPACE_V*t.length)*height/h))).min
    val r2 = (1-MARGIN_LR*2-ar.length*SPACE_H)*width/bounds.map{case(w,h)=>w}.sum
    (Math.min(r1,r2)*paint.getTextSize).toInt
  }
  def verticalText(paint:Paint,canvas:Canvas,startx:Int,starty:Int,text:String){

    // Temporary we just remove furigana
    // TODO: Implement it
    val text_s = AllFuda.removeInsideParens(text)

    var y = starty
    for(t <- text_s){
      val r = new Rect()
      paint.getTextBounds(t.toString,0,1,r)
      paint.setTextAlign(Paint.Align.CENTER)
      val yy = (y - r.top).toInt
      canvas.drawText(t.toString,startx,yy,paint)
      y += r.bottom - r.top + (getHeight*SPACE_V).toInt
    }
  }

  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)
    
    if(android.os.Build.VERSION.SDK_INT >= 11 && canvas.isHardwareAccelerated){
      // The default background has gradation when hardware acceleration is turned on
      // Therefore we have to fill it with black
      canvas.drawColor(Color.BLACK)
    }
    if(hide){
      return
    }

    val num = Globals.play_log.applyOrElse(
      (Globals.is_playing,getId==R.id.yomi_info_view_cur) match{
        case (true,true) => 1
        case (true,false) => 0
        case (false,true) => 2
        case (false,false) => 1
      },(_:Int)=> -1)
    cur_num = num
    if(num >= 0){
      val conf = Globals.prefs.get.getString("show_yomi_info","None")
      val typeface = if(conf.startsWith("asset:")){
        try{
          Typeface.createFromAsset(context.getAssets,"font/"+conf.substring(6))
        }catch{
          case _:Throwable => Typeface.DEFAULT
        }
      }else{
        Typeface.DEFAULT
      }
      val text_array = AllFuda.list_full(num).split(" ")
      paint.setTypeface(typeface)
      paint.setTextSize(DEFAULT_TEXT_SIZE)
      val text_size = calculateTextSize(text_array,paint).toInt
      paint.setTextSize(text_size)
      val span_h = SPACE_H*getWidth
      var startx = (getWidth/2 + ((span_h+text_size)*(text_array.length+1))/2).toInt
      for((t,m) <- text_array.zip(MARGIN_TOP)){
        val starty = (getHeight * m).toInt
        startx -= (span_h+text_size).toInt
        verticalText(paint,canvas,startx,starty,t)
      }
    }
  
  }
}
