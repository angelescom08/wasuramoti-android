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
  val MARGIN_AUTHOR = Array(0.09,0.13)
  val MARGIN_BOTTOM = 0.05
  val MARGIN_LR = 0.08

  val SPACE_V = 0.02
  val SPACE_H = 0.05
  val SPACE_V_FURIGANA = 0.01
  val MARGIN_LEFT_FURIGANA = 0.007
  val FURIGANA_TOP_LIMIT = 0.01
  val FURIGANA_RATIO = 0.82

  // According to http://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  // However, our calculateTextSize() is quite inaccurate when paint's text size is not a default value,
  // we save the default text size here.
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Color.WHITE)
  val paint_furigana = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint_furigana.setColor(Color.rgb(199,239,251))

  var show_furigana = true
  var show_author = true
  var cur_num = -1
  var hide = false

  // This does not include span height
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
  
  def measureActualTextWidth(text_array:Array[String],paint:Paint):Int = {
    text_array.map{x=>val (w,h)=calcVerticalBound(x,paint);w}.max.toInt
  }

  // Typeface of paint must be set before calling this function
  // Also text size must be default before calling this function ( I don't know why, maybe it's a bug ? )
  def calculateTextSize(text_array_with_margin:Array[(String,Double)],paint:Paint,space_h:Double):Int ={
    // we estimate the result text size, the closer it is, the result will be more accurate
    val estimate_size = getWidth / text_array_with_margin.length
    paint.setTextSize(estimate_size)
    val width = getWidth.toFloat
    val height = getHeight.toFloat
    val bounds = (for((t,m)<-text_array_with_margin)yield(calcVerticalBound(t,paint)))
    val r1 = (for(((w,h),(t,m)) <- bounds.zip(text_array_with_margin)) yield (((1-m-MARGIN_BOTTOM-SPACE_V*t.length)*height/h))).min
    val r2 = (1-MARGIN_LR*2-text_array_with_margin.length*space_h)*width/bounds.map{case(w,h)=>w}.sum
    (Math.min(r1,r2)*paint.getTextSize).toInt
  }
  def verticalText(canvas:Canvas,startx:Int,starty:Int,text:String,actual_width:Int,actual_width_furigana:Int){
    val text_s = AllFuda.parseFurigana(text)
    var y = starty
    var prev_furigana_bottom = (getHeight*FURIGANA_TOP_LIMIT).toInt
    for((t,furigana) <- text_s){
      val (y_,prev_furigana_bottom_) = verticalWord(canvas,startx,y,t,furigana,actual_width,actual_width_furigana,prev_furigana_bottom)
      // TODO: the following is so ugly
      y = y_
      prev_furigana_bottom = prev_furigana_bottom_
    }
  }
  def verticalWord(canvas:Canvas,startx:Int,starty:Int,text:String,furigana:String,actual_width:Int,actual_width_furigana:Int,prev_furigana_bottom:Int):(Int,Int) = {
    val (y,width,height) = drawVertical(paint,canvas,startx,starty,text,(getHeight*SPACE_V).toInt)
    var new_furigana_bottom = prev_furigana_bottom
    if(show_furigana && !TextUtils.isEmpty(furigana)){
      val span_v = (getHeight*SPACE_V_FURIGANA).toInt
      val (_,this_height_wo) = calcVerticalBound(furigana,paint_furigana) 
      val this_height = this_height_wo + (furigana.length-1)*span_v
      val sy = Math.max(prev_furigana_bottom,starty+height/2-this_height.toInt/2)
      val sx = (startx+actual_width/2.0+actual_width_furigana/2.0 + getWidth*MARGIN_LEFT_FURIGANA).toInt
      val(furigana_y,_,_) = drawVertical(paint_furigana,canvas,sx,sy,furigana,span_v)
      new_furigana_bottom = furigana_y
    }
    (y,new_furigana_bottom)
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
      if(Globals.IS_DEBUG){
        val paint_debug = new Paint()
        paint_debug.setStyle(Paint.Style.STROKE)
        paint_debug.setColor(Color.RED)
        paint_debug.setStrokeWidth(3)
        canvas.drawRect(startx,starty,startx+1,starty+1,paint_debug)
      }
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
      val space_boost = if(show_furigana){
        Globals.prefs.get.getString("yomi_info_furigana_size","SMALL") match {
          case "SMALL" => 1.0
          case "MEDIUM" => 1.14
          case "LARGE" => 1.3
          case _ => 1.0
        }
      }else{
        1.0
      }
      val space_h = SPACE_H * space_boost

      val text_array_with_margin = (if(show_author){AllFuda.author(num).split(" ").zip(MARGIN_AUTHOR)}else{Array()}) ++ 
        AllFuda.list_full(num).split(" ").zip(MARGIN_TOP)
      val no_furigana = text_array_with_margin.map{case(t,m)=>(AllFuda.removeInsideParens(t),m)}
      var text_size = calculateTextSize(no_furigana,paint,space_h)
      paint.setTextSize(text_size)
      val actual_width = measureActualTextWidth(no_furigana.map{case(t,m)=>t},paint)
      val span_h = space_h*getWidth
      var rowspan = (span_h+actual_width).toInt
      var startx = (getWidth/2 + (rowspan*(text_array_with_margin.length-1))/2).toInt
      var actual_width_furigana = 0.0
      if(show_furigana){
        var furisize = (getWidth/text_array_with_margin.length)/4 // this must be close to result text size
        paint_furigana.setTextSize(furisize)
        val only_furigana = text_array_with_margin.map{case(t,m)=>AllFuda.onlyInsideParens(t)}
        actual_width_furigana = measureActualTextWidth(only_furigana,paint_furigana)
        val actual_ratio_furigana = furisize / actual_width_furigana.toFloat
        paint_furigana.setTextSize(span_h.toFloat*actual_ratio_furigana*FURIGANA_RATIO.toFloat)
      }
      for((t,m) <- text_array_with_margin){
        val starty = (getHeight * m).toInt
        verticalText(canvas,startx,starty,t,actual_width,actual_width_furigana.toInt)
        startx -= rowspan
      }
    }
  
  }
}
