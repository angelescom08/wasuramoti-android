package karuta.hpnpwd.wasuramoti

import _root_.android.content.pm.ActivityInfo
import _root_.android.content.res.Configuration
import _root_.android.app.{Activity,AlertDialog}
import _root_.android.media.AudioManager
import _root_.android.content.{Intent,Context}
import _root_.android.os.{Bundle,Handler,Parcelable,Build}
import _root_.android.view.{View,Menu,MenuItem,WindowManager,Surface}
import _root_.android.view.animation.{AnimationUtils,Interpolator}
import _root_.android.widget.{ImageView,Button,RelativeLayout,ViewFlipper}
import _root_.android.support.v7.app.{ActionBarActivity,ActionBar}
import _root_.java.lang.Runnable
import _root_.java.util.{Timer,TimerTask}
import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import scala.collection.mutable


class WasuramotiActivity extends ActionBarActivity with MainButtonTrait with ActivityDebugTrait{
  val MINUTE_MILLISEC = 60000
  var haseo_count = 0
  var release_lock = None:Option[Unit=>Unit]
  var timer_autoread = None:Option[Timer]
  var timer_dimlock = None:Option[Timer]
  var timer_refresh_text = None:Option[Timer]
  var ringer_mode_bkup = None:Option[Int]
  def restartRefreshTimer(){
    Globals.global_lock.synchronized{
      timer_refresh_text.foreach(_.cancel())
      timer_refresh_text = None
      if(!NotifyTimerUtils.notify_timers.isEmpty){
        timer_refresh_text = Some(new Timer())
        timer_refresh_text.get.schedule(new TimerTask(){
          override def run(){
            if(NotifyTimerUtils.notify_timers.isEmpty){
              timer_refresh_text.foreach(_.cancel())
              timer_refresh_text = None
              return
            }
            Utils.setButtonTextByState(getApplicationContext())
          }
        },0,MINUTE_MILLISEC)
      }
    }
  }

  def cancelAllPlay(){
    Globals.player.foreach(_.stop())
    timer_autoread.foreach(_.cancel())
    timer_autoread = None
  }

  def refreshAndSetButton(force:Boolean = false){
    Globals.global_lock.synchronized{
      Globals.player = AudioHelper.refreshKarutaPlayer(this,Globals.player,force)
      Utils.setButtonTextByState(getApplicationContext())
    }
  }

  override def onCreateOptionsMenu(menu: Menu):Boolean = {
    val inflater = getMenuInflater
    inflater.inflate(R.menu.main, menu)
    super.onCreateOptionsMenu(menu)
  }
  override def onOptionsItemSelected(item: MenuItem):Boolean = {
    cancelAllPlay()
    Utils.setButtonTextByState(getApplicationContext())
    val refresh_task:Unit=>Unit = _=>{
      refreshAndSetButton()
      invalidateYomiInfo()
    }
    item.getItemId match {
      case R.id.menu_shuffle => {
        Utils.confirmDialog(this,Right(R.string.menu_shuffle_confirm),_=>{
          FudaListHelper.shuffle(getApplicationContext())
          FudaListHelper.moveToFirst(getApplicationContext())
          refresh_task()
        })
      }
      case R.id.menu_move => new MovePositionDialog(this,refresh_task).show
      case R.id.menu_timer => startActivity(new Intent(this,classOf[NotifyTimerActivity]))
      case R.id.menu_conf => startActivity(new Intent(this,classOf[ConfActivity]))
      case android.R.id.home => {
        // android.R.id.home is returned when the Icon is clicked if we are using android.support.v7.app.ActionBarActivity
        if(haseo_count < 3){
          haseo_count += 1
        }else{
          val layout = new RelativeLayout(this)
          val builder = new AlertDialog.Builder(this)
          val iv = new ImageView(this)
          iv.setImageResource(R.drawable.hasewo)
          iv.setAdjustViewBounds(true)
          iv.setScaleType(ImageView.ScaleType.FIT_XY)
          val metrics = getResources.getDisplayMetrics
          val maxw = metrics.widthPixels
          val maxh = metrics.heightPixels
          val width = iv.getDrawable.getIntrinsicWidth
          val height = iv.getDrawable.getIntrinsicHeight
          val ratio = width.toDouble/height.toDouble
          val OCCUPY_IN_SCREEN = 0.9
          val Array(tw,th) = (if(maxw/ratio < maxh){
            Array(maxw,maxw/ratio)
          }else{
            Array(maxh*ratio,maxh)
          })
          val Array(neww,newh) = (for (i <- Array(tw,th))yield (i*OCCUPY_IN_SCREEN).toInt)
          val params = new RelativeLayout.LayoutParams(neww,newh)
          params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
          iv.setLayoutParams(params)
          layout.addView(iv)
          builder.setView(layout)
          val dialog = builder.create
          dialog.show
          // we have to get attributes after show()
          val dparams = dialog.getWindow.getAttributes
          dparams.height = newh
          dparams.width = neww
          dialog.getWindow.setAttributes(dparams)
          haseo_count = 0
        }
      }

      case _ => {}
    }
    return true
  }

  def switchViewAndReloadHandler(){
    val flipper = findViewById(R.id.main_flip).asInstanceOf[ViewFlipper]
    val (cn,rb) = if(!Utils.showYomiInfo){
        (0,R.id.read_button_large)
    }else{
        (1,R.id.read_button_small)
    }
    flipper.setDisplayedChild(cn)

    val read_button = findViewById(rb).asInstanceOf[Button]
    val handler = new Handler()
    Globals.setButtonText = Some( arg =>
      handler.post(new Runnable(){
        override def run(){
          arg match {
            // TODO: The following way to call setText is not smart.
            //       Is there any way to do the following two lines in one row ?
            case Left(text) => read_button.setText(text)
            case Right(id) => read_button.setText(id)
          }
        }
      }))
  }
  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    if(Globals.IS_DEBUG){
      setTitle(getResources().getString(R.string.app_name) + " DEBUG")
    }

    //try loading 'libvorbis.so'
    val decoder = new OggVorbisDecoder()
    if(!OggVorbisDecoder.library_loaded){
      Utils.messageDialog(this,Right(R.string.cannot_load_vorbis_library), _=> {finish()})
      return
    }
    if(Utils.showYomiInfo && Globals.prefs.get.getBoolean("hardware_accelerate",true) && android.os.Build.VERSION.SDK_INT >= 11){
         getWindow.setFlags(
           WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
           WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
    }
    setContentView(R.layout.main)
    switchViewAndReloadHandler()
    this.setVolumeControlStream(AudioManager.STREAM_MUSIC)

    val actionbar = getSupportActionBar
    val actionview = getLayoutInflater.inflate(R.layout.actionbar_custom,null)
    actionbar.setCustomView(actionview)
    actionbar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,ActionBar.DISPLAY_SHOW_CUSTOM)
    actionview.findViewById(R.id.actionbar_blue_ring).setVisibility(View.INVISIBLE)
  }
  def showProgress(){
    val v = getSupportActionBar.getCustomView
    if(v!=null){
      val ring = v.findViewById(R.id.actionbar_blue_ring)
      if(ring!=null){
        val rotation = AnimationUtils.loadAnimation(getApplicationContext,R.anim.rotator)
        rotation.setInterpolator(new Interpolator(){
            override def getInterpolation(input:Float):Float={
              return (input*8.0f).toInt/8.0f
            }
          })
        ring.setVisibility(View.VISIBLE)
        ring.startAnimation(rotation)
      }
    }
  }
  def hideProgress(){
    val v = getSupportActionBar.getCustomView
    if(v!=null){
      val ring = v.findViewById(R.id.actionbar_blue_ring)
      if(ring!=null){
        ring.clearAnimation()
        ring.setVisibility(View.INVISIBLE)
      }
    }
  }
  def invalidateYomiInfo(){
    if(!Utils.showYomiInfo){
      return
    }
    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yomi_info != null){
      yomi_info.invalidateAndScroll()
    }
  }
  def scrollYomiInfo(id:Int,smooth:Boolean){
    if(!Utils.showYomiInfo){
      return
    }
    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yomi_info != null){
      yomi_info.scrollToView(id,smooth)
    }

  }
  override def onStart(){
    super.onStart()
    if( Globals.prefs.isEmpty ){
      // onCreate returned before loading preference
      return
    }
    if(Globals.prefs.get.getBoolean("silent_mode_on_start",false)){
      val am = getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
      if(am != null && am.getRingerMode() != AudioManager.RINGER_MODE_SILENT){
        ringer_mode_bkup = Some(am.getRingerMode())
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT)
      }
    }
  }
  override def onResume(){
    super.onResume()
    if( Globals.prefs.isEmpty ){
      // onCreate returned before loading preference
      return
    }
    if(Globals.forceRestart){
      Globals.forceRestart = false
      finish
      startActivity(getIntent)
    }
    restartRefreshTimer()
    cancelAllPlay()
    Globals.player.foreach{ p =>
      // When screen is rotated, the activity is destroyed and new one is created.
      // Therefore, we have to reset the KarutaPlayer's activity
      p.activity = this
    }
    refreshAndSetButton()
    invalidateYomiInfo()
    startDimLockTimer()
    setLongClickButtonOnResume()
  }
  override def onPause(){
    super.onPause()
    release_lock.foreach(_())
    release_lock = None
    timer_refresh_text.foreach(_.cancel())
    timer_refresh_text = None
  }
  override def onStop(){
    super.onStop()
    ringer_mode_bkup.foreach{ mode =>
      val am = getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
      if(am != null){
        am.setRingerMode(mode)
      }
    }
    ringer_mode_bkup = None
  }
  override def onDestroy(){
    Utils.deleteCache(getApplicationContext(),_=>true)
    super.onDestroy()
  }
  def startDimLockTimer(){
    Globals.global_lock.synchronized{
      release_lock.foreach(_())
      getWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      release_lock = {
          Some( _ => {
            runOnUiThread(new Runnable{
                override def run(){
                  getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            })
          })
      }
      rescheduleDimLockTimer()
    }
  }

  def rescheduleDimLockTimer(millisec:Option[Long]=None){
    val DEFAULT_DIMLOCK_MINUTES = 5
    Globals.global_lock.synchronized{
      timer_dimlock.foreach(_.cancel())
      timer_dimlock = None
      var dimlock_millisec = millisec.getOrElse({
        MINUTE_MILLISEC * Utils.getPrefAs[Long]("dimlock_minutes", DEFAULT_DIMLOCK_MINUTES, 9999)
      })
      // if dimlock_millisec overflows then set default value
      if(dimlock_millisec < 0){
        dimlock_millisec = DEFAULT_DIMLOCK_MINUTES * MINUTE_MILLISEC
      }
      timer_dimlock = Some(new Timer())
      timer_dimlock.get.schedule(new TimerTask(){
        override def run(){
          release_lock.foreach(_())
          release_lock = None
        }
      },dimlock_millisec)
    }
  }

  // As for API >= 18, SCREEN_ORIENTATION_LOCKED has been introduced so we use it.
  // However, as for API <= 17, there seems no simple way to lock the orientation of screen programmatically to `current` orientation.
  // Moreover, the actual orientation of Surface.ROTATION_* and ActivityInfo.SCREEN_ORIENTATION_* differs in each devices.
  // The following code is taken from  http://stackoverflow.com/questions/6599770/screen-orientation-lock and seems to work on most devices.
  def lockOrientation(lock:Boolean){
    if(!lock){
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    //}else if(Build.VERSION.SDK_INT >= 18){
    //  setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
    }else{
      val under_9 = Build.VERSION.SDK_INT < 9
      val rotation = (if(under_9){Surface.ROTATION_0}else{getWindowManager.getDefaultDisplay.getRotation})
      val try_lock = { natural:Int =>
        val flag = (under_9 || Array(Surface.ROTATION_0,natural).contains(rotation))
        val ori = (getResources.getConfiguration.orientation match{
          case Configuration.ORIENTATION_PORTRAIT =>
            if(flag){
              ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }else{
              ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            }
          case Configuration.ORIENTATION_LANDSCAPE =>
            if(flag){
              ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }else{
              ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
          case _ =>
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        })
        setRequestedOrientation(ori)
      }
      try_lock(Surface.ROTATION_270)
      // Ensure that the rotation hasn't changed
      if(getWindowManager.getDefaultDisplay.getRotation != rotation){
        try_lock(Surface.ROTATION_90)
      }
    }
  }

  def setLongClickButtonOnResume(){
    for(id <- Array(R.id.read_button_small,R.id.read_button_large)){
      val btn = findViewById(id).asInstanceOf[Button]
      btn.setOnLongClickListener(
        if(Globals.prefs.get.getBoolean("skip_on_longclick",false)){
          new View.OnLongClickListener(){
            override def onLongClick(v:View):Boolean = {
              Globals.global_lock.synchronized{
                if(Globals.is_playing){
                  Globals.player.foreach{p=>
                    p.stop()
                    moveToNextFuda()
                    doPlay(false)
                  }
                }
              }
              return true
            }
          }
        }else{
          null
        }
      )
    }
  }
}

trait MainButtonTrait{
  self:WasuramotiActivity =>
  def onMainButtonClick(v:View) {
    doPlay(false)
  }
  def moveToNextFuda(){
    val is_shuffle = ("SHUFFLE" == Globals.prefs.get.getString("read_order",null))
    if(is_shuffle){
      FudaListHelper.moveNext(self.getApplicationContext())
    }
    // In random mode, there is a possibility that same pairs of fuda are read in a row.
    // In that case, if we do not show "now loading" message, the user can know that same pairs are read.
    // Therefore we give force flag to true for refreshAndSetButton.
    self.refreshAndSetButton(!is_shuffle)
    if(Utils.readCurNext(self.getApplicationContext)){
      runOnUiThread(new Runnable(){
        override def run(){
          invalidateYomiInfo()
        }
      })
    }
  }
  def doPlay(auto_play:Boolean){
    Globals.global_lock.synchronized{
      if(Globals.player.isEmpty){
        if(FudaListHelper.allReadDone(self.getApplicationContext())){
          Utils.messageDialog(self,Right(R.string.all_read_done),{_=>openOptionsMenu()})
        }else{
          Utils.messageDialog(self,Right(R.string.reader_not_found))
        }
        return
      }
      val player = Globals.player.get
      timer_autoread.foreach(_.cancel())
      timer_autoread = None

      if(Globals.is_playing){
        player.stop()
        Utils.setButtonTextByState(self.getApplicationContext())
      }else{
        // TODO: if auto_play then turn off display
        if(!auto_play){
          startDimLockTimer()
        }
        val after:Option[Unit=>Unit] = if(Utils.showYomiInfo && Utils.readCurNext(self.getApplicationContext)){
          Some(Unit => {
              runOnUiThread(new Runnable(){
                  override def run(){
                    scrollYomiInfo(R.id.yomi_info_view_next,true)
                  }
                })
            })
        }else{
          None
        }

        player.play(
          _ => {
            moveToNextFuda()
            val auto = Globals.prefs.get.getBoolean("autoplay_enable",false)
            if(auto && Globals.player.isEmpty && Globals.prefs.get.getBoolean("autoplay_repeat",false) &&
              FudaListHelper.allReadDone(self.getApplicationContext())
            ){
              FudaListHelper.shuffle(getApplicationContext())
              FudaListHelper.moveToFirst(getApplicationContext())
              refreshAndSetButton()
              runOnUiThread(new Runnable(){
                override def run(){
                  invalidateYomiInfo()
                }
              })
            }
            if(auto && !Globals.player.isEmpty){
              timer_autoread = Some(new Timer())
              timer_autoread.get.schedule(new TimerTask(){
                override def run(){
                  runOnUiThread(new Runnable(){
                    override def run(){doPlay(true)}
                  })
                  timer_autoread.foreach(_.cancel())
                  timer_autoread = None
                }},(Globals.prefs.get.getLong("autoplay_span", 5)*1000.0).toLong
              )
            }
        },after)
      }
    }
  }
}

trait ActivityDebugTrait{
  self:WasuramotiActivity =>
  def showBottomInfo(key:String,value:String){
    if(Globals.IS_DEBUG){
      // Since button in the bottom is deleted, the following code does not work anymore
      // TODO: show following `txt` in somewhere else
      //val btn = findViewById(R.id.read_button_bottom).asInstanceOf[Button]
      //var found = false
      //val txt = (btn.getText.toString.split(";").map{_.split("=")}.collect{
      //  case Array(k,v)=>(k,v)
      //}.toMap + ((key,value))).collect{case (k,v)=>k+"="+v}.mkString(";")
      //btn.setText(txt)
    }
  }
  def showAudioLength(len:Long){
    if(Globals.IS_DEBUG){
      showBottomInfo("len",len.toString)
    }
  }
}

trait WasuramotiBaseTrait {
  self:Activity =>
  override def onOptionsItemSelected(item: MenuItem):Boolean = {
    item.getItemId match {
      case android.R.id.home => {
        self.finish()
      }
      case _ => {}
    }
    return true
  }
}
