package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.View
import _root_.android.view.View.MeasureSpec
import _root_.android.widget.{FrameLayout,HorizontalScrollView}
import _root_.android.graphics.{Canvas,Typeface,Paint,Color,Rect}
import _root_.android.util.AttributeSet

class YomiInfoLayout(context:Context, attrs:AttributeSet) extends HorizontalScrollView(context, attrs){
  def invalidateAndScroll(){
    for(i <- Array(R.id.yomi_info_view_next,R.id.yomi_info_view_cur)){
      val v = findViewById(i)
      if(v!=null){v.invalidate}
    }
    setSmoothScrollingEnabled(false)
    fullScroll(View.FOCUS_RIGHT)
  }

  def scrollToNext(){
    setSmoothScrollingEnabled(true)
    fullScroll(View.FOCUS_LEFT)
  }

}

class YomiInfoView(context:Context, attrs:AttributeSet) extends View(context, attrs) {
  val MARGIN_TOP = Array(0.03,0.06,0.09,0.045,0.075) // from left to right
  val MARGIN_BOTTOM = 0.05
  val MARGIN_LR = 0.1
  val SPACE_H = 0.03
  val SPACE_V = 0.02
  // According to http://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  // However, our calculateTextSize() is quite inaccurate when paint's text size is not a default value,
  // we save the default text size here.
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Color.WHITE)
  val DEFAULT_TEXT_SIZE = paint.getTextSize

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

  override def onMeasure(widthMS:Int,heightMS:Int){
    val par = getParent.getParent.asInstanceOf[View]
    val desiredWidth = par.getWidth
    val desiredHeight = par.getHeight
    val specifiedWidth = MeasureSpec.getSize(widthMS)
    val specifiedHeight = MeasureSpec.getSize(heightMS)
    // As for height, we obey parents specification
    val pixelHeight = MeasureSpec.getMode(heightMS) match {
      case MeasureSpec.EXACTLY => specifiedHeight
      case MeasureSpec.AT_MOST => Math.min(specifiedHeight,desiredHeight)
      case _ => desiredHeight
    }
    // As for width, we force our desired width
    val pixelWidth = desiredWidth

    val desiredWSpec = View.MeasureSpec.makeMeasureSpec(pixelWidth, View.MeasureSpec.EXACTLY)
    val desiredHSpec = View.MeasureSpec.makeMeasureSpec(pixelHeight, View.MeasureSpec.EXACTLY)
    setMeasuredDimension(desiredWSpec,desiredHSpec)
  }

  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)
    
    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB && canvas.isHardwareAccelerated){
      // The default background has gradation when hardware acceleration is turned on
      // Therefore we have to fill it with black
      canvas.drawColor(Color.BLACK)
    }

    val num = if(Globals.is_playing){
      if(getId == R.id.yomi_info_view_cur){
        Globals.play_log.applyOrElse(1,(_:Int)=> -1)
      }else{
        Globals.play_log.applyOrElse(0,(_:Int)=> -1)
      }
    }else{
      if(getId == R.id.yomi_info_view_cur){
        Globals.play_log.applyOrElse(2,(_:Int)=> -1)
      }else{
        Globals.play_log.applyOrElse(1,(_:Int)=> -1)
      }
    }
    if(num >= 0){
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
      val list_with_joka:Array[String] = AllFuda.list_full.+:(AllFuda.joka)
      val text_array = list_with_joka(num).split(" ")
      paint.setTypeface(typeface)
      paint.setTextSize(DEFAULT_TEXT_SIZE)
      val text_size = calculateTextSize(text_array,paint).toInt
      paint.setTextSize(text_size)
      var startx = (getWidth/2 + ((SPACE_H*getWidth+text_size)*(text_array.length+1))/2).toInt // center of line
      for((t,m) <- text_array.zip(MARGIN_TOP)){
        val starty = (getHeight * m).toInt
        startx -= (getWidth*SPACE_H+text_size).toInt
        verticalText(paint,canvas,startx,starty,t)
      }
    }
  
  }
}
