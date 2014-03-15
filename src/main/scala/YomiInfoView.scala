package karuta.hpnpwd.wasuramoti
import _root_.android.content.{Context,DialogInterface,Intent}
import _root_.android.view.{View,MotionEvent,ViewTreeObserver}
import _root_.android.text.TextUtils
import _root_.android.widget.HorizontalScrollView
import _root_.android.graphics.{Canvas,Typeface,Paint,Color,Rect}
import _root_.android.util.AttributeSet
import _root_.android.os.{CountDownTimer,Bundle}
import _root_.android.net.Uri
import _root_.android.app.{AlertDialog,SearchManager,Dialog}
import _root_.android.support.v4.app.DialogFragment

import scala.collection.mutable

object TypefaceManager{
  val cache = new mutable.HashMap[String,Typeface]()
  def get(context:Context,conf:String):Typeface = {
    if(conf.startsWith("asset:")){
      cache.getOrElse(conf,
        try{
          val t = Typeface.createFromAsset(context.getAssets,"font/"+conf.substring(6))
          cache.put(conf,t)
          t
        }catch{
          case _:Throwable => Typeface.DEFAULT
        })
    }else{
      Typeface.DEFAULT
    }
  }
}

class YomiInfoLayout(context:Context, attrs:AttributeSet) extends HorizontalScrollView(context, attrs){
  val SCROLL_THREASHOLD = 0.25
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
    if(w == 0){
      return
    }
    val that = this
    // as for android 2.1, we have to execute the following in post(...) method
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

          val vto = that.getViewTreeObserver
          vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){
              override def onGlobalLayout(){
                that.getViewTreeObserver.removeGlobalOnLayoutListener(this)
                val vid = Globals.player.flatMap{_.current_yomi_info}.getOrElse(R.id.yomi_info_view_cur)
                that.scrollToView(vid,false)
              }
          })

          requestLayout()
      }
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
              v.cur_num.foreach{ cn =>
                FudaListHelper.queryIndexFromFudaNum(context,cn).foreach{index =>
                  if(Utils.readCurNext(context)){
                    FudaListHelper.putCurrentIndex(context,index)
                  }else{
                    FudaListHelper.queryPrevOrNext(context,index,false).foreach{ x=>
                      FudaListHelper.putCurrentIndex(context,x._4)
                    }
                  }
                }
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
  var screen_range:Rect = null
  if(Globals.IS_DEBUG){
    screen_range = new Rect(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE)
  }

  val MARGIN_TOP = Array(0.04,0.08,0.12,0.06,0.10) // from right to left, rate of view height
  val MARGIN_AUTHOR = Array(0.09,0.13) // rate of view height
  val MARGIN_BOTTOM = 0.06 // rate of view height
  val MARGIN_LR = 0.06 // rate of view width

  val SPACE_V = 0.15 // rate of text size
  val SPACE_H = 0.05 // rate of view width
  val SPACE_V_FURIGANA = 0.15 // rate of text size
  val MARGIN_LEFT_FURIGANA_RATIO = 0.4 // must be between 0.0 and 1.0
  val FURIGANA_TOP_LIMIT = 0.02 // rate of view height
  val FURIGANA_RATIO_DEFAULT = 0.70 // rate of span_h
  val FURIGANA_MARGIN_LEFT_MIN = 2 // in pixels

  // According to http://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Color.WHITE)
  val paint_furigana = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint_furigana.setColor(Color.rgb(199,239,251))

  var show_furigana = true
  var show_author = true
  var cur_num = None:Option[Int]

  // This does not include span height
  def calcVerticalBound(str:String,paint:Paint):(Int,Int) = {
    var w = 0
    var h = 0
    for(s <- str){
      val r = new Rect()
      paint.getTextBounds(s.toString,0,1,r)
      w = Math.max(w, r.right - r.left + 1)
      h += (r.bottom - r.top + 1)
    }
    (w,h)
  }

  def measureActualTextWidthAve(text_array:Array[String],paint:Paint):Int = {
    val ar = text_array.map{x=>val (w,h)=calcVerticalBound(x,paint);w}
    ar.sum / ar.length
  }
  def measureActualTextWidthMax(text_array:Array[String],paint:Paint):Int = {
    text_array.map{x=>val (w,h)=calcVerticalBound(x,paint);w}.max.toInt
  }

  // Typeface of paint must be set before calling this function
  // Also text size must be default before calling this function ( I don't know why, maybe it's a bug ? )
  def calculateTextSize(text_array_with_margin:Array[(String,Double)],paint:Paint,space_h:Double):Int ={
    // we estimate the result text size, the closer it is, the result will be more accurate
    val estimate_size = getWidth / text_array_with_margin.length
    paint.setTextSize(estimate_size)
    val bounds = (for((t,m)<-text_array_with_margin)yield(calcVerticalBound(t,paint)))
    val r1 = (for(((w,h),(t,m)) <- bounds.zip(text_array_with_margin))yield{
      val ya = (1-m-MARGIN_BOTTOM)*getHeight.toFloat
      val yb = h + (t.length-1) * SPACE_V * estimate_size;
      ya / yb
    }).min
    val xa = (1-MARGIN_LR*2-text_array_with_margin.length*space_h)*getWidth.toFloat
    val xb = bounds.map{case(w,h)=>w}.sum
    val r2 = xa / xb
    (Math.min(r1,r2)*estimate_size).toInt
  }


  def verticalText(canvas:Canvas,startx:Int,starty:Int,text:String,margin_left_furigana:Int){
    val text_s = AllFuda.parseFurigana(text)
    var y = starty
    var prev_furigana_bottom = (getHeight*FURIGANA_TOP_LIMIT).toInt
    for((t,furigana) <- text_s){
      val (y_,prev_furigana_bottom_) = verticalWord(canvas,startx,y,t,furigana,margin_left_furigana,prev_furigana_bottom)
      // TODO: the following is so ugly
      y = y_
      prev_furigana_bottom = prev_furigana_bottom_
    }
  }
  def verticalWord(canvas:Canvas,startx:Int,starty:Int,text:String,furigana:String,margin_left_furigana:Int,prev_furigana_bottom:Int):(Int,Int) = {
    val (y,width,height) = drawVertical(paint,canvas,startx,starty,text,(paint.getTextSize*SPACE_V).toInt)
    var new_furigana_bottom = prev_furigana_bottom
    if(show_furigana && !TextUtils.isEmpty(furigana)){
      val span_v = (paint_furigana.getTextSize*SPACE_V_FURIGANA).toInt
      val (_,this_height_wo) = calcVerticalBound(furigana,paint_furigana)
      val this_height = this_height_wo + (furigana.length-1)*span_v
      val sy = Math.max(prev_furigana_bottom,starty+height/2-this_height.toInt/2)
      val sx = (startx+Math.max(margin_left_furigana,width/2+paint_furigana.getTextSize/2+FURIGANA_MARGIN_LEFT_MIN)).toInt
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
        val r_l = startx-(r.right-r.left)/2
        val r_t = yy+r.top
        val r_r = startx+(r.right-r.left)/2
        val r_b = yy+r.bottom
        canvas.drawRect(r_l,r_t,r_r,r_b,paint_debug)
        screen_range.left = Math.min(r_l,screen_range.left)
        screen_range.top = Math.min(r_t,screen_range.top)
        screen_range.right = Math.max(r_r,screen_range.right)
        screen_range.bottom = Math.max(r_b,screen_range.bottom)
      }
    }
    (y,width,y-starty-span)
  }

  def updateCurNum(){
    var fn = getId match {
      case R.id.yomi_info_view_prev => -1
      case R.id.yomi_info_view_next => 1
      case _ => 0
    }
    if(!Utils.readCurNext(context)){
      fn += 1
    }
    cur_num = if(Utils.isRandom && (Array(2,-1).contains(fn) || !Utils.readCurNext(context) && fn == 0)){
      None
    }else{
      FudaListHelper.getOrQueryFudaNumToRead(context,fn)
    }
  }

  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)

    if(android.os.Build.VERSION.SDK_INT >= 11 && canvas.isHardwareAccelerated){
      // The default background has gradation when hardware acceleration is turned on
      // Therefore we have to fill it with black
      canvas.drawColor(Color.BLACK)
    }
    cur_num.foreach{num =>
      if(Globals.IS_DEBUG){
        val paint_debug = new Paint()
        paint_debug.setStyle(Paint.Style.STROKE)
        paint_debug.setColor(Color.GREEN)
        paint_debug.setStrokeWidth(3)
        canvas.drawRect((getWidth*MARGIN_LR).toInt,(getHeight*FURIGANA_TOP_LIMIT).toInt,
          (getWidth*(1.0-MARGIN_LR)).toInt,(getHeight*(1-MARGIN_BOTTOM)).toInt,paint_debug)
      }
      show_author = Globals.prefs.get.getBoolean("yomi_info_author",false)
      show_furigana = Globals.prefs.get.getString("yomi_info_furigana","None") != "None"
      paint.setTypeface(TypefaceManager.get(context,Globals.prefs.get.getString("show_yomi_info","None")))
      paint_furigana.setTypeface(TypefaceManager.get(context,Globals.prefs.get.getString("yomi_info_furigana","None")))
      val furigana_width_conf_default = context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)
      val furigana_width_conf_max = context.getResources.getInteger(R.integer.yomi_info_furigana_width_max)
      val furigana_width_conf_cur = Globals.prefs.get.getInt("yomi_info_furigana_width",furigana_width_conf_default)

      val text_array_with_margin = (if(show_author){AllFuda.author(num).split(" ").zip(MARGIN_AUTHOR)}else{Array()}) ++
        AllFuda.list_full(num).split(" ").zip(MARGIN_TOP)

      val space_boost1 = if(show_furigana){
        1.3
      }else{
        1.0
      }
      val space_boost2 = if(show_furigana && furigana_width_conf_cur > furigana_width_conf_default){
        1.0 + 0.4*(furigana_width_conf_cur - furigana_width_conf_default)/(furigana_width_conf_max - furigana_width_conf_default).toDouble
      }else{
        1.0
      }

      val space_boost3 = text_array_with_margin.length match {
        case 5 => 1.0
        case 6 => 0.9
        case 7 => 0.8
        case _ => 1.0 // does not happen
      }

      val furigana_ratio = if(show_furigana && furigana_width_conf_cur < furigana_width_conf_default){
        1.0 - 0.4*(1.0 - (furigana_width_conf_cur/furigana_width_conf_default.toDouble))
      }else{
        1.0
      }
      val space_h = SPACE_H * space_boost1 * space_boost2 * space_boost3

      val no_furigana = text_array_with_margin.map{case(t,m)=>(AllFuda.removeInsideParens(t),m)}
      var text_size = calculateTextSize(no_furigana,paint,space_h)
      paint.setTextSize(text_size)
      val actual_width_ave = measureActualTextWidthAve(no_furigana.map{case(t,m)=>t},paint)
      val actual_width_max = measureActualTextWidthMax(no_furigana.map{case(t,m)=>t},paint)
      val span_h = space_h*getWidth
      var rowspan = (span_h+actual_width_ave).toInt
      var startx = (getWidth/2 + (rowspan*(text_array_with_margin.length-1))/2).toInt
      var margin_left_furigana = 0.0
      if(show_furigana){
        var furisize = (getWidth/text_array_with_margin.length)/4 // this must be close to result text size
        paint_furigana.setTextSize(furisize)
        val only_furigana = text_array_with_margin.map{case(t,m)=>AllFuda.onlyInsideParens(t)}
        val actual_width_furigana = measureActualTextWidthMax(only_furigana,paint_furigana)
        val actual_ratio_furigana = furisize / actual_width_furigana.toFloat
        paint_furigana.setTextSize(span_h.toFloat*actual_ratio_furigana*furigana_ratio.toFloat*FURIGANA_RATIO_DEFAULT.toFloat)
        margin_left_furigana = actual_width_ave/2.0+actual_width_furigana/2.0 + (Math.max(0.0, span_h-actual_width_furigana))*MARGIN_LEFT_FURIGANA_RATIO
        startx -= (span_h / 2.0).toInt
      }
      for((t,m) <- text_array_with_margin){
        val starty = (getHeight * m).toInt
        verticalText(canvas,startx,starty,t,margin_left_furigana.toInt)
        startx -= rowspan
      }
      if(Globals.IS_DEBUG){
        val margin_l = screen_range.left / getWidth.toDouble
        val margin_t = screen_range.top / getHeight.toDouble
        val margin_r = (getWidth - screen_range.right) / getWidth.toDouble
        val margin_b = (getHeight - screen_range.bottom) / getHeight.toDouble
        println(String.format("wasuramoti_debug: screen_range l=%.3f t=%.3f r=%.3f b=%.3f",
          new java.lang.Double(margin_l),
          new java.lang.Double(margin_t),
          new java.lang.Double(margin_r),
          new java.lang.Double(margin_b)))
        if(margin_l <= MARGIN_LR*0.7){
          println("wasuramoti_debug: [WARN] cur_num=" + cur_num + ", margin_l is too small: " + margin_l)
        }
        if(margin_r <= MARGIN_LR*0.7){
          println("wasuramoti_debug: [WARN] cur_num=" + cur_num + ", margin_r is too small: " + margin_r)
        }
        if(margin_t <= FURIGANA_TOP_LIMIT*0.7){
          println("wasuramoti_debug: [WARN] cur_num=" + cur_num + ", margin_t is too small: " + margin_t)
        }
        if(margin_b <= MARGIN_BOTTOM*0.7){
          println("wasuramoti_debug: [WARN] cur_num=" + cur_num + ", margin_b is too small: " + margin_b)
        }
      }
    }
  }
}

// The constructor of Fragment must be empty since when fragment is recreated,
// The empty constructor is called.
// Therefore we have to create instance through this function.
object YomiInfoSearchDialogBuilder{
  def newInstance(fudanum:Int):YomiInfoSearchDialog = {
    val fragment = new YomiInfoSearchDialog()
    val args = new Bundle()
    args.putInt("fudanum",fudanum)
    fragment.setArguments(args)
    return fragment
  }
}

class YomiInfoSearchDialog extends DialogFragment{
  override def onCreateDialog(saved:Bundle):Dialog = {
    val fudanum = getArguments.getInt("fudanum",0)
    val builder = new AlertDialog.Builder(getActivity)
    builder.setTitle(R.string.yomi_info_search_title)
    .setItems(R.array.yomi_info_search_array, new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          val query = if(which == 0){
            AllFuda.removeInsideParens(AllFuda.list_full(fudanum))
          }else{
            AllFuda.removeInsideParens(AllFuda.author(fudanum)).replace(" ","") + " 歌人"
          }
          val f1 = {_:Unit =>
            val intent = new Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY,query)
            Left(intent)
          }
          val f2 = {_:Unit => 
            val intent = new Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse("http://www.google.com/search?q="+Uri.encode(query)))
            Left(intent)
          }
          val f3 = {_:Unit => 
            Right({ _:Unit =>
              Utils.messageDialog(getActivity,Left("Application for Web search not found on this device."))
            })
          }
          // scala.util.control.Breaks.break does not work (why?)
          // Therefore we use `exists` in Traversable trait instead
          Seq(f1,f2,f3) exists {f=>
              f() match {
                case Left(intent) =>
                  try{
                    startActivity(intent)
                    true
                  }catch{
                    case _:android.content.ActivityNotFoundException => false
                  }
                case Right(g) => {g();true}
              }
            }
        }
      }
    )
    .create()
  }
}
// このファイルはutf-8で日本語を含んでいます
