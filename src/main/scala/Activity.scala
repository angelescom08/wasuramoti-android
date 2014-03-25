package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Activity,AlertDialog}
import _root_.android.media.AudioManager
import _root_.android.content.{Intent,Context}
import _root_.android.util.{Base64,TypedValue}
import _root_.android.os.{Bundle,Handler,Build}
import _root_.android.view.{View,Menu,MenuItem,WindowManager,ViewStub}
import _root_.android.view.animation.{AnimationUtils,Interpolator}
import _root_.android.widget.{ImageView,Button,RelativeLayout,TextView,LinearLayout}
import _root_.android.support.v7.app.{ActionBarActivity,ActionBar}
import _root_.org.json.{JSONTokener,JSONObject,JSONArray}
import _root_.java.lang.Runnable
import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import scala.collection.mutable


class WasuramotiActivity extends ActionBarActivity with MainButtonTrait with ActivityDebugTrait{
  val MINUTE_MILLISEC = 60000
  var haseo_count = 0
  var release_lock = None:Option[Unit=>Unit]
  var run_dimlock = None:Option[Runnable]
  var run_refresh_text = None:Option[Runnable]
  var ringer_mode_bkup = None:Option[Int]
  val handler = new Handler()

  override def onNewIntent(intent:Intent){
    super.onNewIntent(intent)
    // Since Android core system cannot determine whether or not the new intent is important for us,
    // We have to set intent by our own.
    // We can rely on fact that onResume() is called after onNewIntent()
    setIntent(intent)
  }
  def handleActionView(){
    val intent = getIntent
    // Android 2.x sends same intent at onResume() even after setIntent() is called if resumed from shown list where home button is long pressed.
    // Therefore we check FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY flag to distinguish it.
    if(intent == null ||
      intent.getAction != Intent.ACTION_VIEW || 
      (intent.getFlags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) > 0
    ){
      return
    }
    // we dont need the current intent anymore so replace it with empty one.
    setIntent(new Intent())

    if(android.os.Build.VERSION.SDK_INT < 8){
      // Base64 was added in API >= 8
      Utils.messageDialog(this,Left("Sorry. Importing group of poem sets is supported in Android >= 2.2"))
      return
    }
    try{
      val data = intent.getDataString.split("/").last
      val bytes = Base64.decode(data,Base64.URL_SAFE)
      val str = new String(bytes,"UTF-8")
      val obj = new JSONTokener(str).nextValue.asInstanceOf[JSONObject]
      val title = obj.keys.next.asInstanceOf[String]
      val ar = obj.getJSONArray(title)
      Utils.confirmDialog(this,Left(getResources.getString(R.string.confirm_action_view_fudaset,title,new java.lang.Integer(ar.length))),{ Unit =>
        var count = 0
        val res = (0 until ar.length).map{ i =>
          val o = ar.get(i).asInstanceOf[JSONObject]
          val name = o.keys.next.asInstanceOf[String]
          val n = BigInt(o.getString(name),36)
          val a = mutable.Buffer[Int]()
          for(j <- 0 until n.bitLength){
            if ( ((n >> j) & 1) == 1 ){
              a += j + 1
            }
          }
          val r = TrieUtils.makeKimarijiSetFromNumList(a.toList).exists{
            case (kimari,st_size) =>
              Utils.writeFudaSetToDB(name,kimari,st_size,true)
          }
          (if(r){count+=1;"[OK]"}else{"[NG]"}) + " " + name
        }
        val msg = getResources.getString(R.string.confirm_action_view_fudaset_done,new java.lang.Integer(count)) + 
        "\n" + res.mkString("\n")
        Utils.messageDialog(this,Left(msg))
      })
    }catch{
      case e:Exception => {
        val msg = getResources.getString(R.string.confirm_action_view_fudaset_fail) + "\n" + e.toString
        Utils.messageDialog(this,Left(msg))
      }
    }
  }

  def restartRefreshTimer(){
    Globals.global_lock.synchronized{
      run_refresh_text.foreach(handler.removeCallbacks(_))
      run_refresh_text = None
      if(!NotifyTimerUtils.notify_timers.isEmpty){
        run_refresh_text = Some(new Runnable(){
          override def run(){
            if(NotifyTimerUtils.notify_timers.isEmpty){
              run_refresh_text.foreach(handler.removeCallbacks(_))
              run_refresh_text = None
              return
            }
            Utils.setButtonTextByState(getApplicationContext())
            run_refresh_text.foreach{handler.postDelayed(_,MINUTE_MILLISEC)}
          }
        })
        run_refresh_text.foreach{_.run()}
      }
    }
  }

  def cancelAllPlay(){
    Globals.player.foreach(_.stop())
    KarutaPlayUtils.cancelKarutaPlayTimer(
      getApplicationContext,
      KarutaPlayUtils.Action.Auto
    )
  }

  def refreshAndSetButton(force:Boolean = false){
    Globals.global_lock.synchronized{
      Globals.player = AudioHelper.refreshKarutaPlayer(this,Globals.player,force)
      Utils.setButtonTextByState(getApplicationContext())
    }
  }

  def refreshAndInvalidate(){
    refreshAndSetButton()
    invalidateYomiInfo()
  }

  override def onCreateOptionsMenu(menu: Menu):Boolean = {
    val inflater = getMenuInflater
    inflater.inflate(R.menu.main, menu)
    super.onCreateOptionsMenu(menu)
  }
  override def onOptionsItemSelected(item: MenuItem):Boolean = {
    cancelAllPlay()
    Utils.setButtonTextByState(getApplicationContext())
    item.getItemId match {
      case R.id.menu_shuffle => {
        Utils.confirmDialog(this,Right(R.string.menu_shuffle_confirm),_=>{
          FudaListHelper.shuffle(getApplicationContext())
          FudaListHelper.moveToFirst(getApplicationContext())
          refreshAndInvalidate()
        })
      }
      case R.id.menu_move => {
        val dlg = new MovePositionDialog()
        dlg.show(getSupportFragmentManager,"move_position")
      }
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
    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]
    val stub = findViewById(R.id.yomi_info_stub).asInstanceOf[ViewStub]
    if(Utils.showYomiInfo){
      stub.inflate()
      read_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.read_button_text_small))
      read_button.setBackgroundResource(R.drawable.main_button)
    }else{
      // Android 2.1 does not ignore ViewStub's layout_weight
      val lp = stub.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
      lp.weight = 0.0f
      stub.setLayoutParams(lp)
    }

    val frag_stub = findViewById(R.id.yomi_info_search_stub).asInstanceOf[ViewStub]
    if(frag_stub != null && 
      Utils.showYomiInfo &&
      Globals.prefs.get.getBoolean("yomi_info_show_info_button",true) &&
      Utils.isScreenWide(this)
    ){
      frag_stub.inflate()
      val fragment = YomiInfoSearchDialog.newInstance(false,0)
      getSupportFragmentManager.beginTransaction.replace(R.id.yomi_info_search_fragment,fragment).commit
    }else if(frag_stub != null){
      // Android 2.1 does not ignore ViewStub's layout_weight
      val lp = frag_stub.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
      lp.weight = 0.0f
      frag_stub.setLayoutParams(lp)
    }

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

  def setCustomActionBar(){
    val actionbar = getSupportActionBar
    val actionview = getLayoutInflater.inflate(R.layout.actionbar_custom,null)
    actionbar.setCustomView(actionview)
    actionbar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,ActionBar.DISPLAY_SHOW_CUSTOM)

    val bar_kima = actionview.findViewById(R.id.yomi_info_bar_kimari_container).asInstanceOf[ViewStub]
    if(bar_kima != null &&
      Utils.showYomiInfo &&
      Globals.prefs.get.getBoolean("yomi_info_show_bar_kimari",true) &&
      Utils.isScreenWide(this)
    ){
      bar_kima.inflate()
      actionbar.setDisplayShowTitleEnabled(false)
    }
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
    setCustomActionBar()
    this.setVolumeControlStream(AudioManager.STREAM_MUSIC)

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

  def updatePoemInfo(cur_view:Int){
    val yomi_cur = findViewById(cur_view).asInstanceOf[YomiInfoView]
    if(yomi_cur != null){
      yomi_cur.cur_num.foreach{fudanum =>
        val yomi_dlg = getSupportFragmentManager.findFragmentById(R.id.yomi_info_search_fragment).asInstanceOf[YomiInfoSearchDialog]
        if(yomi_dlg != null && Globals.prefs.get.getBoolean("yomi_info_show_info_button",true)){
          yomi_dlg.setFudanum(fudanum)
        }
        val cv = getSupportActionBar.getCustomView
        if(cv!=null && Globals.prefs.get.getBoolean("yomi_info_show_bar_kimari",true)){
          val v_fn = cv.findViewById(R.id.yomi_info_search_poem_num).asInstanceOf[TextView]
          val v_kima = cv.findViewById(R.id.yomi_info_search_kimariji).asInstanceOf[TextView]
          if(v_fn != null && v_kima != null){
            val (fudanum_s,kimari) = YomiInfoSearchDialog.getFudaNumAndKimari(this,fudanum)
            v_fn.setText(fudanum_s)
            v_kima.setText(kimari)
          }
        }
      }
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
    Globals.global_lock.synchronized{
      Globals.player.foreach{ p =>
        // When screen is rotated, the activity is destroyed and new one is created.
        // Therefore, we have to reset the KarutaPlayer's activity
        p.activity = this
      }
      if(Globals.player.isEmpty || Globals.forceRefresh){
        Globals.player = AudioHelper.refreshKarutaPlayer(this,Globals.player,false)
      }
      Utils.setButtonTextByState(getApplicationContext())
    }
    invalidateYomiInfo()
    startDimLockTimer()
    setLongClickButtonOnResume()
    setLongClickYomiInfoOnResume()
    handleActionView()
  }
  override def onPause(){
    super.onPause()
    release_lock.foreach(_())
    release_lock = None
    run_dimlock.foreach(handler.removeCallbacks(_))
    run_dimlock = None
    run_refresh_text.foreach(handler.removeCallbacks(_))
    run_refresh_text = None
    // Since android:configChanges="orientation" is not set to WasuramotiActivity,
    // we have to close the dialog at onPause() to avoid window leak.
    Utils.dismissAlertDialog()
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
            getWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          })
      }
      rescheduleDimLockTimer()
    }
  }

  def rescheduleDimLockTimer(millisec:Option[Long]=None){
    val DEFAULT_DIMLOCK_MINUTES = 5
    Globals.global_lock.synchronized{
      run_dimlock.foreach(handler.removeCallbacks(_))
      run_dimlock = None
      var dimlock_millisec = millisec.getOrElse({
        MINUTE_MILLISEC * Utils.getPrefAs[Long]("dimlock_minutes", DEFAULT_DIMLOCK_MINUTES, 9999)
      })
      // if dimlock_millisec overflows then set default value
      if(dimlock_millisec < 0){
        dimlock_millisec = DEFAULT_DIMLOCK_MINUTES * MINUTE_MILLISEC
      }
      run_dimlock = Some(new Runnable(){
        override def run(){
          release_lock.foreach(_())
          release_lock = None
        }
      })
      run_dimlock.foreach{handler.postDelayed(_,dimlock_millisec)}
    }
  }

  def setLongClickYomiInfoOnResume(){
    for(id <- Array(R.id.yomi_info_view_prev,R.id.yomi_info_view_cur,R.id.yomi_info_view_next)){
      val view = findViewById(id).asInstanceOf[YomiInfoView]
      if(view != null){
        view.setOnLongClickListener(
          new View.OnLongClickListener(){
            override def onLongClick(v:View):Boolean = {
              view.cur_num.foreach{num=>
                val dlg = YomiInfoSearchDialog.newInstance(true,view.cur_num.getOrElse(num))
                dlg.show(getSupportFragmentManager,"yomi_info_search")
              }
              return true
            }
          }
        )
      }
    }
  }
  def setLongClickButtonOnResume(){
    val btn = findViewById(R.id.read_button).asInstanceOf[Button]
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

trait MainButtonTrait{
  self:WasuramotiActivity =>
  def onMainButtonClick(v:View) {
    doPlay(false)
  }
  def moveToNextFuda(){
    val is_shuffle = ! Utils.isRandom
    if(is_shuffle){
      FudaListHelper.moveNext(self.getApplicationContext())
    }
    // In random mode, there is a possibility that same pairs of fuda are read in a row.
    // In that case, if we do not show "now loading" message, the user can know that same pairs are read.
    // Therefore we give force flag to true for refreshAndSetButton.
    self.refreshAndSetButton(!is_shuffle)
    if(Utils.readCurNext(self.getApplicationContext)){
      invalidateYomiInfo()
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
      KarutaPlayUtils.cancelKarutaPlayTimer(
        getApplicationContext,
        KarutaPlayUtils.Action.Auto
      )
      if(Globals.is_playing){
        player.stop()
        Utils.setButtonTextByState(self.getApplicationContext())
      }else{
        // TODO: if auto_play then turn off display
        if(!auto_play){
          startDimLockTimer()
        }
        val bundle = new Bundle()
        bundle.putBoolean("have_to_run_border",Utils.showYomiInfo && Utils.readCurNext(self.getApplicationContext))
        bundle.putSerializable("from",KarutaPlayUtils.Sender.Main)
        // Since we insert some silence at beginning of audio,
        // the actual wait_time should be shorter.
        val wait_time = Math.max(100,Utils.getPrefAs[Double]("wav_begin_read", 0.5, 9999.0)*1000.0 - Globals.HEAD_SILENCE_LENGTH)
        bundle.putLong("wait_time",wait_time.toLong)
        player.play(bundle)
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
