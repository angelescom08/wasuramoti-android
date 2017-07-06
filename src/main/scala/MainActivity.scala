package karuta.hpnpwd.wasuramoti

import android.app.{Activity,AlertDialog}
import android.content.{Intent,IntentFilter,Context,DialogInterface,BroadcastReceiver}
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.{Bundle,Handler}
import android.support.v7.app.{AppCompatActivity,ActionBar}
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.{Base64,TypedValue}
import android.view.animation.{AnimationUtils,Interpolator}
import android.view.{View,Menu,MenuItem,WindowManager,ViewStub}
import android.widget.{ImageView,Button,RelativeLayout,TextView,LinearLayout,Toast}
import android.preference.PreferenceActivity

import org.json.{JSONTokener,JSONObject}

import scala.collection.mutable

class WasuramotiActivity extends AppCompatActivity with ActivityDebugTrait with MainButtonTrait with RequirePermissionTrait{
  val MINUTE_MILLISEC = 60000
  var haseo_count = 0
  var release_lock = None:Option[()=>Unit]
  var run_dimlock = None:Option[Runnable]
  var run_refresh_text = None:Option[Runnable]
  val handler = new Handler()
  var bar_poem_info_num = None:Option[Int]
  var broadcast_receiver = None:Option[BroadcastReceiver]

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
    val dataString = intent.getDataString
    // we don't need the current intent anymore so replace it with default intent so that getIntent returns plain intent.
    setIntent(new Intent(this,this.getClass))
    dataString.replaceFirst("wasuramoti://","").split("/")(0) match {
      case "fudaset" => importFudaset(dataString)
      case "from_oom" => Utils.messageDialog(this,Right(R.string.from_oom_message))
      case m => Utils.messageDialog(this,Left(s"'${m}' is not correct intent data for ACTION_VIEW for wasuramoti"))
    }
  }

  def reloadFragment(){
    val fragment = WasuramotiFragment.newInstance(true)
    getSupportFragmentManager.beginTransaction.replace(R.id.activity_placeholder, fragment).commit
  }

  def importFudaset(dataString:String){
    try{
      val data = dataString.split("/").last
      val bytes = Base64.decode(data,Base64.URL_SAFE)
      val str = new String(bytes,"UTF-8")
      val obj = new JSONTokener(str).nextValue.asInstanceOf[JSONObject]
      val title = obj.keys.next.asInstanceOf[String]
      val ar = obj.getJSONArray(title)
      Utils.confirmDialog(this,Left(getResources.getString(R.string.confirm_action_view_fudaset,title,new java.lang.Integer(ar.length))),{ () =>
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
              Utils.writeFudaSetToDB(this,name,kimari,st_size)
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
      if(NotifyTimerUtils.notify_timers.nonEmpty){
        run_refresh_text = Some(new Runnable(){
          override def run(){
            if(NotifyTimerUtils.notify_timers.isEmpty){
              run_refresh_text.foreach(handler.removeCallbacks(_))
              run_refresh_text = None
              return
            }
            setButtonTextByState()
            run_refresh_text.foreach{handler.postDelayed(_,MINUTE_MILLISEC)}
          }
        })
        run_refresh_text.foreach{_.run()}
      }
    }
  }

  def setButtonText(txt:String){
     runOnUiThread(new Runnable(){
      override def run(){
        Option(findViewById(R.id.read_button).asInstanceOf[Button]).foreach{ read_button =>
          val lines = txt.split("\n")
          val max_chars = lines.map{x=>Utils.measureStringWidth(x)}.max
          if(YomiInfoUtils.showPoemText){
            val res_id = if(lines.length >= 4 || max_chars >= 18){
              R.dimen.read_button_text_small
            }else{
              R.dimen.read_button_text_normal
            }
            read_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(res_id))
          }
          read_button.setMinLines(lines.length)
          read_button.setMaxLines(lines.length+1) // We accept exceeding one row
          read_button.setText(txt)
        }
      }
    })
  }

  def setButtonTextByState(fromAuto:Boolean = false, invalidateQueryCacheExceptKarafuda:Boolean = false){
    val txt =
      if(NotifyTimerUtils.notify_timers.nonEmpty){
        NotifyTimerUtils.makeTimerText(getApplicationContext)
      }else if(Globals.is_playing && KarutaPlayUtils.have_to_mute){
        getResources.getString(R.string.muted_of_audio_focus)
      }else{
        if(invalidateQueryCacheExceptKarafuda){
          FudaListHelper.invalidateQueryCacheExceptKarafuda()
        }
        FudaListHelper.makeReadIndexMessage(getApplicationContext,fromAuto)
      }
    setButtonText(txt)
  }

  def refreshAndSetButton(force:Boolean = false, fromAuto:Boolean = false, nextRandom:Option[Int] = None){
    Globals.global_lock.synchronized{
      Globals.player = AudioHelper.refreshKarutaPlayer(this, Globals.player, force, fromAuto, nextRandom)
      setButtonTextByState(fromAuto)
    }
  }

  def refreshAndInvalidate(fromAuto:Boolean = false){
    refreshAndSetButton(fromAuto = fromAuto)
    invalidateYomiInfo()
  }

  override def onCreateOptionsMenu(menu: Menu):Boolean = {
    getMenuInflater.inflate(R.menu.main, menu)
    super.onCreateOptionsMenu(menu)
  }

  def showShuffleDialog(){
    Utils.confirmDialog(this,Right(R.string.menu_shuffle_confirm), ()=>{
        FudaListHelper.shuffleAndMoveToFirst(getApplicationContext())
        refreshAndInvalidate()
    })
  }

  override def onOptionsItemSelected(item: MenuItem):Boolean = {
    KarutaPlayUtils.cancelAllPlay()
    setButtonTextByState()
    item.getItemId match {
      case R.id.menu_shuffle => {
        showShuffleDialog()
      }
      case R.id.menu_move => {
        val dlg = new MovePositionDialog()
        dlg.show(getSupportFragmentManager,"move_position")
      }
      case R.id.menu_timer => startActivity(new Intent(this,classOf[NotifyTimerActivity]))
      case R.id.menu_quick_conf => {
        val dlg = new QuickConfigDialog()
        dlg.show(getSupportFragmentManager,"quick_config")
      }
      case R.id.menu_conf => startActivity(new Intent(this,classOf[ConfActivity]))
      case android.R.id.home => {
        // android.R.id.home will be returned when the Application Icon is clicked if we are using android.support.v7.app.ActionBarActivity
      }

      case _ => {}
    }
    return true
  }
  def haseoCounter(){
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

  def setCustomActionBar(){
    val actionbar = getSupportActionBar
    val actionview = getLayoutInflater.inflate(R.layout.actionbar_custom,null)
    val brc = actionview.findViewById(R.id.actionbar_blue_ring_container)
    if(brc != null){
      brc.setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
          haseoCounter()
        }
      })
    }
    actionbar.setCustomView(actionview)
    actionbar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,ActionBar.DISPLAY_SHOW_CUSTOM)

    val bar_container = actionview.findViewById(R.id.yomi_info_bar_container).asInstanceOf[ViewStub]
    val show_kimari  = Globals.prefs.get.getBoolean("yomi_info_show_bar_kimari",true)
    val show_poem_num  = Globals.prefs.get.getBoolean("yomi_info_show_bar_poem_num",true)
    if(bar_container != null &&
      YomiInfoUtils.showPoemText && (show_kimari || show_poem_num)
    ){
      val inflated = bar_container.inflate()
      Option(inflated.findViewById(R.id.command_button_kimariji_container)).foreach{v =>
        v.setVisibility(if(show_kimari){View.VISIBLE}else{View.GONE})
      }
      Option(inflated.findViewById(R.id.command_button_poem_num_container)).foreach{v =>
        v.setVisibility(if(show_poem_num){View.VISIBLE}else{View.GONE})
      }
      actionbar.setDisplayShowTitleEnabled(false)
      actionbar.setDisplayShowHomeEnabled(false)
    }else{
      actionbar.setDisplayShowTitleEnabled(true)
      actionbar.setDisplayShowHomeEnabled(true)
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext)

    if(Globals.prefs.get.getBoolean("light_theme", false)){
      setTheme(R.style.Wasuramoti_MainTheme_Light)
    }

    setContentView(R.layout.main_activity)
    
    // since onResume is always called after onCreate, we don't have to set have_to_resume_task = true
    val fragment = WasuramotiFragment.newInstance(false)

    getSupportFragmentManager.beginTransaction.replace(R.id.activity_placeholder, fragment).commit
    getSupportActionBar.setHomeButtonEnabled(true)
    if(Globals.IS_DEBUG){
      setTitle(getResources().getString(R.string.app_name) + " DEBUG")
      val layout = getWindow.getDecorView.findViewWithTag("main_linear_layout").asInstanceOf[LinearLayout]
      val view = new TextView(this)
      view.setTag("main_debug_info")
      view.setContentDescription("MainDebugInfo")
      layout.addView(view)
    }

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
    if(!YomiInfoUtils.showPoemText){
      return
    }
    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yomi_info != null){
      yomi_info.invalidateAndScroll()
    }
  }

  // There was user report that "Poem text differs with actually played audio".
  // Therefore we periodically check whether poem text and audio queue are same,
  // and set poem text if differs.
  def checkConsistencyBetweenPoemTextAndAudio(){
    Globals.player.foreach{ player =>
      val aq = player.audio_queue.collect{ case Left(w) => Some(w.num) }.flatten.distinct.toList
      if(aq.isEmpty){
        return
      }
      val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
      if(yomi_info == null){
        return
      }
      val sx = yomi_info.getScrollX
      val cur_view = Array(R.id.yomi_info_view_cur,R.id.yomi_info_view_next,R.id.yomi_info_view_prev).view
        .flatMap{ x => Option(yomi_info.findViewById(x).asInstanceOf[YomiInfoView]) }
        .find( _.getLeft == sx ) // only get current displayed YomiInfoView, which scroll ended
      if(cur_view.isEmpty){
        return
      }
      lazy val next_view =
        yomi_info.getNextViewId(cur_view.get.getId).flatMap{
          vid => Option(yomi_info.findViewById(vid).asInstanceOf[YomiInfoView])
        }
      val vq = List(cur_view.flatMap{_.rendered_num},
        if(aq.length > 1){
          next_view.flatMap{_.rendered_num}
        }else{
          None
        }
      ).flatten
      if(vq != aq){
        aq.zip(List(cur_view,next_view).flatten).foreach{ case (num,vw) =>
          vw.updateCurNum(Some(num))
          vw.invalidate()
        }
      }
      if(bar_poem_info_num.exists(_ != aq.head)){
        cur_view.foreach{ c =>
          c.updateCurNum(Some(aq.head))
          updatePoemInfo(c.getId)
        }
      }
    }
  }

  def updatePoemInfo(cur_view:Int){
    val yomi_cur = findViewById(cur_view).asInstanceOf[YomiInfoView]
    if(yomi_cur != null){
      val fudanum = yomi_cur.cur_num
      bar_poem_info_num = fudanum
      for(
           main <- Option(getSupportFragmentManager.findFragmentById(R.id.activity_placeholder).asInstanceOf[WasuramotiFragment]);
           yomi_dlg <- Option(main.getChildFragmentManager.findFragmentById(R.id.command_button_fragment).asInstanceOf[CommandButtonPanel])
       ){
             yomi_dlg.setFudanum(fudanum)
       }
      val cv = getSupportActionBar.getCustomView
      if(cv != null){
        val show_kimari  = Globals.prefs.get.getBoolean("yomi_info_show_bar_kimari",true)
        val show_poem_num  = Globals.prefs.get.getBoolean("yomi_info_show_bar_poem_num",true)
        if(show_kimari || show_poem_num){
          val (fudanum_s,kimari_s) = CommandButtonPanel.getFudaNumAndKimari(this,fudanum)
          if(show_poem_num){
            Option(cv.findViewById(R.id.command_button_poem_num).asInstanceOf[TextView]).foreach{ tv =>
              tv.setText(fudanum_s)
            }
          }
          if(show_kimari){
            Option(cv.findViewById(R.id.command_button_kimariji).asInstanceOf[TextView]).foreach{ tv =>
              tv.setText(kimari_s)
            }
          }
        }
      }
    }
  }

  def getCurNumInView():Option[Int] = {
    Option(findViewById(R.id.yomi_info_view_cur).asInstanceOf[YomiInfoView]).flatMap{_.cur_num}
  }

  def scrollYomiInfo(id:Int,smooth:Boolean,do_after_done:Option[()=>Unit]=None){
    if(!YomiInfoUtils.showPoemText){
      return
    }
    val yomi_info = findViewById(R.id.yomi_info).asInstanceOf[YomiInfoLayout]
    if(yomi_info != null){
      yomi_info.scrollToView(id,smooth,false,do_after_done)
    }
  }

  override def onStart(){
    super.onStart()
    if( Globals.prefs.isEmpty ){
      // onCreate returned before loading preference
      return
    }
  }

  // code which have to be done:
  // (a) after reloadFragment() or in onResume() ... put it inside WasuramotiActivity.doWhenResume()
  // (b) after reloadFragment() or after onCreate() ... put it inside WasuramotiFragment.onViewCreated()
  // (c) only in onResume() ... put it inside WasuramotiActivity.onResume()
  def doWhenResume(){
    Globals.global_lock.synchronized{
      if(Globals.forceRefresh){
        KarutaPlayUtils.replay_audio_queue = None
      }
      if(Globals.player.isEmpty || Globals.forceRefresh){
        if(! Utils.readFirstFuda && FudaListHelper.getCurrentIndex(this) <=0 ){
          FudaListHelper.moveToFirst(this)
        }
        Globals.player = AudioHelper.refreshKarutaPlayer(this,Globals.player,false)
      }
    }
    setButtonTextByState()
    if(Globals.player.forall(!_.is_replay)){
      invalidateYomiInfo()
    }else{
      Globals.player.foreach{ p =>
        KarutaPlayUtils.replay_audio_queue.foreach{ q =>
          p.forceYomiInfoView(q)
        }
      }
    }
    this.setVolumeControlStream(Utils.getAudioStreamType)
    KarutaPlayUtils.setReplayButtonEnabled(this,
      if(Globals.is_playing){
        Some(false)
      }else{
        None
      }
    )
    KarutaPlayUtils.setSkipButtonEnabled(this)
    KarutaPlayUtils.setRewindButtonEnabled(this)
  }

  def genBroadcastReceiver():BroadcastReceiver = {
    new BroadcastReceiver(){
        override def onReceive(c:Context,i:Intent){
          setButtonTextByState()
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
      reloadFragment()
    }else{
      doWhenResume()
    }
    Globals.global_lock.synchronized{
      Globals.player.foreach{ p =>
        // When screen is rotated, the activity is destroyed and new one is created.
        // Therefore, we have to reset the KarutaPlayer's activity
        p.activity = this
      }
    }
    handleActionView()
    restartRefreshTimer()
    startDimLockTimer()
    Globals.prefs.foreach{ p =>
      if(!p.contains("intended_use") && getSupportFragmentManager.findFragmentByTag("intended_use_dialog") == null){
        IntendedUseDialog.newInstance(true).show(getSupportFragmentManager,"intended_use_dialog")
      }
    }
    if(NotifyTimerUtils.notify_timers.nonEmpty){
      val brc = genBroadcastReceiver
      registerReceiver(brc,new IntentFilter(Globals.ACTION_NOTIFY_TIMER_FIRED))
      broadcast_receiver = Some(brc)
    }
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
    // without this, window leak occurs when rotating the device when dialog is shown.
    Utils.dismissAlertDialog()
    broadcast_receiver.foreach{ brc =>
      unregisterReceiver(brc)
      broadcast_receiver = None
    }
  }
  override def onStop(){
    super.onStop()
    Utils.cleanProvidedFile(this,false)
  }

  // don't forget that this method may be called when device is rotated
  // also not that this is not called when app is terminated by user using task manager.
  // See:
  //   http://stackoverflow.com/questions/4449955/activity-ondestroy-never-called
  //   https://developer.android.com/reference/android/app/Activity.html#onDestroy%28%29
  override def onDestroy(){
    super.onDestroy()
    Utils.deleteCache(getApplicationContext,_=>true)
  }
  def startDimLockTimer(){
    Globals.global_lock.synchronized{
      release_lock.foreach(_())
      getWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      release_lock = {
          Some( () => {
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

}

trait MainButtonTrait{
  self:WasuramotiActivity =>
  def onMainButtonClick(v:View) {
    doPlay(from_main_button=true)
  }
  def moveToNextFuda(showToast:Boolean = true, fromAuto:Boolean = false){
    val is_shuffle = ! Utils.isRandom
    if(is_shuffle){
      FudaListHelper.moveNext(self.getApplicationContext())
    }
    // In random mode, there is a possibility that same pairs of fuda are read in a row.
    // In that case, if we do not show "now loading" message, the user can know that same pairs are read.
    // Therefore we give force flag to true for refreshAndSetButton.
    self.refreshAndSetButton(!is_shuffle,fromAuto)
    if(Utils.readCurNext(self.getApplicationContext)){
      invalidateYomiInfo()
    }
    if(showToast && Globals.prefs.get.getBoolean("show_message_when_moved",true)){
      Toast.makeText(getApplicationContext,R.string.message_when_moved,Toast.LENGTH_SHORT).show()
    }
  }

  def doPlay(auto_play:Boolean = false, from_main_button:Boolean = false, from_swipe:Boolean = false){
    Globals.global_lock.synchronized{
      if(Globals.player.isEmpty){
        if(Globals.prefs.get.getBoolean("memorization_mode",false) &&
          FudaListHelper.getOrQueryNumbersToRead() == 0){
          Utils.messageDialog(self,Right(R.string.all_memorized))
        }else if(FudaListHelper.allReadDone(self.getApplicationContext())){
          val custom = (builder:AlertDialog.Builder) => {
            builder.setNeutralButton(R.string.menu_shuffle, new DialogInterface.OnClickListener(){
              override def onClick(dialog:DialogInterface, which:Int){
                showShuffleDialog()
              }
            })
          }
          Utils.messageDialog(self,Right(R.string.all_read_done),custom=custom)
        }else if(
          Utils.isExternalReaderPath(Globals.prefs.get.getString("reader_path",null))
          && !checkRequestMarshmallowPermission(REQ_PERM_MAIN_ACTIVITY)){
          // do nothing since checkRequestMarshmallowPermission shows the dialog when permission denied
        }else if(Globals.player_none_reason.nonEmpty){
          Utils.messageDialog(self,Left(Globals.player_none_reason.get))
        }else{
          Utils.messageDialog(self,Right(R.string.player_none_reason_unknown))
        }
        return
      }
      val player = Globals.player.get
      KarutaPlayUtils.cancelAutoTimer()
      if(Globals.is_playing){
        val have_to_go_next = (
          from_main_button &&
          Globals.prefs.get.getBoolean("move_after_first_phrase",true) &&
          ! player.is_replay &&
          player.isAfterFirstPhrase
        )
        player.stop()
        if(have_to_go_next){
          moveToNextFuda()
        }else{
          setButtonTextByState()
        }
      }else{
        // TODO: if auto_play then turn off display
        if(!auto_play){
          startDimLockTimer()
        }
        val bundle = new Bundle()
        bundle.putBoolean("have_to_run_border",YomiInfoUtils.showPoemText && Utils.readCurNext(self.getApplicationContext))
        bundle.putString("fromSender",KarutaPlayUtils.SENDER_MAIN)
        player.play(bundle,auto_play,from_swipe)
      }
    }
  }
}

trait ActivityDebugTrait{
  self:WasuramotiActivity =>
  def showBottomInfo(key:String,value:String){
    if(Globals.IS_DEBUG){
      val btn = getWindow.getDecorView.findViewWithTag("main_debug_info").asInstanceOf[TextView]
      val txt = (btn.getText.toString.split(";").map{_.split("=")}.collect{
        case Array(k,v)=>(k,v)
      }.toMap + ((key,value))).collect{case (k,v)=>k+"="+v}.mkString(";")
      btn.setText(txt)
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

trait RequirePermissionTrait {
  self:Activity =>
  val REQ_PERM_MAIN_ACTIVITY = 1
  val REQ_PERM_PREFERENCE_SCAN = 2
  val REQ_PERM_PREFERENCE_CHOOSE_READER = 3

  // References:
  //   https://developer.android.com/training/permissions/requesting.html
  //   http://sys1yagi.hatenablog.com/entry/2015/11/07/185539
  //   http://quesera2.hatenablog.jp/entry/2016/04/29/165124
  //   http://stackoverflow.com/questions/30719047/android-m-check-runtime-permission-how-to-determine-if-the-user-checked-nev
  def checkRequestMarshmallowPermission(reqCode:Int):Boolean = {
    if(android.os.Build.VERSION.SDK_INT < 23){
      return true
    }
    val reqPerm = android.Manifest.permission.READ_EXTERNAL_STORAGE
    if(PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this,reqPerm)){
      val reqfunc = {()=>ActivityCompat.requestPermissions(this,Array(reqPerm),reqCode)}
      if(ActivityCompat.shouldShowRequestPermissionRationale(this,reqPerm)){
         // permission was previously denied, with never ask again turned off.
         Utils.messageDialog(this,Right(R.string.read_external_storage_permission_rationale),reqfunc)
      }else{
         // we previously never called requestPermission, or permission was denied with never ask again turned on.
         reqfunc()
      }
      return false
    }else{
      return true
    }
  }

  override def onRequestPermissionsResult(requestCode:Int, permissions:Array[String], grantResults:Array[Int]){
    if(!Seq(REQ_PERM_MAIN_ACTIVITY,REQ_PERM_PREFERENCE_SCAN,REQ_PERM_PREFERENCE_CHOOSE_READER).contains(requestCode)){
      return
    }
    val (deniedMessage,deniedForeverMessage,grantedAction) = requestCode match {
      case REQ_PERM_MAIN_ACTIVITY =>
        (R.string.read_external_storage_permission_denied, R.string.read_external_storage_permission_denied_forever,
          ()=>{
            Globals.player = AudioHelper.refreshKarutaPlayer(self.asInstanceOf[WasuramotiActivity], Globals.player, true)
          })
      case REQ_PERM_PREFERENCE_SCAN | REQ_PERM_PREFERENCE_CHOOSE_READER =>
        (R.string.read_external_storage_permission_denied_scan, R.string.read_external_storage_permission_denied_forever_scan,
          ()=>{
            val pref = self.asInstanceOf[PreferenceActivity].findPreference("reader_path")
            if(pref != null){
              pref.asInstanceOf[ReaderListPreference].showDialogPublic(requestCode == REQ_PERM_PREFERENCE_SCAN)
            }
          })
    }

    val reqPerm = android.Manifest.permission.READ_EXTERNAL_STORAGE
    for((perm,grant) <- permissions.zip(grantResults)){
      if(perm == reqPerm){
        if(grant == PackageManager.PERMISSION_GRANTED){
          grantedAction()
        }else{
          if(ActivityCompat.shouldShowRequestPermissionRationale(this,reqPerm)){
            // permission is denied for first time, or denied with never ask again turned off
            Utils.messageDialog(this,Right(deniedMessage))
          }else{
            // permission is denied, with never ask again turned on
            Utils.confirmDialog(this,Right(deniedForeverMessage),()=>{
              val intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
              val uri = Uri.fromParts("package", getPackageName(), null)
              intent.setData(uri)
              startActivity(intent)
            })
          }
        }
      }
    }
  }
}
