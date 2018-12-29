package karuta.hpnpwd.wasuramoti

import android.annotation.TargetApi
import android.app.{Activity,AlarmManager,PendingIntent}
import android.content.res.{Resources,Configuration}
import android.content.pm.PackageManager
import android.content.{Context,SharedPreferences,Intent,ContentValues}
import android.database.sqlite.SQLiteDatabase
import android.graphics.Paint
import android.media.{AudioTrack,AudioManager}
import android.net.Uri
import android.os.{Environment,Handler}
import android.support.v7.preference.PreferenceManager
import android.text.{TextUtils}
import android.util.{Log,TypedValue}
import android.view.{View,ContextThemeWrapper}
import android.widget.{TextView,ListView,ArrayAdapter}

import android.support.v4.content.FileProvider
import android.support.v4.app.{DialogFragment,FragmentManager}

import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.BitSet

import karuta.hpnpwd.audio.OpenSLESPlayer

import scala.collection.mutable
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Try,Random}

object Globals {
  val IS_DEBUG = false
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 6
  val PREFERENCE_VERSION = 9
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val CACHE_SUFFIX_OGG = "_copied.ogg"
  val READER_SCAN_DEPTH_MAX = 3
  val ACTION_NOTIFY_TIMER_FIRED = "karuta.hpnpwd.wasuramoti.notify.timer.fired"
  val global_lock = new Object()
  val db_lock = new Object()
  val decode_lock = new Object()
  val rand = new Random()
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
  var player_none_reason = None:Option[String]
  var is_playing = false
  var autoplay_started = None:Option[Long]
  var forceRestart = false
  var forceRefreshPlayer = false
  var forceReloadUI = false
  var audio_volume_bkup = None:Option[Int]
  var audio_track_failed_count = 0
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
    val Shuffle, Random, PoemNum, Musumefusahose = Value
  }
  object YomiInfoLang extends Enumeration{
    type YomiInfoLang = Value
    val Japanese,Romaji,English = Value
    def getDefaultLangFromPref(pref:SharedPreferences):YomiInfoLang = {
      Option(pref.getString("yomi_info_default_lang",null)).map{ p =>
        withName(p)
      }.getOrElse(Japanese)
    }
  }

  object OverrideLang extends Enumeration {
    type OverrideLang = Value
    val Device = Value("device")
    val Japanese = Value("ja")
    val English = Value("en")
  }
  def getOverrideContext(context:Context):Context = {
    if(android.os.Build.VERSION.SDK_INT < 17){
      return context
    }
    val lang = Globals.prefs.get.getString("override_language",OverrideLang.Device.toString)
    val system = context.getResources.getString(R.string.locale)
    if(lang == OverrideLang.Device.toString || lang == system){
      return context
    }
    val config = new Configuration(context.getResources.getConfiguration)
    config.setLocale(new Locale(lang))
    return context.createConfigurationContext(config)
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

  def throwawayRandom(){
    // Throw away some numbers to avoid unique sequence.
    // The effect is doubtful, it's just for conscience's sake.
    val count = 8 + (System.currentTimeMillis % 16)
    for(i <- 0 until count.toInt){
      Globals.rand.nextInt()
    }
  }

  type EqualizerSeq = Seq[Option[Float]]
  // Since every Activity has a possibility to be killed by android when it is background,
  // all the Activity in this application should call this method in onCreate()
  // Increment Globals.PREFERENCE_VERSION if you want to read again
  def initGlobals(app_context:Context) {
    Globals.global_lock.synchronized{
      Globals.rand.setSeed(System.currentTimeMillis)
      Utils.throwawayRandom()

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

        if(prev_version > 0 && prev_version < 9){
          if(pref.getBoolean("light_theme", false)){
            edit.putString("color_theme","white")
          }else{
            edit.putString("color_theme","black")
          }
          edit.remove("light_theme")
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

  def dpToPx(dp:Float):Float = {
   (dp * Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  def pxToDp(px:Float):Float = {
    (px / Resources.getSystem.getDisplayMetrics.density).toFloat
  }

  // getDimension() does not work for <item format="float" type="dimen"> so we use getValue() instead
  // reference: http://stackoverflow.com/questions/3282390/add-floating-point-value-to-android-resources-values
  def getDimenFloat(context:Context,resId:Int):Float={
    val outValue = new TypedValue
    context.getResources.getValue(resId,outValue,true)
    outValue.getFloat
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
      case "MUSUMEFUSAHOSE" => ReadOrder.Musumefusahose
      case _ => ReadOrder.Shuffle
    }
  }

  def readCurNext(context:Context):Boolean = {
    val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
    val is_joka = FudaListHelper.getCurrentIndex(context)  == 0 && readJoka
    (is_joka || roe.contains("CUR")) && roe.contains("NEXT")
  }
  def readJoka():Boolean = {
    Globals.prefs.exists{ pref =>
      // Since FudaListHelper.saveRestoreReadOrderJoka() may set read_order_joka to upper_0,lower_0,
      // we check both joka_enable and read_order_joka
      pref.getBoolean("joka_enable",true) && 
      pref.getString("read_order_joka","upper_1,lower_1") != "upper_0,lower_0"
    }
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
  def indexOffset():Int = {
    if(Utils.readFirstFuda){ 0 } else { 1 }
  }
  def makeDisplayedNum(index:Int,total:Int):(Int,Int) = {
    val dx = indexOffset()
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
    try{
      func()
      db.setTransactionSuccessful()
    }finally{
      db.endTransaction()
    }
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
  //   http://stackoverflow.com/questions/5694933/find-an-external-sd-card-location
  //   http://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
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
  //    in https://github.com/android/platform_frameworks_base 
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

  sealed trait ProvidedName { def template:String }
  case object ProvidedAnonymousForm extends ProvidedName { val template = "anonymous_form_%s.html" }
  case object ProvidedBugReport extends ProvidedName { val template = "bug_report_%s.gz" }

  def getProvidedName(name:ProvidedName):String = {
    val ds = if(HAVE_TO_GRANT_CONTENT_PERMISSION){
      // fix content url
      "wasuramoti"
    }else{
      (new java.text.SimpleDateFormat("yyyyMMdd")).format(new java.util.Date)
    }
    name.template.format(ds)
  }

  val FILE_PROVIDER_DIR = "provided"
  val FILE_PROVIDER_AUTHORITY = "karuta.hpnpwd.wasuramoti.fileprovider"
  val provided_uris = new mutable.Queue[Uri]()
  def getProvidedFile(context:Context,name:ProvidedName,createDir:Boolean):File = {
    val filename = getProvidedName(name)
    if(createDir){
      val dir = new File(context.getCacheDir,FILE_PROVIDER_DIR)
      if(!dir.exists){
        dir.mkdir()
      }
    }
    new File(context.getCacheDir,FILE_PROVIDER_DIR+"/"+filename)
  }

  def getProvidedUri(context:Context,file:File):Uri = {
    FileProvider.getUriForFile(context,FILE_PROVIDER_AUTHORITY,file)
  }

  def isRecentProvidedFile(file:File):Boolean = {
    Array(getProvidedName(ProvidedAnonymousForm),getProvidedName(ProvidedBugReport)).contains(file.getName)
  }

  def cleanProvidedFile(context:Context,all:Boolean){
    Try{
      val files = new File(context.getCacheDir,FILE_PROVIDER_DIR).listFiles
      for(f <- files if all || !isRecentProvidedFile(f) ){
        Try(f.delete())
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
      alarmManagerSetExact(mgr, AlarmManager.RTC, System.currentTimeMillis+100, pending_intent)
      System.exit(0)
    }
  }
  // return true if succeed
  def writeFudaSetToDB(context:Context,title:String,kimari:String,st_size:Int,orig_title:Option[String]=None,insertOrUpdate:Boolean=false):Boolean = Globals.db_lock.synchronized{
    val cv = new ContentValues()
    val db = Globals.database.get.getWritableDatabase
    cv.put("title",title)
    cv.put("body",kimari)
    cv.put("set_size",new java.lang.Integer(st_size))
    val r = orig_title match{
      case None =>
        // Insert
        val cursor = db.query(Globals.TABLE_FUDASETS,Array("ifnull(max(set_order),0)"),null,null,null,null,null,null)
        cursor.moveToFirst
        val max_order = cursor.getInt(0)
        cursor.close
        cv.put("set_order",new java.lang.Integer(max_order+1))
        if (!insertOrUpdate) {
          db.insert(Globals.TABLE_FUDASETS,null,cv) != -1
        } else {
          val id = db.insertWithOnConflict(Globals.TABLE_FUDASETS,null,cv,SQLiteDatabase.CONFLICT_IGNORE)
          if (id == -1){
            db.update(Globals.TABLE_FUDASETS,cv,"title = ?",Array(title)) > 0
          } else {
            true
          }
        }
      case Some(orig) =>
        // Update
        db.update(Globals.TABLE_FUDASETS,cv,"title = ?",Array(orig)) > 0
    }
    db.close()
    if(orig_title.exists{_==Globals.prefs.get.getString("fudaset",null)}){
      Globals.prefs.get.edit.putString("fudaset",title).commit
      FudaListHelper.updateSkipList(context)
    }
    return r
  }
  def getButtonDrawableId(yiv:Option[YomiInfoView],tag:String):Int = {
    // TODO: we should use Theme#resolveAttribute to get the drawable of theme instead of switching by isLight
    val isLight = ColorThemeHelper.isLight
    val Array(prefix,postfix) = tag.split("_")
    val is_mem = prefix == CommandButtonPanel.PREFIX_MEMORIZE
    val (ic_on,ic_off) = if(is_mem){
      if(isLight){
        (R.drawable.light_ic_action_important,R.drawable.light_ic_action_not_important)
      }else{
        (R.drawable.ic_action_important,R.drawable.ic_action_not_important)
      }
    }else{
      if(isLight){
        (R.drawable.light_ic_action_brightness_high,R.drawable.light_ic_action_brightness_low)
      }else{
        (R.drawable.ic_action_brightness_high,R.drawable.ic_action_brightness_low)
      }
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
  // Don't forget that Android >= 5.1 takes at least five seconds to trigger even using setExact
  //   https://issuetracker.google.com/code/p/android/issues/detail?id=161516
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
    val poss = container.getCheckedItemPositions
    val adapter = container.getAdapter().asInstanceOf[ArrayAdapter[T]]
    for(i <- 0 until adapter.getCount if poss.get(i,false))yield{
      adapter.getItem(i)
    }
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

  // see https://developer.android.com/training/multiple-threads/communicate-ui.html
  def runOnUiThread(context:Context,func:()=>Unit){
    new Handler(context.getMainLooper).post(new Runnable(){
      override def run(){
        func()
      }
    })
  }


  // When API < 16, setting provided content uri as EXTRA_STREAM gives us following exception
  //   java.lang.SecurityException: Permission Denial: opening provider android.support.v4.content.FileProvider from ProcessRecord{...} (pid=xxx, uid=yyy) requires null or null
  // To avoid this, have to call Context.grantUriPermission() to all the packages which has the possibility to be chosen from the user.
  // From API >= 16, the uri inside EXTRA_STREAM is automatically wrapped as ClipData, so we don't have to do that nasty thing.
  // However, managing grantUriPermission/revokeUriPermission correctly is hard task [*1], and the popularity of API < 16 is only 5.3%, we just fix the path of getProvidedUri
  // [*1] It is easy doing clean up in startActivityForResult(), however, onActivityResult() is not always called. e.g. user killed the app by task manager.
  //      Also, note that if we don't call revokeUriPermission, garbage residues in `adb shell dumpsys | grep UriPermission`
  // See:
  //   http://stackoverflow.com/questions/18249007/how-to-use-support-fileprovider-for-sharing-content-to-other-apps
  //   http://stackoverflow.com/questions/13435654/how-to-grant-temporary-access-to-custom-content-provider-using-flag-grant-read-u
  //   https://issuetracker.google.com/code/p/android/issues/detail?id=76683
  val HAVE_TO_GRANT_CONTENT_PERMISSION = android.os.Build.VERSION.SDK_INT < 16 
  def grantUriPermissionsForExtraStream(context:Context,intent:Intent,uri:Uri){
    for(res <- context.getPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).asScala){
      context.grantUriPermission(res.activityInfo.packageName,uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  @TargetApi(11)
  def copyToClipBoard(context:Context,label:String,text:String){
    if(android.os.Build.VERSION.SDK_INT >= 11){
      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
        .asInstanceOf[android.content.ClipboardManager]
      val data = android.content.ClipData.newPlainText(label,text)
      clip.setPrimaryClip(data)
    }else{
      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
        .asInstanceOf[android.text.ClipboardManager]
      clip.setText(text)
    }
  }

  @TargetApi(11)
  def copyFromClipBoard(context:Context):String = {
    if(android.os.Build.VERSION.SDK_INT >= 11){
      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
        .asInstanceOf[android.content.ClipboardManager]
      clip.getPrimaryClip().getItemAt(0).getText.toString
    }else{
      val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
        .asInstanceOf[android.text.ClipboardManager]
      clip.getText.toString
    }
  }



  def playAfterMove(wa:WasuramotiActivity){
    if(Globals.player.nonEmpty && Globals.prefs.get.getBoolean("play_after_swipe",false)){
      wa.doPlay(from_swipe=true)
    }
  }

  val PATTERN_JOKA_UPPER = "upper_(\\d+)".r
  val PATTERN_JOKA_LOWER = "lower_(\\d+)".r
  def parseReadOrderJoka():(Int,Int) = {
    val read_order_joka = Globals.prefs.get.getString("read_order_joka","upper_1,lower_1")
    var upper, lower = 1
    read_order_joka.split(",").foreach{x =>
      x match {
        case PATTERN_JOKA_UPPER(n) => upper = n.toInt
        case PATTERN_JOKA_LOWER(n) => lower = n.toInt
      }
    }
    (upper,lower)
  }

  def isExternalReaderPath(path:String):Boolean = {
    Option(path).exists(x => Seq("EXT","ABS").contains(x.split(":")(0)))
  }

  def measureStringWidth(s:String):Int = {
    val z = s.toCharArray
    z.zip(z.tail:+Char.MinValue).collect{
      case (hi,lo) if !Character.isLowSurrogate(hi)=>
        if(lo!=Char.MinValue && Character.isSurrogatePair(hi,lo)){
          Character.toCodePoint(hi,lo)
        }else{
          hi.toInt
        }
    }.map{ codePoint =>
      Try{
        Character.UnicodeBlock.of(codePoint) match {
          // HALFWIDTH_AND_FULLWIDTH_FORMS contains both fullwidth alphanumeric and halfwidth kana,
          // however, we treat both of of it has width = 2
          // https://en.wikipedia.org/wiki/Halfwidth_and_fullwidth_forms
          case Character.UnicodeBlock.BASIC_LATIN => 1
          case _ => 2
        }
      }.toOption.getOrElse(1)
    }.sum
  }
  def printStackTrace(){
    for(elem <- Thread.currentThread.getStackTrace){
      Log.d("wasuramoti_debug",elem.toString)
    }
  }
  def attrColor(context:Context, attr_id:Int):Int = {
    val typedValue = new TypedValue
    val resolved = context.getTheme.resolveAttribute(attr_id, typedValue, true)
    if(!resolved){
      val attr_name = Try(context.getResources.getResourceName(attr_id)).getOrElse("unknown")
      val theme_name = {
        val tv = new TypedValue
        val rs = context.getTheme.resolveAttribute(R.attr.themeName, tv, true)
        if(rs){tv.string}else{"unknown"}
      }
      throw new RuntimeException(s"attr='${attr_name}' not found in theme='${theme_name}'")
    }
    return typedValue.data
  }

  def colorToHex(color:Int):String = {
    // remove alpha channel when color is ARGB
    return "%06x".format(0xffffff & color)
  }

  lazy val colorPattern = """color=['"]?\?attr/(\w+)['"]?""".r
  def htmlAttrFormatter(context:Context,html:String):String = {
    return colorPattern.replaceAllIn(html, _ match { case(m) =>
      Try(attrColor(context,context.getResources.getIdentifier(m.group(1),"attr", context.getPackageName))).map{ color =>
        s"color='#${colorToHex(color)}'"
      }.getOrElse("")
    })
  }

  def showDialogOrFallbackToStateless(manager:FragmentManager,dialog:DialogFragment,tag:String){
    try{
      dialog.show(manager,tag)
    }catch{
      case _:IllegalStateException =>
        // I could not find out how this exception occurs, but it is sometimes reported from user.
        // In that case, I will retry to show the dialog allowing state loss
        // https://medium.com/inloop/demystifying-androids-commitallowingstateloss-cb9011a544cc
        val ft = manager.beginTransaction
        ft.add(dialog,tag)
        ft.commitAllowingStateLoss()
    }
  }
  def getColorOfTheme(context:Context,themeId:Int,colorId:Int):Int = {
    val typed = new TypedValue
    val theme = new ContextThemeWrapper(context, themeId).getTheme
    theme.resolveAttribute(colorId, typed, true)
    return typed.data
  }
  def kimarijiToHtml(context:Context,fudanum:Int,color:Boolean):String = {
    val (kimari_all,kimari_cur,kimari_in_fudaset) = FudaListHelper.getKimarijis(fudanum)
    val k_b = kimari_all.substring(kimari_cur.length,kimari_in_fudaset.length)
    val k_c = kimari_all.substring(kimari_in_fudaset.length)
    if(color){
      // TODO: cache these values
      val COLOR_1 = Utils.colorToHex((Utils.attrColor(context,R.attr.kimarijiPrimaryColor)))
      val COLOR_2 = Utils.colorToHex((Utils.attrColor(context,R.attr.kimarijiSecondaryColor)))
      val COLOR_3 = Utils.colorToHex((Utils.attrColor(context,R.attr.kimarijiTertiaryColor)))
      s"""<font color="#$COLOR_1">$kimari_cur</font><font color="#$COLOR_2">$k_b</font><font color="#$COLOR_3">$k_c</font>"""
    }else{
      kimari_cur + (if(!TextUtils.isEmpty(k_b)){
        " / " + k_b
      }else{""}) + (if(!TextUtils.isEmpty(k_c)) {
        " (" + k_c + ")"
      }else{""})
    }
  }
}

class AlreadyReportedException(s:String) extends Exception(s){
}
