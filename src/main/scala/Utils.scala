package karuta.hpnpwd.wasuramoti

import scala.io.Source
import _root_.android.app.{AlertDialog,AlarmManager,NotificationManager}
import _root_.android.content.{DialogInterface,Context,Intent,SharedPreferences}
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.preference.PreferenceManager
import _root_.android.text.{TextUtils,Html}
import _root_.android.os.{Environment,Handler}
import _root_.android.media.AudioManager
import _root_.android.view.{LayoutInflater,View}
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
  val PREFERENCE_VERSION = 2
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val CACHE_SUFFIX_OGG = "_copied.ogg"
  val CACHE_SUFFIX_WAV = "_decoded.wav"
  val HEAD_SILENCE_LENGTH = 200 // in milliseconds
  val READER_SCAN_DEPTH_MAX = 3
  val global_lock = new Object()
  val play_log = new mutable.ListBuffer[Int]()
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
  var setButtonText = None:Option[Either[String,Int]=>Unit]
  var is_playing = false
  var forceRefresh = false
  var forceRestart = false
  var audio_volume_bkup = None:Option[Int]
}

object Utils {
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
      if(Globals.prefs.get.getInt("preference_version",0) < Globals.PREFERENCE_VERSION){
        PreferenceManager.setDefaultValues(app_context,R.xml.conf,true)
        val edit = Globals.prefs.get.edit
        edit.putInt("preference_version",Globals.PREFERENCE_VERSION)
        edit.commit()
      }
      NotifyTimerUtils.loadTimers()
      ReaderList.setDefaultReader(app_context)
    }
  }

  def showYomiInfo():Boolean = {
    Globals.prefs.exists{_.getString("show_yomi_info","None") != "None"}
  }

  def readCurNext():Boolean = {
    Globals.player.exists{_.read_both}
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
  def confirmDialog(context:Context,arg:Either[String,Int],func_yes:Unit=>Unit,func_no:Unit=>Unit=identity[Unit]){
    val builder = new AlertDialog.Builder(context)
    val str = arg match {
      case Left(x) => x
      case Right(x) => context.getResources().getString(x)
    }
    builder.setMessage(str).setPositiveButton(context.getResources.getString(android.R.string.yes),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_yes()
        }
      }).setNegativeButton(context.getResources.getString(android.R.string.no),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_no()
        }
      }).create.show()

  }
  def messageDialog(context:Context,arg:Either[String,Int],func_done:Unit=>Unit=identity[Unit]){
    val builder = new AlertDialog.Builder(context)
    val str = arg match {
      case Left(x) => x
      case Right(x) => context.getResources().getString(x)
    }
    builder.setMessage(str).setPositiveButton(context.getResources.getString(android.R.string.ok),new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_done()
        }
      }).create.show()
  }

  def generalHtmlDialog(context:Context,html_id:Int,func_done:Unit=>Unit=identity[Unit]){
    val builder= new AlertDialog.Builder(context)
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val html = context.getResources.getString(html_id)
    view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView].setText(Html.fromHtml(html))
    builder.setView(view)
    builder.setPositiveButton(context.getResources.getString(android.R.string.ok), new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_done()
        }
      });
    builder.create.show()
  }
  // Android's Environment.getExternalStorageDirectory does not actually return external SD card's path.
  // Thus we have to explore where the mount point of SD card is by owr own.
  // There are several ways to do this , and there seems no best way
  // (1) Read environment variables such SECONDARY_STORAGE -> not useful since the name of varibale varies between devices.
  // (2) Parse /system/etc/vold.fstab -> cannot use since Android 4.3 because it is removed.
  // (3) Parse /proc/mounts and find /dev/block/vold/* or vfat -> maybe good.
  // We use third method
  // see follwing for more infos:
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
    Globals.setButtonText.foreach( func =>
      func(
      if(Globals.is_playing){
        Right(R.string.now_playing)
      }else if(!NotifyTimerUtils.notify_timers.isEmpty){
        Left(NotifyTimerUtils.makeTimerText(context))
      }else{
        Left(FudaListHelper.makeReadIndexMessage(context))
      }))
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

  def setAudioPlayButton(view:View,context:Context,before_play:Option[KarutaPlayer=>Unit]=None){
    val btn = view.findViewById(R.id.audio_play).asInstanceOf[Button]
    val handler = new Handler()
    btn.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        Globals.global_lock.synchronized{
          Globals.player match{
            case Some(pl) => {
              if(Globals.is_playing){
                pl.stop()
                btn.setText(context.getResources().getString(R.string.audio_play))
              }else{
                before_play.foreach(_(pl))
                pl.play( _ => {
                  handler.post(new Runnable(){
                    override def run(){
                      btn.setText(context.getResources().getString(R.string.audio_play))
                    }
                  })
                })
                btn.setText(context.getResources().getString(R.string.audio_stop))
              }
            }
            case None =>
              handler.post(new Runnable(){
                override def run(){
                  Utils.messageDialog(context,Right(R.string.player_error_noplay))
                }
              })
          }
        }
      }
    })
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
}

class AlreadyReportedException(s:String) extends Exception(s){
}

