package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,AlertDialog}
import _root_.android.media.AudioManager
import _root_.android.content.{Intent,Context}
import _root_.android.os.{Bundle,Handler,Parcelable}
import _root_.android.view.{View,Menu,MenuItem,WindowManager}
import _root_.android.widget.{ImageView,Button,RelativeLayout,ViewFlipper}
import _root_.android.support.v7.app.ActionBarActivity
import _root_.java.lang.Runnable
import _root_.java.util.{Timer,TimerTask}
import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import scala.collection.mutable


class WasuramotiActivity extends ActionBarActivity with MainButtonTrait with ActivityDebugTrait{
  val MINUTE_MILLISEC = 60000
  val ACTIVITY_REQUEST_NOTIFY_TIMER = 1
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
      if(!Globals.notify_timers.isEmpty){
        timer_refresh_text = Some(new Timer())
        timer_refresh_text.get.schedule(new TimerTask(){
          override def run(){
            if(Globals.notify_timers.isEmpty){
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
    Globals.player.foreach(_.stop())
    timer_autoread.foreach(_.cancel())
    timer_autoread = None
    Utils.setButtonTextByState(getApplicationContext())
    item.getItemId match {
      case R.id.menu_shuffle => {
        Utils.confirmDialog(this,Right(R.string.menu_shuffle_confirm),_=>{
          FudaListHelper.shuffle(getApplicationContext())
          FudaListHelper.moveToFirst(getApplicationContext())
          Globals.play_log.clear()
          refreshAndSetButton()
          invalidateYomiInfo(Some(View.FOCUS_LEFT))
        })
      }
      case R.id.menu_move => new MovePositionDialog(this,
        _=>{
          Globals.play_log.clear()
          refreshAndSetButton()
          invalidateYomiInfo(Some(View.FOCUS_LEFT))
        }
        ).show
      case R.id.menu_timer => startActivityForResult(new Intent(this,classOf[NotifyTimerActivity]),ACTIVITY_REQUEST_NOTIFY_TIMER)
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
            //       Is there any way to do the follwing two lines in one row ?
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
    if(savedInstanceState != null && savedInstanceState.containsKey("notify_timers_keys")){
      var values = savedInstanceState.getParcelableArray("notify_timers_values").map{_.asInstanceOf[Intent]}
      var keys = savedInstanceState.getIntArray("notify_timers_keys")
      Globals.notify_timers.clear()
      Globals.notify_timers ++= keys.zip(values)
      // Since there is a possibility that Globals.notify_timers are updated during NotifyTimerReceiver.onReceive,
      // we should remove the timers in the past.
      // However if we can update the savedInstanceState value which is passed to onCreate
      // in the NotifyTimerReceiver.onReceive, it is a much more general way.
      // Therefore the following code is a tentative method.
      // TODO: Find the way to update the value of savedInstanceState in NotifyTimerReceiver.onReceive and remove the following code.
      Globals.notify_timers.retain{ (k,v) => v.getExtras.getLong("limit_millis") > System.currentTimeMillis() }
    }
    this.setVolumeControlStream(AudioManager.STREAM_MUSIC)
  }
  override def onSaveInstanceState(instanceState: Bundle){
    // Sending HashMap in Bundle across the Activity using Serializable does not work.
    // This is maybe because launchMode is singleTask or singleInstance.
    // Thus we send keys and values separately as IntArray and ParcelableArray.
    // TODO: implement Parcelable HashMap and send the following data in one row.
    super.onSaveInstanceState(instanceState)
    instanceState.putIntArray("notify_timers_keys",Globals.notify_timers.keys.toArray[Int])
    instanceState.putParcelableArray("notify_timers_values",Globals.notify_timers.values.toArray[Parcelable])
  }
  override def onActivityResult(requestCode:Int,resultCode:Int,data:Intent){
    if(requestCode == ACTIVITY_REQUEST_NOTIFY_TIMER && resultCode == Activity.RESULT_OK && data != null){
      val keys = data.getIntArrayExtra("notify_timers_keys")
      val values = data.getParcelableArrayExtra("notify_timers_values").map{_.asInstanceOf[Intent]}
      Globals.notify_timers.clear()
      Globals.notify_timers ++= keys.zip(values)
    }else{
      super.onActivityResult(requestCode,resultCode,data)
    }
  }
  def invalidateYomiInfo(scroll:Option[Int]=Some(View.FOCUS_RIGHT),have_to_hide:Boolean=false){
    if(!Utils.showYomiInfo){
      return
    }
    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yomi_info != null){
      yomi_info.invalidateAndScroll(scroll,have_to_hide)
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
    Globals.player.foreach{ p =>
      p.stop()
      // When screen is rotated, the activity is destroyed and new one is created.
      // Therefore, we have to reset the KarutaPlayer's activity
      p.activity = this
    }
    timer_autoread.foreach(_.cancel())
    timer_autoread = None
    refreshAndSetButton()
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
    //TODO: Implement the rest
    /*val scroll = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    val v = findViewById(R.id.yomi_info_view_cur).asInstanceOf[YomiInfoView]
    val is_shuffle = ("SHUFFLE" == Globals.prefs.get.getString("read_order",null))
    val player = Globals.player.get
    val readnum = if(Utils.readCurNext){player.cur_num}else{player.next_num}
    // The scroll is on the rightmost.
    if(v != null && scroll != null && !Globals.is_playing && 
      scroll.getWidth + scroll.getScrollX >= v.getRight*0.9 &&
      is_shuffle &&
      v.cur_num > 0 &&
      v.cur_num != readnum
    ){
      ... Go To Previous Fuda And Play
    }
    */
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
        val after:Option[Unit=>Unit] = if(Utils.showYomiInfo){
          Some(Unit => {
              runOnUiThread(new Runnable(){
                  override def run(){
                    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
                    if(yomi_info!=null){
                      yomi_info.scrollToNext() 
                    }
                  }
                })
            })
        }else{
          None
        }

        player.play(
          _ => {
            moveToNextFuda()
            if(!Globals.player.isEmpty && Globals.prefs.get.getBoolean("autoplay_enable",false)){
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
