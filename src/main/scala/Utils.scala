package karuta.hpnpwd.wasuramoti

import android.app.{AlertDialog,AlarmManager,PendingIntent,Activity}
import android.content.res.{Configuration,Resources}
import android.content.{DialogInterface,Context,SharedPreferences,Intent,ContentValues}
import android.database.sqlite.SQLiteDatabase
import android.graphics.Paint
import android.media.{AudioTrack,AudioManager}
import android.net.Uri
import android.os.Environment
import android.preference.{DialogPreference,PreferenceManager}
import android.text.method.LinkMovementMethod
import android.text.{TextUtils,Html}
import android.util.Log
import android.view.{LayoutInflater,View,WindowManager,Surface}
import android.widget.{TextView,Button,ListView,ArrayAdapter,CheckBox,RadioGroup,RadioButton}

import java.io.File
import java.text.NumberFormat
import java.util.Locale

import karuta.hpnpwd.audio.OpenSLESPlayer

import scala.collection.mutable
import scala.io.Source

object Globals {
  val IS_DEBUG = false
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 4
  val PREFERENCE_VERSION = 7
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val CACHE_SUFFIX_OGG = "_copied.ogg"
  val READER_SCAN_DEPTH_MAX = 3
  val global_lock = new Object()
  val db_lock = new Object()
  val decode_lock = new Object()
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
  var setButtonText = None:Option[String=>Unit]
  var is_playing = false
  var forceRefresh = false
  var forceRestart = false
  var audio_volume_bkup = None:Option[Int]
  var audio_track_failed_count = 0
  // TODO: use DialogFragment instead of holding the global reference of AlertDialog and dismissing at onPause()
  var alert_dialog = None:Option[AlertDialog]

  var current_config_dialog = None:Option[DialogPreference]
}

object Utils {
  abstract class PrefAccept[T <% Ordered[T] ] {
    def from(s:String):T
    def >(a:T,b:T):Boolean = a > b
  }
  object PrefAccept{
    implicit val LongPrefAccept = new PrefAccept[Long] {
      def from(s : String) = s.toLong
    }
    implicit val DoublePrefAccept = new PrefAccept[Double] {
      def from(s : String) = s.toDouble
    }
  }

  def getPrefAs[T:PrefAccept](key:String,defValue:T,maxValue:T):T = {
    if(Globals.prefs.isEmpty){
      return defValue
    }
    val r = try{
      val v = Globals.prefs.get.getString(key,defValue.toString)
      implicitly[PrefAccept[T]].from(v)
    }catch{
      case _:NumberFormatException => defValue
    }
    if( implicitly[PrefAccept[T]].>(r,maxValue)  ){
      maxValue
    }else{
      r
    }
  }

  object ReadOrder extends Enumeration{
    type ReadOrder = Value
    val Shuffle, Random, PoemNum = Value
  }
  object YomiInfoLang extends Enumeration{
    type YomiInfoLang = Value
    val Japanese,Romaji,English = Value
  }

  // Caution: Never use StringOps.format since the output varies with locale.
  // Instead, use this function.
  def formatFloat(fmt:String, x:Float):String = {
    fmt.formatLocal(Locale.US, x) // some country uses comma instead of dot !
  }
  def parseFloat(s:String):Float = {
    // old version of wasuramoti forgot to use .formatLocal() ,
    // so there may be something like "0,12" in setting file
    try{
      val nf = NumberFormat.getInstance(Locale.US)
      nf.parse(s.replaceAll(",",".")).floatValue
    }catch{
      case e:java.text.ParseException => throw new NumberFormatException(e.getMessage) // same exception as .toFloat()
    }
  }


  type EqualizerSeq = Seq[Option[Float]]
  // Since every Activity has a possibility to be killed by android when it is background,
  // all the Activity in this application should call this method in onCreate()
  // Increment Globals.PREFERENCE_VERSION if you want to read again
  def initGlobals(app_context:Context) {
    Globals.global_lock.synchronized{
      if(Globals.database.isEmpty){
        Globals.database = Some(new DictionaryOpenHelper(app_context))
      }
      if(Globals.prefs.isEmpty){
        Globals.prefs = Some(PreferenceManager.getDefaultSharedPreferences(app_context))
      }
      AllFuda.init(app_context)
      val pref = Globals.prefs.get
      val prev_version = pref.getInt("preference_version",0)
      if(prev_version < Globals.PREFERENCE_VERSION){
        PreferenceManager.setDefaultValues(app_context,R.xml.conf,true)
        val edit = pref.edit
        if(prev_version <= 2){
          val roe = pref.getString("read_order_each","CUR2_NEXT1")
          val new_roe = roe match {
            case "NEXT1_NEXT2" => "CUR1_CUR2"
            case "NEXT1_NEXT2_NEXT2" => "CUR1_CUR2_CUR2"
            case _ => roe
          }
          if(roe != new_roe){
            edit.putString("read_order_each",new_roe)
          }
        }
        if(prev_version <= 4){
          val english_mode = ! pref.getBoolean("yomi_info_default_lang_is_jpn",true)
          if(english_mode){
            edit.putString("yomi_info_default_lang",YomiInfoLang.English.toString)
          }
        }

        if(prev_version > 0 && prev_version < 5){
          edit.putString("intended_use","competitive")
        }

        if(prev_version > 0 && prev_version < 6){
          val show_yomi_info = pref.getString("show_yomi_info","None").split(";")
          val yomi_info_show_text = show_yomi_info.head != "None"
          val poem_text_font = {
            val ar = show_yomi_info.filter{ _ != "None" }
            if(ar.isEmpty){
              YomiInfoUtils.DEFAULT_FONT
            }else{
              ar.head
            }
          }
          edit.putBoolean("yomi_info_show_text",yomi_info_show_text)
          edit.putString("yomi_info_japanese_font", poem_text_font)
          if(pref.getString("yomi_info_furigana_font","None") == "None"){
            edit.putString("yomi_info_furigana_font", poem_text_font)
          }
          if(pref.getString("yomi_info_torifuda_font","None") == "None"){
            edit.putString("yomi_info_torifuda_font", poem_text_font)
          }
          edit.remove("show_yomi_info")
        }
        if(prev_version > 0 && prev_version < 7){
          if(pref.getString("read_order_joka","upper_1,lower_1") == "upper_0,lower_0"){
            edit.putBoolean("joka_enable",false)
            edit.putString("read_order_joka","upper_1,lower_1")
          }
        }

        // remove obsolete preferences
        edit.remove("wav_threashold") // misspelled
        edit.remove("hardware_accelerate") // always true
        edit.remove("yomi_info_furigana_size") // migrated to yomi_info_furigana_width
        edit.remove("audio_track_mode") // always STATIC
        
        edit.putInt("preference_version",Globals.PREFERENCE_VERSION)
        edit.commit()
      }
      NotifyTimerUtils.loadTimers()
      ReaderList.setDefaultReader(app_context)
    }
  }

  def readStream(is:java.io.InputStream):String = {
    val s = new java.util.Scanner(is,"UTF-8").useDelimiter("\\A")
    if(s.hasNext){s.next}else{""}
  }

  def setStatusBarForLolipop(activity:Activity){
    // TODO: As for AppCompat >= 21, the correct way to change color of status bar seems to be setting colorPrimaryDark.
    //  https://chris.banes.me/2014/10/17/appcompat-v21/
    //  https://developer.android.com/training/material/theme.html
    //  http://stackoverflow.com/questions/27093287/how-to-change-status-bar-color-to-match-app-in-lollipop-android
    //  http://stackoverflow.com/questions/26702000/change-status-bar-color-with-appcompat-actionbaractivity
    //  http://stackoverflow.com/questions/22192291/how-to-change-the-status-bar-color-in-android
    if(android.os.Build.VERSION.SDK_INT >= 21){
      val window = activity.getWindow
      window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
      window.setStatusBarColor(android.graphics.Color.DKGRAY)
    }
  }

  def dpToPx(dp:Float):Float = {
   (dp * Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  def pxToDp(px:Float):Float = {
    (px / Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  def isRandom():Boolean = {
    getReadOrder == ReadOrder.Random
  }
  def disableKimarijiLog():Boolean = {
    isRandom || Globals.prefs.get.getBoolean("memorization_mode",false)
  }

  def getReadOrder():ReadOrder.ReadOrder = {
    Globals.prefs.get.getString("read_order",null) match {
      case "RANDOM" => ReadOrder.Random
      case "POEM_NUM" => ReadOrder.PoemNum
      case _ => ReadOrder.Shuffle
    }
  }

  def isScreenWide(context:Context):Boolean = {
    isScreenLarge(context) || (isScreenNormal(context) && isLandscape(context))
  }

  def getScreenLayout(context:Context):Int = {
    context.getResources.getConfiguration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK
  }

  def isScreenNormal(context:Context):Boolean = {
    Configuration.SCREENLAYOUT_SIZE_NORMAL == getScreenLayout(context)
  }
  def isScreenLarge(context:Context):Boolean = {
    Array(Configuration.SCREENLAYOUT_SIZE_LARGE,Configuration.SCREENLAYOUT_SIZE_XLARGE) contains
    getScreenLayout(context)
  }
  def isLandscape(context:Context):Boolean = {
    if(android.os.Build.VERSION.SDK_INT >= 8){
      val display = context.getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager].getDefaultDisplay
      Array(Surface.ROTATION_90,Surface.ROTATION_270).contains(display.getRotation)
    }else{
      context.getResources.getConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
  }

  def readCurNext(context:Context):Boolean = {
    val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
    val is_joka = FudaListHelper.getCurrentIndex(context)  == 0 && readJoka
    (is_joka || roe.contains("CUR")) && roe.contains("NEXT")
  }
  def readJoka():Boolean = {
    Globals.prefs.get.getBoolean("joka_enable",true)
  }
  def readFirstFuda():Boolean = {
    val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
    roe.contains("NEXT") || readJoka
  }

  def incTotalRead():Int = {
    val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
    if(roe.contains("CUR") && roe.contains("NEXT")){
     0
    }else{
     1
    }
  }
  def makeDisplayedNum(index:Int,total:Int):(Int,Int) = {
    val dx = if(Utils.readFirstFuda){ 0 } else { 1 }
    (index-dx,total-dx)
  }

  def findAncestorViewById(v:View,id:Int):Option[View] = {
    var cur = v
    while( cur != null && cur.getId != id ){
      cur = cur.getParent.asInstanceOf[View]
    }
    Option(cur)
  }

  def withTransaction(db:SQLiteDatabase,func:()=>Unit){
    db.beginTransaction()
    func()
    db.setTransactionSuccessful()
    db.endTransaction
  }

  // save dialog instance to variable so that it can be dismissed by dismissAlertDialog()
  def showDialogAndSetGlobalRef(dialog:AlertDialog, func_done:()=>Unit = {()=>Unit}){
    // AlaertDialog.Builder.setOnDismissListener() was added on API >= 17 so we use Dialog.setOnDismissListener() instead
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
        override def onDismiss(interface:DialogInterface){
          func_done()
          Globals.alert_dialog = None
        }
      })
    dialog.show()
    Globals.alert_dialog = Some(dialog)
  }

  def getStringOrResource(context:Context,arg:Either[String,Int]):Option[String] = {
    Option(arg).map{
      case Left(x) => x
      case Right(x) => context.getResources.getString(x)
    }
  }

  def confirmDialog(
    context:Context,
    arg:Either[String,Int],
    func_yes:()=>Unit,
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
  ){
    val builder = custom(new AlertDialog.Builder(context))
    // TODO: can't we use setMessage(Int) instead of context.getResources().getString() ?
    getStringOrResource(context,arg).foreach{builder.setMessage(_)}
    val dialog:AlertDialog = builder
    .setPositiveButton(android.R.string.yes,new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_yes()
        }
      })
    .setNegativeButton(android.R.string.no,null)
    .create
    showDialogAndSetGlobalRef(dialog)
  }
  def messageDialog(
    context:Context,
    arg:Either[String,Int],
    func_done:()=>Unit = {()=>Unit},
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
  ){
    val builder = custom(new AlertDialog.Builder(context))
    getStringOrResource(context,arg).foreach{builder.setMessage(_)}
    val dialog = builder
      .setPositiveButton(android.R.string.ok,null)
      .create
    showDialogAndSetGlobalRef(dialog, func_done)
  }

  def generalHtmlDialog(
    context:Context,
    arg:Either[String,Int],
    func_done:()=>Unit={()=>Unit},
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
  ){
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val html = getStringOrResource(context,arg).getOrElse("")
    val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
    txtview.setText(Html.fromHtml(html))

    // this makes "<a href='...'></a>" clickable
    txtview.setMovementMethod(LinkMovementMethod.getInstance)

    val dialog = custom(new AlertDialog.Builder(context))
      .setPositiveButton(android.R.string.ok,null)
      .setView(view)
      .create
    showDialogAndSetGlobalRef(dialog, func_done)
  }

  def generalCheckBoxConfirmDialog(
    context:Context,
    arg_text:Either[String,Int],
    arg_checkbox:Either[String,Int],
    func_yes:(CheckBox)=>Unit,
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
    ){
      val view = LayoutInflater.from(context).inflate(R.layout.general_checkbox_dialog,null)
      val vtext = view.findViewById(R.id.checkbox_dialog_text).asInstanceOf[TextView]
      val vcheckbox = view.findViewById(R.id.checkbox_dialog_checkbox).asInstanceOf[CheckBox]
      getStringOrResource(context,arg_text).foreach(vtext.setText(_))
      getStringOrResource(context,arg_checkbox).foreach(vcheckbox.setText(_))
      val dialog = custom(new AlertDialog.Builder(context))
        .setPositiveButton(android.R.string.ok,
          new DialogInterface.OnClickListener(){
            override def onClick(interface:DialogInterface,which:Int){
              func_yes(vcheckbox)
            }
        })
        .setNegativeButton(android.R.string.no,null)
        .setView(view)
        .create
      showDialogAndSetGlobalRef(dialog)
  }

  def listDialog(
    context:Context,
    title_id:Int,
    items_id:Int,
    funcs:Array[()=>Unit]){
      val items = context.getResources().getStringArray(items_id)
      val builder = new AlertDialog.Builder(context)
      builder.setTitle(title_id)
      builder.setItems(items.map{_.asInstanceOf[CharSequence]},new DialogInterface.OnClickListener(){
          override def onClick(d:DialogInterface,position:Int){
            if(position > funcs.length){
              return
            }
            funcs(position)()
            d.dismiss()
          }
        })
      builder.setNegativeButton(R.string.button_cancel,new DialogInterface.OnClickListener(){
          override def onClick(d:DialogInterface,position:Int){
            d.dismiss()
          }
        })
      showDialogAndSetGlobalRef(builder.create)
  }

  def dismissAlertDialog(){
    Globals.alert_dialog.foreach{_.dismiss}
    Globals.alert_dialog = None
  }
  // Getting internal and external storage path is a big mess in android.
  // Android's Environment.getExternalStorageDirectory does not actually return external SD card's path.
  // Instead, it returns internal storage path for most devices.
  // Thus we have to explore where the mount point of SD card is by our own.
  // There are several ways to do this , and there seems no best way
  // (1) Read environment variables such SECONDARY_STORAGE -> not useful since the name of variable varies between devices.
  // (2) Parse /system/etc/vold.fstab -> cannot use since Android >= 4.3 because it is removed.
  // (3) Parse /proc/mounts and find /dev/block/vold/* or vfat -> works until Android <= 4.4 (?)
  // see following for more info:
  //   http://source.android.com/devices/tech/storage/
  //   http://stackoverflow.com/questions/5694933/find-an-external-sd-card-location
  //   http://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
  //   https://code.google.com/p/wagic/source/browse/trunk/projects/mtg/Android/src/net/wagic/utils/StorageOptions.java
  // We use third method for Android < 4.0 (see following Updated)
  // [Updated]
  // As for Android >= 4.4 (?) it seems that parsing /proc/mounts does not work anymore since row containing vfat returns invalid path.
  // Another option I found is using StorageManager.getVolumeList, which is hidden API.
  // You can access hidden API using reflection. see following URL.
  //   http://qiita.com/aMasatoYui/items/e13664455af45123a66e
  //   http://buchi.hatenablog.com/entry/2014/10/28/000842
  //
  // This API seems to added since Android >= 4.0 (or 3.2.4 ?) and works valid until at least Android 5.0.2
  // see
  //   core/java/android/os/storage/StorageManager.java
  //    in https://github.com/android/platform_frameworks_base.git
  def getAllExternalStorageDirectories(context:Context):Set[File] = {
    val ret = mutable.Set[File]()
    val state = Environment.getExternalStorageState
    if(state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY){
      ret += Environment.getExternalStorageDirectory
      Log.d("wasuramoti",s"Environment.getExternalStorageDirectory=${Environment.getExternalStorageDirectory}")
    }
    if(android.os.Build.VERSION.SDK_INT >= 14){
      try{
        val sm = context.getSystemService(Context.STORAGE_SERVICE)
        val getVolumeList = sm.getClass.getDeclaredMethod("getVolumeList")
        val volumeList = getVolumeList.invoke(sm).asInstanceOf[Array[java.lang.Object]]
        for(volume <- volumeList){
          val getPath = volume.getClass.getDeclaredMethod("getPath")
          val isRemovable = volume.getClass.getDeclaredMethod("isRemovable")
          val path = getPath.invoke(volume).asInstanceOf[String]
          val removable = isRemovable.invoke(volume).asInstanceOf[Boolean]
          Log.d("wasuramoti",s"StorageManager.getVolumeList,path=${path},removable=${removable}")
          // we search both removable and non-removable path
          if(path != null){
            ret += new File(path)
          }
        }
      }catch{
        case e:Exception => {
          Log.v("wasuramoti","getAllExternalStorageDirectories",e)
        }
      }
    }else{
      try{
        for(line <- Source.fromFile("/proc/mounts").getLines){
          val buf = line.split(" ")
          // currently we assume that SD card is vfat
          if(buf.length >= 3 && buf(2) == "vfat"){
            Log.d("wasuramoti",s"proc_mounts=${buf(1)}")
            ret += new File(buf(1))
          }
        }
      }catch{
        case _:Exception => None
      }
    }
    ret.toSet
  }

  def getAllExternalStorageDirectoriesWithUserCustom(context:Context):Set[File] = {
    val dirs = mutable.Set(getAllExternalStorageDirectories(context).toArray:_*)
    val user_path = Globals.prefs.get.getString("scan_reader_additional","")
    if(!TextUtils.isEmpty(user_path)){
      dirs += new File(user_path).getCanonicalFile
    }
    dirs.toSet[File]
  }

  def walkDir(cur:File,depth:Int,func:(File)=>Unit){
    // Checking whether File object is not null is usually not required.
    // However I will check it just for sure.
    if(depth == 0 || cur == null){
      return
    }
    val files = cur.listFiles()
    if(files == null){
      // There seems some directory which File.isDirectory is `true',
      // but File.listFiles returns `null'.
      return
    }
    for( f <- files ){
      if( f != null ){
        func(f)
        if( f.isDirectory ){
          walkDir(f,depth - 1,func)
        }
      }
    }
  }
  def setButtonTextByState(context:Context, fromAuto:Boolean = false, invalidateQueryCacheExceptKarafuda:Boolean = false){
    Globals.setButtonText.foreach{
      _(
        if(NotifyTimerUtils.notify_timers.nonEmpty){
          NotifyTimerUtils.makeTimerText(context)
        }else{
          val res = context.getResources
          if(invalidateQueryCacheExceptKarafuda){
            FudaListHelper.invalidateQueryCacheExceptKarafuda()
          }
          FudaListHelper.makeReadIndexMessage(context) + "\n" +
          (
            if(Globals.is_playing){
              if(Globals.prefs.get.getBoolean("autoplay_enable",false)){
                res.getString(R.string.now_auto_playing)
              }else{
                res.getString(R.string.now_playing)
              }
            }else{
              if(fromAuto && Globals.player.nonEmpty){
                val sec = Globals.prefs.get.getLong("autoplay_span",5).toInt
                res.getString(R.string.now_stopped_auto,new java.lang.Integer(sec))
              }else{
                res.getString(R.string.now_stopped)
              }
            }
          )
        }
      )
    }
  }

  def deleteCache(context:Context,match_func:String=>Boolean){
    if(android.os.Build.VERSION.SDK_INT >= 9){
      // context.getCacheDir is only used in Asset.withAssetOrFile
      return
    }
    this.synchronized{
      val files = context.getCacheDir().listFiles()
      if(files != null){
        for(f <- files){
          if(match_func(f.getAbsolutePath())){
            try{
              f.delete()
            }catch{
              case _:Exception => None
            }
          }
        }
      }
    }
  }

  def saveAndSetAudioVolume(context:Context){
    readPrefAudioVolume.foreach{ pref_volume =>
      val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
      if(am != null){
        val max_volume = am.getStreamMaxVolume(Utils.getAudioStreamType)
        val new_volume = math.min((pref_volume*max_volume).toInt,max_volume)
        Globals.audio_volume_bkup = Some(am.getStreamVolume(Utils.getAudioStreamType))
        am.setStreamVolume(Utils.getAudioStreamType,new_volume,0)
      }
    }
  }

  def readPrefAudioVolume():Option[Float] = {
    val pref_audio_volume = Globals.prefs.get.getString("audio_volume","")
    if(TextUtils.isEmpty(pref_audio_volume)){
      None
    }else{
      Some(Utils.parseFloat(pref_audio_volume))
    }
  }

  def restoreAudioVolume(context:Context){
    Globals.audio_volume_bkup.foreach{ volume =>
        val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
        if(am != null){
          am.setStreamVolume(Utils.getAudioStreamType,volume,0)
        }
    }
    Globals.audio_volume_bkup = None
  }

  def getPrefsEqualizer():EqualizerSeq = {
    val str = Globals.prefs.get.getString("effect_equalizer_seq","")
    if(TextUtils.isEmpty(str)){
      Seq()
    }else{
      str.split(",").map(x =>
        if( x == "None" ){
          None
        }else{
          try{
            Some(Utils.parseFloat(x))
          }catch{
            case e:NumberFormatException => None
          }
        })
    }
  }
  def equalizerToString(eq:EqualizerSeq):String = {
    eq.map(_ match{
        case None => "None"
        case Some(x) => formatFloat("%.3f", x)
      }).mkString(",")
  }

  def setUnderline(view:TextView){
    view.setPaintFlags(view.getPaintFlags | Paint.UNDERLINE_TEXT_FLAG)
  }

  def restartActivity(activity:Activity){
    activity.finish
    try{
      activity.startActivity(activity.getIntent)
    }catch{
      case _:android.content.ActivityNotFoundException =>
        // Some device might set the empty intent ?
        // This code is just for sure so you may remove it if you can convince that
        // all device's activity.getIntent returns valid intent
        activity.startActivity(new Intent(activity,activity.getClass))
    }
  }

  def restartApplication(context:Context,from_oom:Boolean=false){
    // This way totally exits application using System.exit()
    val start_activity = if(!from_oom){
      new Intent(context,classOf[WasuramotiActivity])
    }else{
      new Intent(Intent.ACTION_VIEW,Uri.parse("wasuramoti://from_oom"))
    }
    val pending_id = 271828
    val pending_intent = PendingIntent.getActivity(context, pending_id, start_activity, PendingIntent.FLAG_CANCEL_CURRENT)
    val mgr = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    if(mgr != null){
      // don't need to be setExact()
      mgr.set(AlarmManager.RTC, System.currentTimeMillis+100, pending_intent)
      System.exit(0)
    }
  }
  // return true if succeed
  def writeFudaSetToDB(title:String,kimari:String,st_size:Int,is_add:Boolean,orig_title:String=null):Boolean = Globals.db_lock.synchronized{
    val cv = new ContentValues()
    val db = Globals.database.get.getWritableDatabase
    cv.put("title",title)
    cv.put("body",kimari)
    cv.put("set_size",new java.lang.Integer(st_size))
    val r = if(is_add){
      db.insert(Globals.TABLE_FUDASETS,null,cv) != -1
    }else{
      db.update(Globals.TABLE_FUDASETS,cv,"title = ?",Array(orig_title)) > 0
    }
    db.close()
    return r
  }
  def getButtonDrawableId(yiv:Option[YomiInfoView],tag:String):Int = {
    val Array(prefix,postfix) = tag.split("_")
    val is_mem = prefix == YomiInfoSearchDialog.PREFIX_MEMORIZE
    val (ic_on,ic_off) = if(is_mem){
      (R.drawable.ic_action_important,R.drawable.ic_action_not_important)
    }else{
      (R.drawable.ic_action_brightness_high,R.drawable.ic_action_brightness_low)
    }
    yiv.flatMap{vw =>
      val rx = if(is_mem){
        vw.isMemorized
      }else{
        postfix match{
          case "AUTHOR" => vw.show_author
          case "KAMI" => vw.show_kami
          case "SIMO" => vw.show_simo
          case "FURIGANA" => vw.show_furigana
        }
      }
      if(rx){
        Some(ic_on)
      }else{
        None
      }
    }.getOrElse(ic_off)
  }
  def alarmManagerSetExact(manager:AlarmManager,typ:Int,triggerAtMillis:Long,operation:PendingIntent){
    if(android.os.Build.VERSION.SDK_INT >= 19){
      manager.setExact(typ,triggerAtMillis,operation)
    }else{
      manager.set(typ,triggerAtMillis,operation)
    }
  }
  def getAudioStreamType():Int = {
    import AudioManager._
    Globals.prefs.map{
      _.getString("audio_stream_type","MUSIC") match {
        case "MUSIC" => STREAM_MUSIC
        case "NOTIFICATION" => STREAM_NOTIFICATION
        case "ALARM" => STREAM_ALARM
        case "RING" => STREAM_RING
        case "VOICE_CALL" => STREAM_VOICE_CALL
        case "SYSTEM" => STREAM_SYSTEM
        case _ => STREAM_MUSIC
      }
    }.getOrElse(STREAM_MUSIC)
  }

  def getCheckedItemsFromListView[T](container:ListView):Seq[T] = {
    val poss = container.getCheckedItemPositions()
    val adapter = container.getAdapter().asInstanceOf[ArrayAdapter[T]]
    (0 until poss.size()).filter{poss.valueAt(_)}.map{ poss.keyAt(_) }.map{ adapter.getItem(_) }
  }

  def setVolume(track:AudioTrack,gain:Float){
    if(android.os.Build.VERSION.SDK_INT >= 21){
      track.setVolume(gain)
    }else{
      track.setStereoVolume(gain,gain)
    }
  }
  def setVolumeMute(track:Either[AudioTrack,OpenSLESTrack],is_mute:Boolean){
    track match {
      case Left(t) => setVolume(t, if(is_mute){0.0f}else{1.0f})
      case Right(_) => OpenSLESPlayer.slesMute(is_mute)
    }
  }
  def replayButtonText(res:Resources):String = {
    val nums = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1").filter{"12".contains(_)}.reverse.distinct
    val rid = nums match {
      case "12" => R.string.repeat_last_1st
      case "21" => R.string.repeat_last_2nd
      case _ => R.string.repeat_last_else
    }
    res.getString(R.string.repeat_button_format,res.getString(rid))
  }

  def setRadioTextClickListener(group:RadioGroup){
    var last_radio_button = None:Option[RadioButton]
    for(i <- 0 until group.getChildCount){
      group.getChildAt(i) match {
        case radio:RadioButton => last_radio_button = Some(radio)
        case view => {
          val tag = view.getTag
          if(Option(tag).exists(_.toString == "radio_text")){
            last_radio_button.foreach{ btn =>
              view.setOnClickListener(new View.OnClickListener(){
                override def onClick(v:View){
                  group.check(btn.getId)
                }
              })
            }
          }
        }
      }
    }
  }
}

class AlreadyReportedException(s:String) extends Exception(s){
}

trait CustomAlertDialogTrait{
  self:AlertDialog =>
  def doWhenClose(view:View)
  def setViewAndButton(view:View){
    self.setView(view)
    self.setButton(DialogInterface.BUTTON_POSITIVE,self.getContext.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        doWhenClose(view)
        dismiss()
      }
    })
    self.setButton(DialogInterface.BUTTON_NEGATIVE,self.getContext.getResources.getString(android.R.string.cancel),new DialogInterface.OnClickListener(){
      override def onClick(dialog:DialogInterface,which:Int){
        dismiss()
      }
    })
  }
}
