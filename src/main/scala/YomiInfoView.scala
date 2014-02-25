package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.{View,MotionEvent,GestureDetector}
import _root_.android.text.TextUtils
import _root_.android.widget.HorizontalScrollView
import _root_.android.graphics.{Canvas,Typeface,Paint,Color,Rect}
import _root_.android.util.AttributeSet
import _root_.android.support.v4.view.GestureDetectorCompat
import _root_.android.os.CountDownTimer


class YomiInfoLayout(context:Context, attrs:AttributeSet) extends HorizontalScrollView(context, attrs){
  val SCROLL_THREASHOLD = 0.3
  val SCROLL_SPEED = 200 // in millisec
  var cur_view = None:Option[Int]
  def scrollAnimation(endx:Int,on_finish:Unit=>Unit=Unit=>Unit){
    val startx = getScrollX
    new CountDownTimer(SCROLL_SPEED,10){
      override def onTick(millisUntilFinished:Long){
        val r = millisUntilFinished / SCROLL_SPEED.toFloat
        val pos = (startx * r + endx * (1-r)).toInt
        smoothScrollTo(pos,0)
      }
      override def onFinish(){
        smoothScrollTo(endx,0)
        // There seems no simple way to run a hook after smoothScrollTo() is ended.
        // Therefore we run on_finish() after specific time.
        postDelayed(new Runnable(){
            override def run(){
              on_finish()
            }},30)
      }
    }.start
  }
  override def onTouchEvent(ev:MotionEvent):Boolean = {
    super.onTouchEvent(ev)
    ev.getAction match{
      case MotionEvent.ACTION_UP =>
        cur_view.foreach{ vid=>
          val v = findViewById(vid)
          if(v != null){
            val dx = getScrollX-v.getLeft
            val nvid = if(Math.abs(dx) > v.getWidth * SCROLL_THREASHOLD){
              if(dx > 0){
                vid match{
                  case R.id.yomi_info_view_next => R.id.yomi_info_view_cur
                  case _ => R.id.yomi_info_view_prev
                }
              }else{
                vid match{
                  case R.id.yomi_info_view_prev => R.id.yomi_info_view_cur
                  case _ => R.id.yomi_info_view_next
                }
              }
            }else{
              vid
            }
            scrollToView(nvid,true,true)
          }
        }
      case _ => Unit
    }
    true
  }
  override def onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
    super.onSizeChanged(w,h,oldw,oldh)
    post(new Runnable(){
      override def run(){
        for(i <- Array(R.id.yomi_info_view_next,R.id.yomi_info_view_cur,R.id.yomi_info_view_prev)){
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
  def invalidateAndScroll(){
    for(i <- Array(R.id.yomi_info_view_next,R.id.yomi_info_view_cur,R.id.yomi_info_view_prev)){
      val v = findViewById(i).asInstanceOf[YomiInfoView]
      if(v!=null){
        v.updateCurNum
        v.invalidate
      }
    }
    scrollToView(R.id.yomi_info_view_cur,false)
  }

  def scrollToView(id:Int,smooth:Boolean,from_touch_event:Boolean=false){
    val v = findViewById(id).asInstanceOf[YomiInfoView]
    if(v!=null){
      val x = v.getLeft
      val have_to_move = from_touch_event && Array(R.id.yomi_info_view_prev,R.id.yomi_info_view_next).contains(id)
      if(smooth){
        scrollAnimation(x,
          _=>
            if(have_to_move){
              val wa = context.asInstanceOf[WasuramotiActivity]
              wa.cancelAllPlay()
              if(id == R.id.yomi_info_view_prev){
                FudaListHelper.movePrev(context)
              }else{
                FudaListHelper.moveNext(context)
              }
              wa.refreshAndSetButton()
              wa.invalidateYomiInfo()
            }
        )
      }else{
        scrollTo(x,0)
      }
      cur_view = Some(id)
    }
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
  val MARGIN_LEFT_FURIGANA_RATIO = 0.4 // must be between 0.0 and 1.0
  val FURIGANA_TOP_LIMIT = 0.02
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
  var cur_num = None:Option[Int]

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
  def verticalText(canvas:Canvas,startx:Int,starty:Int,text:String,actual_width:Int,margin_left_furigana:Int){
    val text_s = AllFuda.parseFurigana(text)
    var y = starty
    var prev_furigana_bottom = (getHeight*FURIGANA_TOP_LIMIT).toInt
    for((t,furigana) <- text_s){
      val (y_,prev_furigana_bottom_) = verticalWord(canvas,startx,y,t,furigana,actual_width,margin_left_furigana,prev_furigana_bottom)
      // TODO: the following is so ugly
      y = y_
      prev_furigana_bottom = prev_furigana_bottom_
    }
  }
  def verticalWord(canvas:Canvas,startx:Int,starty:Int,text:String,furigana:String,actual_width:Int,margin_left_furigana:Int,prev_furigana_bottom:Int):(Int,Int) = {
    val (y,width,height) = drawVertical(paint,canvas,startx,starty,text,(getHeight*SPACE_V).toInt)
    var new_furigana_bottom = prev_furigana_bottom
    if(show_furigana && !TextUtils.isEmpty(furigana)){
      val span_v = (getHeight*SPACE_V_FURIGANA).toInt
      val (_,this_height_wo) = calcVerticalBound(furigana,paint_furigana)
      val this_height = this_height_wo + (furigana.length-1)*span_v
      val sy = Math.max(prev_furigana_bottom,starty+height/2-this_height.toInt/2)
      val sx = (startx+margin_left_furigana).toInt
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

  def updateCurNum(){
    val fn = getId match {
      case R.id.yomi_info_view_prev => -1
      case R.id.yomi_info_view_next => 1
      case _ => 0
    }
    cur_num = FudaListHelper.getOrQueryFudaNumToRead(context,fn)
  }

  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)

    if(android.os.Build.VERSION.SDK_INT >= 11 && canvas.isHardwareAccelerated){
      // The default background has gradation when hardware acceleration is turned on
      // Therefore we have to fill it with black
      canvas.drawColor(Color.BLACK)
    }

    cur_num.foreach{num =>
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
      var margin_left_furigana = 0.0
      if(show_furigana){
        var furisize = (getWidth/text_array_with_margin.length)/4 // this must be close to result text size
        paint_furigana.setTextSize(furisize)
        val only_furigana = text_array_with_margin.map{case(t,m)=>AllFuda.onlyInsideParens(t)}
        val actual_width_furigana = measureActualTextWidth(only_furigana,paint_furigana)
        val actual_ratio_furigana = furisize / actual_width_furigana.toFloat
        paint_furigana.setTextSize(span_h.toFloat*actual_ratio_furigana*FURIGANA_RATIO.toFloat)
        margin_left_furigana = actual_width/2.0+actual_width_furigana/2.0 + (Math.max(0.0, span_h-actual_width_furigana))*MARGIN_LEFT_FURIGANA_RATIO
      }
      for((t,m) <- text_array_with_margin){
        val starty = (getHeight * m).toInt
        verticalText(canvas,startx,starty,t,actual_width,margin_left_furigana.toInt)
        startx -= rowspan
      }
    }

  }
}
