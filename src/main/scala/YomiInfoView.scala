package karuta.hpnpwd.wasuramoti
import android.content.Context
import android.view.{View,MotionEvent}
import android.text.TextUtils
import android.graphics.{Canvas,Paint,Color,Rect,Path}
import android.util.{Log,AttributeSet}

import scala.util.hashing.MurmurHash3

class YomiInfoView(var context:Context, attrs:AttributeSet) extends View(context, attrs)
  with YomiInfoTorifudaTrait with YomiInfoYomifudaTrait with YomiInfoEnglishTrait with YomiInfoRomajiTrait{
  val RENDER_WITH_PATH_THRESHOLD = 496
  // According to https://developer.android.com/guide/topics/graphics/hardware-accel.html ,
  // `Don't create render objects in draw methods`
  val paint = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint.setColor(Utils.attrColor(context,R.attr.poemTextMainColor))
  val paint_furigana = new Paint(Paint.ANTI_ALIAS_FLAG)
  paint_furigana.setColor(Utils.attrColor(context,R.attr.poemTextFuriganaColor))
  var cur_num = None:Option[Int]
  var marker = None:Option[String]
  var torifuda_mode = false
  var info_lang = Utils.YomiInfoLang.Japanese
  var render_with_path = false

  var rendered_num = None:Option[Int] // for consistency check

  def isMemorized():Boolean = {
    cur_num.exists(FudaListHelper.isMemorized(_))
  }
  def switchMemorized(){
    cur_num.foreach{ num =>
      if(num == 0){
        // TODO: include joka to memorization mode
        CommonDialog.messageDialog(context,Right(R.string.memorization_warn_joka))
      }else{
        FudaListHelper.switchMemorized(num)
      }
    }
  }

  override def onTouchEvent(ev:MotionEvent):Boolean = {
    super.onTouchEvent(ev)
    if(getId != R.id.yomi_info_view_cur){
      return true
    }
    val LONG_CLICK_MILLISEC = 500
    val TOUCH_REGION = 0.3
    ev.getAction match {
      case MotionEvent.ACTION_UP =>
        if((ev.getEventTime-ev.getDownTime) < LONG_CLICK_MILLISEC){
          val maybe_goto_id = if(ev.getX <= getWidth * TOUCH_REGION){
            Some(R.id.yomi_info_view_next)
          }else if(ev.getX >= getWidth * (1.0-TOUCH_REGION)){
            Some(R.id.yomi_info_view_prev)
          }else{
            None
          }
          for(
            goto_id <- maybe_goto_id;
            yomi <- Utils.findAncestorViewById(this,R.id.yomi_info)
          ){
            yomi.asInstanceOf[YomiInfoLayout].scrollToView(goto_id, true, true)
          }
        }
      case _ => Unit
    }

    true
  }

  def updateCurNum(num:Option[Int] = None){
    // do all the heavy task here
    val fn = getId match {
      case R.id.yomi_info_view_prev => -1
      case R.id.yomi_info_view_next => 1
      case _ => 0
    }
    val (cur_num_temp,random_reverse) = if(num.nonEmpty){
      (num,false)
    }else if(Utils.isRandom && fn == -1){
      (None,false)
    }else{
      val tmp = FudaListHelper.getOrQueryFudaNumToRead(context,fn)
      (tmp.map{_._1},tmp.map{_._2}.getOrElse(false))
    }
    cur_num = cur_num_temp
    rendered_num = None
    render_with_path = false
    Globals.prefs.foreach{ prefs =>
      show_author = prefs.getBoolean("yomi_info_author",false)
      show_kami = prefs.getBoolean("yomi_info_kami",true)
      show_simo = prefs.getBoolean("yomi_info_simo",true)
      show_furigana = prefs.getBoolean("yomi_info_furigana_show",false)
      torifuda_mode = prefs.getBoolean("yomi_info_torifuda_mode",false)
      torifuda_reverse = checkHaveToReverse(context,random_reverse)
      info_lang = Utils.YomiInfoLang.getDefaultLangFromPref(prefs)
    }
    if(!show_author && !show_kami && !show_simo){
      updateMarker(cur_num)
    }
    initDrawing()
  }

  def checkHaveToReverse(context:Context,random_reverse:Boolean):Boolean = {
    PrefManager.getPrefStr(context,PrefKeyStr.TorifudaRotate) match {
      case "REVERSE" => true
      case "RANDOM" => random_reverse
      case _ => false
    }
  }

  def initDrawing(){
    if(torifuda_mode){
      initTorifuda()
    }else if(info_lang == Utils.YomiInfoLang.English){
      initEnglish()
    }else if(info_lang == Utils.YomiInfoLang.Romaji){
      initRomaji()
    }else{
      initYomifuda()
    }
  }
  override def onDraw(canvas:Canvas){
    super.onDraw(canvas)
    if(torifuda_mode){
      if(torifuda_reverse){
        canvas.save()
        canvas.rotate(180, canvas.getWidth/2, canvas.getHeight/2)
      }
      onDrawTorifuda(canvas)
      if(torifuda_reverse){
        canvas.restore()
      }
    }else if(info_lang == Utils.YomiInfoLang.English){
      onDrawEnglish(canvas)
    }else if(info_lang == Utils.YomiInfoLang.Romaji){
      onDrawRomaji(canvas)
    }else{
      onDrawYomifuda(canvas)
    }
    rendered_num = cur_num
  }

  // This does not include span height
  def measureBoundOneLine(str:String,paint:Paint):(Int,Int,Int) = {
    var wchar = 0
    var hchar = 0
    var hline = 0
    for(s <- str){
      val r = new Rect()
      paint.getTextBounds(s.toString,0,1,r)
      wchar = Math.max(wchar, r.right - r.left + 1)
      val h = r.bottom - r.top + 1
      hchar = Math.max(hchar,h)
      hline += h
    }
    (wchar,hchar,hline)
  }

  def measureBoundAve(text_array:Array[String],paint:Paint):(Int,Int,Int) = {
    val ar = text_array.map{measureBoundOneLine(_,paint)}
    val w_ave = ar.map{_._1}.sum / ar.length
    val h_ave = ar.map{_._2}.sum / ar.length
    val hh_ave = ar.map{_._3}.sum / ar.length
    (w_ave,h_ave,hh_ave)
  }
  def measureBoundMax(text_array:Array[String],paint:Paint):(Int,Int,Int) = {
    val ar = text_array.map{measureBoundOneLine(_,paint)}
    val w_max = ar.map{_._1}.max.toInt
    val h_max = ar.map{_._2}.max.toInt
    val hh_max = ar.map{_._3}.max.toInt
    (w_max,h_max,hh_max)
  }

  def switchRenderWithPathWhenLarge(paint:Paint,texts:Array[String]){
    // When hardware acceleration is enabled, trying to render big text causes exception:
    //  E/OpenGLRenderer(16754): Font size too large to fit in cache. width, height = ...
    // Reading libs/hwui/FontRenderer.cpp we found that this occurs when following equation holds:
    //  glyph.fHeight + TEXTURE_BORDER_SIZE * 2 > DEFAULT_TEXT_LARGE_CACHE_HEIGHT
    //    where TEXTURE_BORDER_SIZE = 1 and DEFAULT_TEXT_LARGE_CACHE_HEIGHT = 512
    // so we use getTextPath and drawPath instead of drawText when this equation holds.
    // Note: since height_char_max is not accurate, we consider the threshold a little bit smaller.
    val (_,height_char_max,_) = measureBoundMax(texts,paint)
    render_with_path = height_char_max >= RENDER_WITH_PATH_THRESHOLD
  }

  def drawTextOrPath(canvas:Canvas,paint:Paint,str:String,x:Float,y:Float){
    if(render_with_path){
      val path = new Path()
      paint.getTextPath(str,0,str.length,x,y,path)
      path.close
      canvas.drawPath(path,paint)
    }else{
      canvas.drawText(str,x,y,paint)
    }
  }
  def getTextArrayWithMargin[T](num:Int,res_id:Int,res_author:Int,delimiter:String,sub_delimiter:String,author_is_right:Boolean,
    margin_top:Array[T],margin_author:Array[T],author_prefix:String=""):Array[(String,T)] = {
      val full = AllFuda.get(context,res_id)(num).split(delimiter).zip(margin_top)
      val author = if(show_author){
        (author_prefix + AllFuda.get(context,res_author)(num)).split(delimiter).zip(margin_author)
      }else{
        new Array[(String,T)](0)
      }
      val body =
        (if(show_kami){full.take(3)}else{new Array[(String,T)](0)}) ++
        (if(show_simo){full.takeRight(2)}else{new Array[(String,T)](0)})
      val res = if(author_is_right){
        author ++ body
      }else{
        body ++ author
      }
      if(TextUtils.isEmpty(sub_delimiter)){
        res
      }else{
        res.map{case (s,t) => s.split(sub_delimiter).map{(_,t)}}.flatten
      }
  }
}

trait YomiInfoYomifudaTrait{
  self:YomiInfoView =>
  var screen_range_main:Rect = null
  var screen_range_furi:Rect = null

  val MARGIN_TOP_BASE = Array(0.04,0.08,0.12,0.06,0.10) // from right to left, rate of view height
  val MARGIN_AUTHOR_BASE = Array(0.09,0.13) // rate of view height
  val MARGIN_BOTTOM_BASE = 0.06 // rate of view height
  val MARGIN_LR_BASE = 0.06 // rate of view width

  var MARGIN_TOP = MARGIN_TOP_BASE
  var MARGIN_AUTHOR = MARGIN_AUTHOR_BASE
  var MARGIN_BOTTOM_ORIG = MARGIN_BOTTOM_BASE
  var MARGIN_BOTTOM = MARGIN_BOTTOM_ORIG
  var MARGIN_LR = MARGIN_LR_BASE

  val SPACE_V = 0.15 // rate of text size
  val SPACE_H = 0.05 // rate of view width
  val SPACE_V_FURIGANA = 0.15 // rate of text size
  val FURIGANA_TOP_LIMIT = 0.02 // rate of view height
  val FURIGANA_BOTTOM_LIMIT = 0.03 // rate of view height
  val FURIGANA_RATIO_DEFAULT = 0.70 // rate of span_h
  val FURIGANA_MARGIN_LEFT_MIN = 2 // in pixels

  var show_furigana = true
  var show_author = true
  var show_kami = true
  var show_simo = true

  var torifuda_reverse = false

  def initYomifuda(){
    val margin_boost = Utils.getDimenFloat(context,R.dimen.poemtext_margin_yomifuda)
    MARGIN_TOP = MARGIN_TOP_BASE.map{_*margin_boost}
    MARGIN_AUTHOR = MARGIN_AUTHOR_BASE.map{_*margin_boost}
    MARGIN_BOTTOM_ORIG = MARGIN_BOTTOM_BASE*margin_boost
    MARGIN_BOTTOM = MARGIN_BOTTOM_ORIG
    MARGIN_LR = MARGIN_LR_BASE*margin_boost

    val main_font = TypefaceManager.get(context,YomiInfoUtils.getPoemTextFont)
    paint.setTypeface(main_font)
    val furigana_font = Globals.prefs.get.getString("yomi_info_furigana_font",YomiInfoUtils.DEFAULT_FONT)
    paint_furigana.setTypeface(TypefaceManager.get(context,furigana_font))
  }

  // Typeface of paint must be set before calling this function
  // Also text size must be default before calling this function ( I don't know why, maybe it's a bug ? )
  def calculateTextSize(text_array_with_margin:Array[(String,Double)],paint:Paint,space_h:Double):Int ={
    // we estimate the result text size, the closer it is, the result will be more accurate
    val estimate_size = self.getWidth / text_array_with_margin.length
    paint.setTextSize(estimate_size)
    val bounds = (for((t,m)<-text_array_with_margin)yield(measureBoundOneLine(t,paint)))
    val r1 = (for(((w,_,h),(t,m)) <- bounds.zip(text_array_with_margin))yield{
      val ya = (1-m-MARGIN_BOTTOM)*getHeight.toFloat
      val yb = h + (t.length-1) * SPACE_V * estimate_size;
      ya / yb
    }).min
    val xa = (1-MARGIN_LR*2-text_array_with_margin.length*space_h)*getWidth.toFloat
    val xb = bounds.map{case(w,_,h)=>w}.sum
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
      val (_,_,this_height_wo) = measureBoundOneLine(furigana,paint_furigana)
      val this_height = this_height_wo + (furigana.length-1)*span_v
      val candidate_y1 = starty+height/2-this_height.toInt/2
      val candidate_y2 = (getHeight*(1-FURIGANA_BOTTOM_LIMIT)).toInt - this_height
      val sy = Math.max(prev_furigana_bottom, Math.min(candidate_y1,candidate_y2))

      val sx = (startx+Math.max(margin_left_furigana,width/2+paint_furigana.getTextSize/2+FURIGANA_MARGIN_LEFT_MIN)).toInt
      val(furigana_y,_,_) = drawVertical(paint_furigana,canvas,sx,sy,furigana,span_v)
      new_furigana_bottom = furigana_y
    }
    (y,new_furigana_bottom)
  }
  def drawVertical(paint:Paint,canvas:Canvas,startx:Int,starty:Int,text:String,span:Int) = {
    var y = starty
    var width = Int.MinValue
    for(t <- text){
      val r = new Rect()
      paint.getTextBounds(t.toString,0,1,r)
      width = Math.max(r.right-r.left,width)
      val yy = (y - r.top).toInt
      self.drawTextOrPath(canvas,paint,t.toString,startx,yy)
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
        val screen_range = if(paint.hashCode == paint_furigana.hashCode){screen_range_furi}else{screen_range_main}
        screen_range.left = Math.min(r_l,screen_range.left)
        screen_range.top = Math.min(r_t,screen_range.top)
        screen_range.right = Math.max(r_r,screen_range.right)
        screen_range.bottom = Math.max(r_b,screen_range.bottom)
      }
    }
    (y,width,y-starty-span)
  }

  def updateMarker(num:Option[Int]){
    marker = num.map{ n =>
      // Black Spade Suit, Red Heart Suit, Red Diamond Suit, Black Club Suit,
      // White Spade Suit, White Heart Suit, White Diamond Suit, White Club Suit
      // see https://en.wikipedia.org/wiki/Playing_cards_in_Unicode
      val symbols = Array("\u2660","\u2661","\u2662","\u2663","\u2664","\u2665","\u2666","\u2667")
      val seed = System.currentTimeMillis / (1800 * 1000) // preserve same marker for thirty minutes
      // TODO: is this a correct usage of MurmurHash3 ?
      val hash = MurmurHash3.finalizeHash(MurmurHash3.mix(seed.toInt,n),2)
      var index = hash % symbols.length
      if(index < 0){
        index += symbols.length
      }
      symbols(index)
    }
  }

  // boost : [1.0, 1.4]
  // ratio:  [0.6, 1.0]
  def calcBoostRatioFurigana():(Double,Double) = {
    val furigana_width_conf_default = self.context.getResources.getInteger(R.integer.yomi_info_furigana_width_default)
    val furigana_width_conf_max = self.context.getResources.getInteger(R.integer.yomi_info_furigana_width_max)
    val furigana_width_conf_cur = Globals.prefs.get.getInt("yomi_info_furigana_width",furigana_width_conf_default)
    val boost = if(furigana_width_conf_cur > furigana_width_conf_default){
      1.0 + 0.4*(furigana_width_conf_cur - furigana_width_conf_default)/(furigana_width_conf_max - furigana_width_conf_default).toDouble
    }else{
      1.0
    }
    val ratio = if(furigana_width_conf_cur < furigana_width_conf_default){
      1.0 - 0.4*(1.0 - (furigana_width_conf_cur/furigana_width_conf_default.toDouble))
    }else{
      1.0
    }
    (boost,ratio)
  }

  // rate: [0.6, 1.4]
  def calcRateFurigana():Double = {
    val (boost,ratio) = calcBoostRatioFurigana
    if(boost > 1.0){
      boost
    }else if(ratio < 1.0){
      ratio
    }else{
      1.0
    }
  }

  def onDrawYomifuda(canvas:Canvas){

    if(Globals.IS_DEBUG){
      val paint_debug = new Paint()
      paint_debug.setStyle(Paint.Style.STROKE)
      paint_debug.setStrokeWidth(3)
      paint_debug.setColor(Color.GREEN)
      canvas.drawRect((getWidth*MARGIN_LR).toInt,(getHeight*MARGIN_TOP.min).toInt,
        (getWidth*(1.0-MARGIN_LR)).toInt,(getHeight*(1-MARGIN_BOTTOM)).toInt,paint_debug)
      paint_debug.setColor(Color.CYAN)
      canvas.drawRect((getWidth*MARGIN_LR).toInt,(getHeight*FURIGANA_TOP_LIMIT).toInt,
        (getWidth*(1.0-MARGIN_LR)).toInt,(getHeight*(1-FURIGANA_BOTTOM_LIMIT)).toInt,paint_debug)
      val ni = {()=>new Rect(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE)}
      screen_range_main = ni()
      screen_range_furi = ni()
    }
    cur_num.foreach{num =>
      paint.setTextAlign(Paint.Align.CENTER)
      paint_furigana.setTextAlign(Paint.Align.CENTER)

      var text_array_with_margin:Array[(String,Double)] = getTextArrayWithMargin[Double](num,R.array.list_full,R.array.author," ","",true,MARGIN_TOP,MARGIN_AUTHOR)
      if(text_array_with_margin.isEmpty){
        text_array_with_margin = Array((marker.getOrElse("\u2022"),0.4))
        MARGIN_BOTTOM = 0.4
      }else{
        MARGIN_BOTTOM = MARGIN_BOTTOM_ORIG
      }

      if(show_author && ! show_kami && ! show_simo){
        // if only author then vertical center since the furigana will sometimes be larger than poem text
        val mint = text_array_with_margin.map{_._2}.min
        text_array_with_margin = text_array_with_margin.map{case(t,m)=>(t,m-mint+MARGIN_BOTTOM)}
      }

      val space_boost1 = if(show_furigana){
        1.3
      }else{
        1.0
      }

      val space_boost2 = text_array_with_margin.length match {
        case 1 => 1.4
        case 2 => 1.3
        case 3 => 1.2
        case 4 => 1.1
        case 5 => 1.0
        case 6 => 0.9
        case 7 => 0.8
        case _ => 0.8
      }

      val (space_boost3,furigana_ratio) = if(show_furigana){calcBoostRatioFurigana}else{(1.0,1.0)}

      val space_h = SPACE_H * space_boost1 * space_boost2 * space_boost3

      val no_furigana = text_array_with_margin.map{case(t,m)=>(AllFuda.removeInsideParens(t),m)}
      val text_size = calculateTextSize(no_furigana,paint,space_h)
      paint.setTextSize(text_size)

      switchRenderWithPathWhenLarge(paint,no_furigana.map{case(t,m)=>t})

      val (actual_width_ave,_,_) = measureBoundAve(no_furigana.map{case(t,m)=>t},paint)
      val span_h = space_h*getWidth
      val rowspan = (span_h+actual_width_ave).toInt
      var startx = (getWidth/2 + (rowspan*(text_array_with_margin.length-1))/2).toInt
      var margin_left_furigana = 0.0
      if(show_furigana){
        val furisize_tmp = (getWidth/text_array_with_margin.length)/4 // this must be close to result text size
        paint_furigana.setTextSize(furisize_tmp)
        val only_furigana = text_array_with_margin.map{case(t,m)=>AllFuda.onlyInsideParens(t)}
        val (actual_width_furigana,_,furigana_height_max) = measureBoundMax(only_furigana,paint_furigana)
        val actual_ratio_furigana = furisize_tmp / actual_width_furigana.toFloat
        val candidate_w = span_h.toFloat*actual_ratio_furigana*furigana_ratio.toFloat*FURIGANA_RATIO_DEFAULT

        val candidate_h = furisize_tmp * (1-FURIGANA_TOP_LIMIT-FURIGANA_BOTTOM_LIMIT)*getHeight / (furisize_tmp* SPACE_V_FURIGANA * (only_furigana.map{_.length-1}).max + furigana_height_max)
        paint_furigana.setTextSize(Math.min(candidate_w,candidate_h).toFloat)
        margin_left_furigana = rowspan / 2.0
        startx -= (span_h / 2.0).toInt
      }
      for((t,m) <- text_array_with_margin){
        val starty = (getHeight * m).toInt
        verticalText(canvas,startx,starty,t,margin_left_furigana.toInt)
        startx -= rowspan
      }
      if(Globals.IS_DEBUG){
        for(screen_range <- Array(screen_range_main,screen_range_furi) if screen_range.left != Integer.MAX_VALUE){
          val (name,t_top,t_bottom) = if(screen_range.hashCode == screen_range_furi.hashCode){
            ("screen_range_furi",FURIGANA_TOP_LIMIT,FURIGANA_BOTTOM_LIMIT)
          }else{
            ("screen_range_main",MARGIN_TOP.min,MARGIN_BOTTOM)
          }
          val margin_l = screen_range.left / getWidth.toDouble
          val margin_t = screen_range.top / getHeight.toDouble
          val margin_r = (getWidth - screen_range.right) / getWidth.toDouble
          val margin_b = (getHeight - screen_range.bottom) / getHeight.toDouble
          Log.v("wasuramoti_debug",String.format("%s,n=%d,l=%.3f t=%.3f r=%.3f b=%.3f",
            name,
            new java.lang.Integer(num),
            new java.lang.Double(margin_l),
            new java.lang.Double(margin_t),
            new java.lang.Double(margin_r),
            new java.lang.Double(margin_b)))
          if(margin_l < MARGIN_LR*0.9){
            Log.w("wasuramoti_debug",name + ",n=" + num + " => margin_l is too small")
          }
          if(margin_r < MARGIN_LR*0.9){
            Log.w("wasuramoti_debug",name + ",n=" + num + " => margin_r is too small")
          }
          if(margin_t < t_top*0.9){
            Log.w("wasuramoti_debug",name + ",n=" + num + " => margin_t is too small")
          }
          if(margin_b < t_bottom*0.9){
            Log.w("wasuramoti_debug",name + ",n=" + num + " => margin_b is too small")
          }
        }
      }
    }
  }
}

trait YomiInfoTorifudaTrait{
  self:YomiInfoView =>
  val FUDA_RATE = 74.0/53.0 // fuda size of kyogi karuta is 74mm x 53mm
  val FUDA_MARGIN_TB = 0.05
  val FUDA_MARGIN_LR = 0.05
  val FUDA_PADDING_LR = 0.09
  val FUDA_PADDING_TB = 0.09
  val FUDA_SPAN_V = 0.07
  val FUDA_SPAN_H = 0.07
  val FUDA_CHARS_PER_ROW = 5
  val FUDA_MAX_ROW = 3
  val FUDA_EDGE_SIZE = 0.04

  val paint_edge = new Paint(Paint.ANTI_ALIAS_FLAG)

  def initTorifuda(){
    val font = Globals.prefs.get.getString("yomi_info_torifuda_font",YomiInfoUtils.DEFAULT_FONT)
    paint.setTypeface(TypefaceManager.get(context,font))
  }
  def drawEdge(canvas:Canvas,frame:Rect){
    val edge_size = FUDA_EDGE_SIZE*(frame.bottom-frame.top)
    val edge_rect = new Rect(
      (frame.left + edge_size / 2).toInt,
      (frame.top + edge_size / 2).toInt,
      (frame.right - edge_size / 2).toInt,
      (frame.bottom - edge_size / 2).toInt)
    if(ColorThemeHelper.getFromPref.fillTorifuda){
      paint_edge.setStyle(Paint.Style.FILL)
      paint_edge.setColor(Utils.attrColor(self.context,R.attr.torifudaFillColor))
      canvas.drawRect(edge_rect,paint_edge)
    }
    paint_edge.setStyle(Paint.Style.STROKE)
    paint_edge.setColor(Utils.attrColor(self.context,R.attr.torifudaEdgeColor))
    paint_edge.setStrokeWidth(edge_size.toFloat)
    canvas.drawRect(edge_rect,paint_edge)
  }
  def calcFudaTextSize(paint:Paint,frame:Rect):Float ={
    val wmax = frame.right - frame.left
    val hmax = frame.bottom - frame.top
    val wtext = (1.0-FUDA_PADDING_LR*2-FUDA_SPAN_H*(FUDA_MAX_ROW-1))*wmax/FUDA_MAX_ROW
    val htext = (1.0-FUDA_PADDING_TB*2-FUDA_SPAN_V*(FUDA_CHARS_PER_ROW-1))*hmax/FUDA_CHARS_PER_ROW
    val estimate_size = hmax / FUDA_CHARS_PER_ROW
    paint.setTextSize(estimate_size)
    // we set same text size for all fuda
    val (w_ave,h_ave,_) = measureBoundAve(AllFuda.get(context,R.array.list_torifuda).head.replace(" ","").toArray.map{_.toString},paint)
    (Math.min(wtext.toDouble/w_ave.toDouble,htext.toDouble/h_ave.toDouble)*estimate_size).toFloat
  }
  def onDrawTorifuda(canvas:Canvas){
    cur_num.foreach{ num=>
      paint.setTextAlign(Paint.Align.CENTER)
      val margin_boost = Utils.getDimenFloat(context,R.dimen.poemtext_margin_torifuda)
      val max_height = getHeight * (1.0 - 2*FUDA_MARGIN_TB*margin_boost)
      val max_width = getWidth * (1.0 - 2*FUDA_MARGIN_LR*margin_boost)
      val r = max_height / max_width
      val (fuda_width,fuda_height) = if(r < FUDA_RATE){
        (max_height/FUDA_RATE,max_height)
      }else{
        (max_width,max_width*FUDA_RATE)
      }
      val center_x = getWidth / 2.0
      val center_y = getHeight / 2.0
      val frame = new Rect((center_x - fuda_width/2.0).toInt,
        (center_y - fuda_height/2.0).toInt,
        (center_x + fuda_width/2.0).toInt,
        (center_y + fuda_height/2.0).toInt)
      drawEdge(canvas,frame)
      paint.setTextSize(calcFudaTextSize(paint,frame))
      val ary = AllFuda.get(context,R.array.list_torifuda)(num).replace(" ","").toArray.map{_.toString}.grouped(FUDA_CHARS_PER_ROW).toArray

      switchRenderWithPathWhenLarge(paint,ary.map{_.mkString("")})

      val width = (1.0 - 2*FUDA_PADDING_LR)*fuda_width
      val height = (1.0 - 2*FUDA_PADDING_TB)*fuda_height
      val dx = width / FUDA_MAX_ROW.toDouble
      for(i <- 0 until FUDA_MAX_ROW){
        val x = center_x + width/2.0 - dx/2.0 - dx*i
        val row:Array[String] = (if(i == FUDA_MAX_ROW-1){
          ary.drop(i).flatten.toArray
        }else{
          ary(i)
        })
        val dy = height / Math.max(row.length,FUDA_CHARS_PER_ROW).toDouble
        for(j <- 0 until row.length){
          val s = row(j)
          val y = center_y - height/2.0 + dy/2.0 + dy*j
          val r = new Rect()
          paint.getTextBounds(s,0,1,r)
          val ch = r.bottom - r.top
          val yy = y.toFloat+ch/2.0f-r.bottom
          drawTextOrPath(canvas,paint,s,x.toFloat,yy)
          if(Globals.IS_DEBUG){
            val paint_debug = new Paint()
            paint_debug.setStyle(Paint.Style.STROKE)
            paint_debug.setColor(Color.RED)
            paint_debug.setStrokeWidth(3)
            val r_l = x-(r.right-r.left)/2
            val r_t = yy+r.top
            val r_r = x+(r.right-r.left)/2
            val r_b = yy+r.bottom
            canvas.drawRect(r_l.toFloat,r_t.toFloat,r_r.toFloat,r_b.toFloat,paint_debug)
          }
        }
      }
    }
  }
}
trait YomiInfoEnglishTrait{
  self:YomiInfoView =>
  // TODO: use infinite array
  val ENG_MARGIN_LEFT = Array.fill[Paint.Align](8)(Paint.Align.CENTER)
  val ENG_MARGIN_AUTHOR = Array.fill[Paint.Align](2)(Paint.Align.RIGHT)
  val ENG_MARGIN_TB_BASE = 0.04 // rate of view height
  val ENG_MARGIN_LR_BASE = 0.04 // rate of view height
  val ENG_ROWSPAN = 0.04 // rate of view height

  var ENG_MARGIN_TB = ENG_MARGIN_TB_BASE
  var ENG_MARGIN_LR = ENG_MARGIN_LR_BASE
  def initEnglish(){
    paint.setTypeface(TypefaceManager.get(context,Globals.prefs.get.getString("yomi_info_english_font","Serif")))
    val margin_boost = Utils.getDimenFloat(context,R.dimen.poemtext_margin_yomifuda)
    ENG_MARGIN_TB = ENG_MARGIN_TB_BASE*margin_boost
    ENG_MARGIN_LR = ENG_MARGIN_LR_BASE*margin_boost
  }

  def measureTextSizeEng(ar:Array[String],paint:Paint):(Int,Int,Int) = {
    var tsumx = 0
    var tsumy = 0
    var topsum = 0
    for(t<-ar){
      val r = new Rect()
      paint.getTextBounds(t,0,t.length,r)
      tsumx = Math.max(tsumx,r.right-r.left)
      tsumy += r.bottom - r.top
      topsum += r.top
    }
    (tsumx,tsumy,topsum/ar.length)
  }
  def calculateTextSizeEng(text_array_with_margin:Array[(String,Paint.Align)],paint:Paint):Int ={
    val rows = text_array_with_margin.size
    val estimated = getHeight / rows
    paint.setTextSize(estimated)
    val resty = (1.0 - ENG_MARGIN_TB*2 - (rows-1)*ENG_ROWSPAN)*getHeight
    val restx = (1.0 - ENG_MARGIN_LR*2)*getWidth
    val (tsumx,tsumy,_) = measureTextSizeEng(text_array_with_margin.map{_._1},paint)
    val rr = Math.min(resty/tsumy,restx/tsumx)
    (rr*estimated).toInt
  }
  def onDrawEnglish(canvas:Canvas){
    if(Globals.IS_DEBUG){
      val paint_debug = new Paint()
      paint_debug.setStyle(Paint.Style.STROKE)
      paint_debug.setColor(Color.RED)
      paint_debug.setStrokeWidth(3)
      val r_l = getWidth * ENG_MARGIN_LR
      val r_r = getWidth - r_l
      val r_t = getHeight * ENG_MARGIN_TB
      val r_b = getHeight - r_t
      canvas.drawRect(r_l.toFloat,r_t.toFloat,r_r.toFloat,r_b.toFloat,paint_debug)
    }
    cur_num.foreach{num =>
      val text_array_with_margin = getTextArrayWithMargin[Paint.Align](num,R.array.list_full_en,R.array.author_en,"//","##",false,ENG_MARGIN_LEFT,ENG_MARGIN_AUTHOR,"- ")
      if(text_array_with_margin.isEmpty){
        return
      }
      val textsize = calculateTextSizeEng(text_array_with_margin,paint)
      paint.setTextSize(textsize)
      val (_,tsumy,topave) = measureTextSizeEng(text_array_with_margin.map{_._1},paint)
      val rows = text_array_with_margin.length
      val rowspan = getHeight*ENG_ROWSPAN
      val charheight = tsumy / rows
      val rowheight = charheight + rowspan
      var starty = getHeight/2.0 - tsumy/2.0 - (rows-1)*rowspan/2.0 - topave

      if(charheight >= RENDER_WITH_PATH_THRESHOLD){
        render_with_path = true
      }

      for((t,m) <- text_array_with_margin){
        paint.setTextAlign(m)
        val x = if(
          m == Paint.Align.CENTER){
          getWidth/2
        }else{
          getWidth - ENG_MARGIN_LR*getWidth
        }
        drawTextOrPath(canvas,paint,t,x.toFloat,starty.toFloat)
        starty += rowheight
      }
    }
  }
}
trait YomiInfoRomajiTrait{
  self:YomiInfoView =>
  val ROMAJI_LR_BASE = 0.06 // rate of view width
  val ROMAJI_TB_BASE = 0.06 // rate of view height
  val ROMAJI_ROWSPAN = 0.03 // rate of view height
  val ROMAJI_WORDSPAN = 0.04 // rate of view width
  val ROMAJI_ROMA_HIRA_SPAN = 0.01 // rate of view height

  var ROMAJI_LR = ROMAJI_LR_BASE
  var ROMAJI_TB = ROMAJI_TB_BASE
  def initRomaji(){
    val margin_boost = Utils.getDimenFloat(context,R.dimen.poemtext_margin_yomifuda)
    ROMAJI_LR = ROMAJI_LR_BASE * margin_boost
    ROMAJI_TB = ROMAJI_TB_BASE * margin_boost

    val main_font = TypefaceManager.get(context,YomiInfoUtils.getPoemTextFont)
    paint.setTypeface(main_font)
    // Roboto is default font for android >= 4.0, so most of the devices should already have it.
    // However, there might be a possibility that the vender removes the font
    // and replace it to which that does not have letter with macron.
    // Therefore, we include this font in this app.
    val furigana_font = TypefaceManager.get(context,"asset:roboto-slab.ttf")
    paint_furigana.setTypeface(furigana_font)
  }

  def getRomajiBounds(hira:String,roma:String,paint:Paint,paint_furigana:Paint):(Int,(Int,Int),(Int,Int)) = {
    val r_hira = new Rect
    val r_roma = new Rect
    paint.getTextBounds(hira,0,hira.length,r_hira)
    paint_furigana.getTextBounds(roma,0,roma.length,r_roma)
    val w_hira = r_hira.right - r_hira.left
    val w_roma = r_roma.right - r_roma.left
    val h_hira = r_hira.bottom - r_hira.top
    val h_roma = r_roma.bottom - r_roma.top
    val width = Math.max(w_hira,w_roma)
    (width,(w_hira,h_hira),(w_roma,h_roma))
  }

  def measureTextSizeRomaji(all_array:Array[Array[(String,String)]],paint:Paint,paint_furigana:Paint,space:Int):(Int,Int,Int) = {
    var max_roma = 0
    var max_hira = 0
    val w_max = all_array.map{ ar =>
      ar.map{case (roma,hira) =>
        val (width,(_,h_hira),(_,h_roma)) = getRomajiBounds(hira,roma,paint,paint_furigana)
        max_roma = Math.max(max_roma,h_roma)
        max_hira = Math.max(max_hira,h_hira)
        width
      }.sum + space * (ar.length - 1)
    }.max
    (w_max,max_hira,max_roma)
  }
  def calculateTextSizeRomaji(all_array:Array[Array[(String,String)]],paint:Paint,paint_furigana:Paint):(Int,Int,Int) ={
    val furigana_rate = calcRateFurigana
    val row_chars = all_array.map{ ar => ar.map{_._2.length}.sum + ar.length - 1}.max
    val roma_rate =
    if(row_chars >= 14){
      0.8
    }else if(all_array.length <= 5){
      0.6
    }else if(all_array.length == 6){
      0.7
    }else{
      0.8
    }
    val base_rate = Math.min( 0.5 / all_array.length, 1.0 / row_chars)
    val estimated_roma = (getHeight * base_rate * roma_rate * furigana_rate).toInt
    val estimated_hira = (getHeight * base_rate).toInt
    val estimated_space = (Math.max(estimated_roma,estimated_hira)/2.0).toInt
    paint.setTextSize(estimated_hira)
    paint_furigana.setTextSize(estimated_roma)
    val (w_max,max_hira,max_roma) = measureTextSizeRomaji(all_array,paint,paint_furigana,estimated_space)
    val h_screen = (1 - ROMAJI_TB*2 - ROMAJI_ROWSPAN*(all_array.length-1) - ROMAJI_ROMA_HIRA_SPAN*all_array.length) * getHeight
    val w_screen = (1 - ROMAJI_LR*2) * getWidth
    val rate_h = h_screen / ((max_roma + max_hira)*all_array.length)
    val rate_w = w_screen / w_max
    val r = Math.min(rate_h,rate_w)
    ((estimated_hira*r).toInt,(estimated_roma*r).toInt,(estimated_space*r).toInt)
  }
  def renderRomaji(canvas:Canvas,ar:Array[(String,String)],dy:Int,hira_height:Int,space:Int){
    var dx = (getWidth * ROMAJI_LR).toInt
    for((roma,hira) <- ar){
      val (width,(w_hira,_),(w_roma,_)) = getRomajiBounds(hira,roma,paint,paint_furigana)
      drawTextOrPath(canvas,paint_furigana,roma,dx+width/2-w_roma/2,dy)
      drawTextOrPath(canvas,paint,hira,dx+width/2-w_hira/2,dy+hira_height)
      dx += width + space
    }
  }
  def onDrawRomaji(canvas:Canvas){
    cur_num.foreach{ num =>
      paint.setTextAlign(Paint.Align.LEFT)
      paint_furigana.setTextAlign(Paint.Align.LEFT)
      val margin = Array.fill[Double](5)(0) // dummy
      val text_array_with_margin:Array[(String,Double)] = getTextArrayWithMargin[Double](num,R.array.list_full,R.array.author," ","",false,margin,margin)
      val romaji_array_with_margin:Array[(String,Double)] = getTextArrayWithMargin[Double](num,R.array.list_full_romaji,R.array.author_romaji,"\\|","",false,margin,margin)

      if(text_array_with_margin.isEmpty || romaji_array_with_margin.isEmpty){
        return
      }
      val all_array = text_array_with_margin.zip(romaji_array_with_margin).map{ case ((txt,margin),(roma,_)) =>
        val hira = AllFuda.getOnlyHiragana(txt)
        AllFuda.splitToCorrespondingRomaji(roma,hira)
      }

      val (size_h,size_r,space) = calculateTextSizeRomaji(all_array,paint,paint_furigana)
      val roma_hira_span = (ROMAJI_ROMA_HIRA_SPAN * getHeight).toInt
      if(size_h >= RENDER_WITH_PATH_THRESHOLD || size_r >= RENDER_WITH_PATH_THRESHOLD){
        render_with_path = true
      }
      paint.setTextSize(size_h)
      paint_furigana.setTextSize(size_r)
      val (_,max_hira,max_roma) = measureTextSizeRomaji(all_array,paint,paint_furigana,space)
      val hira_height = roma_hira_span + max_hira
      val row_span = hira_height + max_roma + (ROMAJI_ROWSPAN*getHeight).toInt
      var dy = max_roma + (getHeight * ROMAJI_TB).toInt
      for(ar <- all_array){
        renderRomaji(canvas,ar,dy,hira_height,space)
        dy += row_span
      }
    }
  }
}
