package karuta.hpnpwd.wasuramoti
import _root_.android.content.Context
import _root_.android.view.{View,MotionEvent,ViewTreeObserver}
import _root_.android.widget.HorizontalScrollView
import _root_.android.graphics.Typeface
import _root_.android.util.AttributeSet
import _root_.android.os.CountDownTimer

import java.util.concurrent.CountDownLatch

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
    }else if(conf == "Serif"){
      Typeface.SERIF
    }else{
      Typeface.DEFAULT
    }
  }
}

object YomiInfoConf{
  var SCROLL_THRESHOLD = 0.17
  var SCROLL_THRESHOLD_DIP = 70f
  val SCROLL_SPEED = 200 // in millisec
}

class YomiInfoLayout(context:Context, attrs:AttributeSet) extends HorizontalScrollView(context, attrs){

  var cur_view = None:Option[Int]
  var should_be_played_list = List[Option[Int]]()

  var scrolling_latch = None:Option[CountDownLatch]

  def scrollAnimation(endx:Int,on_finish:()=>Unit={()=>Unit}){
    val startx = getScrollX
    scrolling_latch.foreach{_.await()}
    scrolling_latch = Some(new CountDownLatch(1))
    new CountDownTimer(YomiInfoConf.SCROLL_SPEED,10){
      override def onTick(millisUntilFinished:Long){
        val r = millisUntilFinished / YomiInfoConf.SCROLL_SPEED.toFloat
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
              scrolling_latch.foreach{_.countDown()}
              scrolling_latch = None
            }},30)
      }
    }.start
  }
  override def onTouchEvent(ev:MotionEvent):Boolean = {
    super.onTouchEvent(ev)
    ev.getAction match{
      case MotionEvent.ACTION_UP =>
        if(!scrolling_latch.isEmpty){
          return true
        }
        cur_view.foreach{ vid=>
          val v = findViewById(vid)
          if(v != null){
            val dx = getScrollX-v.getLeft
            val threshold = Math.min((v.getWidth * YomiInfoConf.SCROLL_THRESHOLD).toInt, Utils.dpToPx(YomiInfoConf.SCROLL_THRESHOLD_DIP).toInt)
            val nvid = if(Math.abs(dx) > threshold){
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

  def scrollToView(id:Int,smooth:Boolean,from_touch_event:Boolean=false,do_after_done:Option[()=>Unit]=None){
    synchronized{
      val v = findViewById(id).asInstanceOf[YomiInfoView]
      if(v!=null){
        cur_view = Some(id)
        getContext.asInstanceOf[WasuramotiActivity].updatePoemInfo(id)
        val x = v.getLeft
        val have_to_move = from_touch_event && Array(R.id.yomi_info_view_prev,R.id.yomi_info_view_next).contains(id)
        val func = do_after_done.getOrElse(
            {()=>
              if(have_to_move){
                val wa = context.asInstanceOf[WasuramotiActivity]
                wa.cancelAllPlay()
                v.cur_num.foreach{ cn =>
                  FudaListHelper.queryIndexFromFudaNum(context,cn).foreach{index =>
                    FudaListHelper.putCurrentIndex(context,index)
                  }
                }
                wa.refreshAndSetButton()
                wa.invalidateYomiInfo()

                val play_after_swipe = Globals.prefs.get.getBoolean("play_after_swipe",false)
                if(play_after_swipe && !Globals.player.isEmpty){
                  val auto = Globals.prefs.get.getBoolean("autoplay_enable",false)
                  wa.doPlay(auto_play=auto,from_swipe=true)
                }
              }
            })
        if(smooth){
          scrollAnimation(x,
            () =>{
              func()
              updateShouldBePlayedList()
            })
        }else{
          scrollTo(x,0)
          updateShouldBePlayedList()
        }
      }
    }
  }

  def updateShouldBePlayedList(){
    should_be_played_list = if(Utils.readCurNext(context)){
      List(getCurNum,getNextNum)
    }else{
      List(getCurNum)
    }
  }
  
  def getCurNum():Option[Int] = {
    cur_view.flatMap(findViewById(_).asInstanceOf[YomiInfoView].cur_num)
  }
  def getNextNum():Option[Int] = {
    cur_view.flatMap(id => {
      val nid = id match {
        case R.id.yomi_info_view_prev => Some(R.id.yomi_info_view_cur)
        case R.id.yomi_info_view_cur => Some(R.id.yomi_info_view_next)
        case _ => None
      }
      nid.flatMap( x =>
          Option(findViewById(x).asInstanceOf[YomiInfoView]).flatMap{_.cur_num}
      )
    })
  }
}
