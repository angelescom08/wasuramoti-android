package karuta.hpnpwd.wasuramoti

import scala.io.Source
import _root_.android.app.{AlertDialog,AlarmManager,PendingIntent}
import _root_.android.util.TypedValue
import _root_.android.content.{DialogInterface,Context,SharedPreferences,Intent,ContentValues}
import _root_.android.content.res.{Configuration,Resources}
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.preference.{DialogPreference,PreferenceManager}
import _root_.android.text.{TextUtils,Html}
import _root_.android.text.method.LinkMovementMethod
import _root_.android.os.Environment
import _root_.android.media.AudioManager
import _root_.android.view.{LayoutInflater,View,WindowManager,Surface}
import _root_.android.widget.{TextView,Button}

import _root_.java.io.File

import scala.collection.mutable

object Globals {
  val IS_DEBUG = false
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 3
  val PREFERENCE_VERSION = 3
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val CACHE_SUFFIX_OGG = "_copied.ogg"
  val CACHE_SUFFIX_WAV = "_decoded.wav"
  val READER_SCAN_DEPTH_MAX = 3
  val global_lock = new Object()
  val db_lock = new Object()
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

  object ReadOrder extends Enumeration{
    type ReadOrder = Value
    val Shuffle, Random, PoemNum = Value
  }

  // return value is (silence, wait) in millisec
  def calcSilenceAndWaitLength() : (Int,Int) = {
    val SILENCE_MIN = 250 // in millisec
    val SILENCE_MAX = 5000 // in millisec, making this too big can consume much memory
    val WAIT_MIN = 50 // in millisec
    val total = (Utils.getPrefAs[Double]("wav_begin_read", 0.5, 9999.0)*1000.0).toInt
    val silence = Math.max(Math.min(total,SILENCE_MAX)-WAIT_MIN,SILENCE_MIN)
    val wait = Math.max(total-silence, WAIT_MIN)
    (silence,wait)
  }

  type EqualizerSeq = Seq[Option[Double]]
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
      val prev_version = Globals.prefs.get.getInt("preference_version",0)
      if(prev_version < Globals.PREFERENCE_VERSION){
        PreferenceManager.setDefaultValues(app_context,R.xml.conf,true)
        val edit = Globals.prefs.get.edit
        if(prev_version <= 2){
          val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
          val new_roe = roe match {
            case "NEXT1_NEXT2" => "CUR1_CUR2"
            case "NEXT1_NEXT2_NEXT2" => "CUR1_CUR2_CUR2"
            case _ => roe
          }
          if(roe != new_roe){
            edit.putString("read_order_each",new_roe)
          }
        }
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

  def dpToPx(dp:Float):Float = {
   (dp * Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  def pxToDp(px:Float):Float = {
    (px / Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  def isRandom():Boolean = {
    getReadOrder == ReadOrder.Random
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
    val roj = Globals.prefs.get.getString("read_order_joka","upper_1,lower_1")
    roj != "upper_0,lower_0" 
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
  def showDialogAndSetGlobalRef(dialog:AlertDialog){
    // AlaertDialog.Builder.setOnDismissListener() was added on API >= 17 so we use Dialog.setOnDismissListener() instead
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
        override def onDismiss(interface:DialogInterface){
          Globals.alert_dialog = None
        }
      })
    dialog.show()
    Globals.alert_dialog = Some(dialog)
  }
  def confirmDialog(context:Context,arg:Either[String,Int],func_yes:Unit=>Unit,func_no:Unit=>Unit=identity[Unit]){
    val builder = new AlertDialog.Builder(context)
    val str = arg match {
      case Left(x) => x
      case Right(x) => context.getResources().getString(x)
    }
    val dialog = builder.setMessage(str)
    .setPositiveButton(context.getResources.getString(android.R.string.yes),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_yes()
        }
      })
    .setNegativeButton(context.getResources.getString(android.R.string.no),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_no()
        }
      })
    .create
    showDialogAndSetGlobalRef(dialog)
  }
  def messageDialog(context:Context,arg:Either[String,Int],func_done:Unit=>Unit=identity[Unit]){
    val builder = new AlertDialog.Builder(context)
    val str = arg match {
      case Left(x) => x
      case Right(x) => context.getResources().getString(x)
    }
    val dialog = builder.setMessage(str)
    .setPositiveButton(context.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_done()
        }
      }).create
    showDialogAndSetGlobalRef(dialog)
  }

  def dismissAlertDialog(){
    Globals.alert_dialog.foreach{_.dismiss}
    Globals.alert_dialog = None
  }

  def generalHtmlDialog(context:Context,html_id:Either[String,Int],func_done:Unit=>Unit=identity[Unit]){
    val builder= new AlertDialog.Builder(context)
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val html = html_id match {
      case Left(txt) => txt
      case Right(id) => context.getResources.getString(id)
    }
    val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
    txtview.setText(Html.fromHtml(html))

    // this makes "<a href='...'></a>" clickable
    txtview.setMovementMethod(LinkMovementMethod.getInstance)
    
    builder.setView(view)
    builder.setPositiveButton(context.getResources.getString(android.R.string.ok), new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_done()
        }
      });
    builder.create.show()
  }
  // Android's Environment.getExternalStorageDirectory does not actually return external SD card's path.
  // Thus we have to explore where the mount point of SD card is by our own.
  // There are several ways to do this , and there seems no best way
  // (1) Read environment variables such SECONDARY_STORAGE -> not useful since the name of variable varies between devices.
  // (2) Parse /system/etc/vold.fstab -> cannot use since Android 4.3 because it is removed.
  // (3) Parse /proc/mounts and find /dev/block/vold/* or vfat -> maybe good.
  // We use third method
  // see following for more infos:
  //   http://source.android.com/devices/tech/storage/
  //   http://stackoverflow.com/questions/5694933/find-an-external-sd-card-location
  //   http://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
  //   https://code.google.com/p/wagic/source/browse/trunk/projects/mtg/Android/src/net/wagic/utils/StorageOptions.java
  def getAllExternalStorageDirectories():Set[File] = {
    val ret = mutable.Set[File]()
    val state = Environment.getExternalStorageState
    if(state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY){
      ret += Environment.getExternalStorageDirectory
    }
    try{
      for(line <- Source.fromFile("/proc/mounts").getLines){
        val buf = line.split(" ")
        // currently we assume that SD card is vfat
        if(buf.length >= 3 && buf(2) == "vfat"){
          ret += new File(buf(1))
        }
      }
    }catch{
      case _:java.io.FileNotFoundException => None
    }
    ret.toSet
  }

  def getAllExternalStorageDirectoriesWithUserCustom():Set[File] = {
    val dirs = mutable.Set(getAllExternalStorageDirectories.toArray:_*)
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
  def setButtonTextByState(context:Context){
    Globals.setButtonText.foreach{
      _(
      (if(!NotifyTimerUtils.notify_timers.isEmpty){
        NotifyTimerUtils.makeTimerText(context)
      }else{
        FudaListHelper.makeReadIndexMessage(context)
      })+"\n"+
      context.getResources.getString(
        if(Globals.is_playing){
          if(Globals.prefs.get.getBoolean("autoplay_enable",false)){
            R.string.now_auto_playing
          }else{
            R.string.now_playing
          }
        }else{
          R.string.now_stopped
        })
      )
    }
  }
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

  def deleteCache(context:Context,match_func:String=>Boolean){
    Globals.global_lock.synchronized{
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
    val pref_audio_volume = Globals.prefs.get.getString("audio_volume","")
    if(!TextUtils.isEmpty(pref_audio_volume)){
      val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
      if(am != null){
        val max_volume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val new_volume = math.min((pref_audio_volume.toFloat*max_volume).toInt,max_volume)
        Globals.audio_volume_bkup = Some(am.getStreamVolume(AudioManager.STREAM_MUSIC))
        am.setStreamVolume(AudioManager.STREAM_MUSIC,new_volume,0)
      }
    }
  }

  def restoreAudioVolume(context:Context){
    Globals.audio_volume_bkup.foreach{ volume =>
        val am = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
        if(am != null){
          am.setStreamVolume(AudioManager.STREAM_MUSIC,volume,0)
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
            Some(x.toDouble)
          }catch{
            case e:NumberFormatException => None
          }
        })
    }
  }
  def equalizerToString(eq:EqualizerSeq):String = {
    eq.map(_ match{
        case None => "None"
        case Some(x) => "%.3f" format x
      }).mkString(",")
  }
  def restartApplication(context:Context){
    // This way totally exits application using System.exit()
    val start_activity = new Intent(context,classOf[WasuramotiActivity])
    val pending_id = 271828
    val pending_intent = PendingIntent.getActivity(context, pending_id, start_activity, PendingIntent.FLAG_CANCEL_CURRENT)
    val mgr = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
    if(mgr != null){
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
}

class AlreadyReportedException(s:String) extends Exception(s){
}

