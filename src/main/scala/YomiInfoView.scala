package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.View
import _root_.android.text.TextUtils
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
  val MARGIN_TOP = Array(0.04,0.08,0.12,0.06,0.10) // from right to left
  val MARGIN_AUTHOR = Array(0.09,0.11)
  val MARGIN_BOTTOM = 0.05
  val MARGIN_LR = 0.08
  val SPACE_H = 0.03
  val SPACE_V = 0.02
  val SPACE_V_FURIGANA = 0.01
  // According to http://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  // However, our calculateTextSize() is quite inaccurate when paint's text size is not a default value,
  // we save the default text size here.
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Color.WHITE)
  val paint_furigana = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint_furigana.setColor(Color.WHITE)
  val DEFAULT_TEXT_SIZE = paint.getTextSize

  var show_furigana = true
  var show_author = true
  var cur_num = -1
  var hide = false

  // This does not include span height
  def calcVerticalBound(str:String,paint:Paint):(Double,Double) = {
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
  
  def measureActualTextWidth(text_array:Array[String],paint:Paint):Int = {
    text_array.map{x=>val (w,h)=calcVerticalBound(x,paint);w}.max.toInt
  }

  // Typeface of paint must be set before calling this function
  // Also text size must be default before calling this function ( I don't know why, maybe it's a bug ? )
  def calculateTextSize(text_array_with_margin:Array[(String,Double)],paint:Paint):Int ={
    val width = getWidth
    val height = getHeight
    val bounds = (for((t,m)<-text_array_with_margin)yield(calcVerticalBound(t,paint)))
    val r1 = (for(((w,h),(t,m)) <- bounds.zip(text_array_with_margin)) yield (((1-m-MARGIN_BOTTOM-SPACE_V*t.length)*height/h))).min
    val r2 = (1-MARGIN_LR*2-text_array_with_margin.length*SPACE_H)*width/bounds.map{case(w,h)=>w}.sum
    (Math.min(r1,r2)*paint.getTextSize).toInt
  }
  def verticalText(canvas:Canvas,startx:Int,starty:Int,text:String,actual_width:Int){
    val text_s = AllFuda.parseFurigana(text)
    var y = starty
    for((t,furigana) <- text_s){
      y = verticalWord(canvas,startx,y,t,furigana,actual_width)
    }
  }
  def verticalWord(canvas:Canvas,startx:Int,starty:Int,text:String,furigana:String,actual_width:Int):Int = {
    val (y,width,height) = drawVertical(paint,canvas,startx,starty,text,(getHeight*SPACE_V).toInt)
    if(show_furigana && !TextUtils.isEmpty(furigana)){
      val span_v = (getHeight*SPACE_V_FURIGANA).toInt
      val (_,this_height_wo) = calcVerticalBound(furigana,paint_furigana) 
      val this_height = this_height_wo + (furigana.length-1)*span_v
      // TODO: adjust sy so that furigana does not overwrap
      val sy = Math.max(0,starty+height/2-this_height.toInt/2)
      drawVertical(paint_furigana,canvas,startx+actual_width/2+paint_furigana.getTextSize.toInt/2,sy,furigana,span_v)
    }
    y
  }
  def drawVertical(paint:Paint,canvas:Canvas,startx:Int,starty:Int,text:String,span:Int) = {
    var y = starty
    paint.setTextAlign(Paint.Align.CENTER)
    var width = Int.MinValue
    for(t <- text){
      val r = new Rect()
      paint.getTextBounds(t.toString,0,1,r)
      width = Math.max(r.right-r.left,width)
      val yy = (y - r.top).toInt
      canvas.drawText(t.toString,startx,yy,paint)
      y += r.bottom - r.top + span 
    }
    (y,width,y-starty-span)
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
      show_author = Globals.prefs.get.getBoolean("yomi_info_author",false)
      for(key <- Array("show_yomi_info","yomi_info_furigana")){
        val conf = Globals.prefs.get.getString(key,"None")
        val typeface = if(conf.startsWith("asset:")){
          try{
            Typeface.createFromAsset(context.getAssets,"font/"+conf.substring(6))
          }catch{
            case _:Throwable => Typeface.DEFAULT
          }
        }else{
          Typeface.DEFAULT
        }
        if(key == "show_yomi_info"){
          paint.setTypeface(typeface)
        }else{
          show_furigana = (conf != "None")
          paint_furigana.setTypeface(typeface)
        }
      }
      val text_array_with_margin = (if(show_author){AllFuda.author(num).split(" ").zip(MARGIN_AUTHOR)}else{Array()}) ++ 
        AllFuda.list_full(num).split(" ").zip(MARGIN_TOP)
      val no_furigana = text_array_with_margin.map{case(t,m)=>(AllFuda.removeInsideParens(t),m)}
      paint.setTextSize(DEFAULT_TEXT_SIZE)
      val size_rate = 
      if(show_furigana){
        // if show_furigana then smaller font
        0.9
      }else{
        1.0
      }
      var orig_text_size = calculateTextSize(no_furigana,paint)
      var text_size = (orig_text_size*size_rate).toInt
      
      paint.setTextSize(text_size)
      val actual_width = measureActualTextWidth(no_furigana.map{case(t,m)=>t},paint)
      // if show_furigana then make row span litte bit wider
      val span_h = SPACE_H*getWidth + orig_text_size*(1-size_rate)
      var startx = (getWidth/2 + ((span_h+text_size)*(text_array_with_margin.length+1))/2).toInt
      if(show_furigana){
        // if show_furigana then place left a little bit
        startx -= (span_h / 2.0).toInt
      }
      var rowspan = (span_h+text_size).toInt
      val actual_ratio = text_size / actual_width
      paint_furigana.setTextSize((rowspan-actual_width)*actual_ratio)
      for((t,m) <- text_array_with_margin){
        val starty = (getHeight * m).toInt
        startx -= rowspan
        verticalText(canvas,startx,starty,t,actual_width)
      }
    }
  
  }
}
