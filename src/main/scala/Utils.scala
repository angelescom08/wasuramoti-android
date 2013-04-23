package karuta.hpnpwd.wasuramoti

import _root_.android.app.{AlertDialog,AlarmManager,NotificationManager}
import _root_.android.content.{DialogInterface,Context,Intent,SharedPreferences}
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.preference.PreferenceManager
import _root_.android.text.{TextUtils,Html}
import _root_.android.util.Base64
import _root_.android.os.Handler
import _root_.android.media.AudioManager
import _root_.android.view.{LayoutInflater,View}
import _root_.android.widget.{TextView,Button}

import _root_.java.io.{File,ByteArrayOutputStream,ObjectOutputStream,ByteArrayInputStream,ObjectInputStream,InvalidClassException}
import _root_.java.text.SimpleDateFormat
import _root_.java.util.Date

import scala.collection.mutable

object Globals {
  val IS_DEBUG = false
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 2
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val CACHE_SUFFIX_OGG = "_copied.ogg"
  val CACHE_SUFFIX_WAV = "_decoded.wav"
  val HEAD_SILENCE_LENGTH = 200 // in milliseconds
  val global_lock = new Object()
  val notify_timers = new mutable.HashMap[Int,Intent]()
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
  var setButtonText = None:Option[Either[String,Int]=>Unit]
  var alarm_manager = None:Option[AlarmManager]
  var notify_manager = None:Option[NotificationManager]
  var is_playing = false
  var forceRefresh = false
  var audio_volume_bkup = None:Option[Int]
}

object Utils {
  type EqualizerSeq = Seq[Option[Double]]
  // Since every Activity has a possibility to be killed by android when it is background,
  // all the Activity in this application should call this method in onCreate()
  def initGlobals(app_context:Context) {
    Globals.global_lock.synchronized{
      if(Globals.database.isEmpty){
        Globals.database = Some(new DictionaryOpenHelper(app_context))
      }
      PreferenceManager.setDefaultValues(app_context,R.xml.conf,false)
      if(Globals.prefs.isEmpty){
        Globals.prefs = Some(PreferenceManager.getDefaultSharedPreferences(app_context))
      }
      ReaderList.setDefaultReader(app_context)
    }
  }

  def makeTimerText(context:Context):String = {
     val nt = Globals.notify_timers
     var title = context.getResources.getString(R.string.timers_remaining)
     nt.toList.sortWith{case ((k1,v1),(k2,v2)) => v1.getExtras.getLong("limit_millis") < v1.getExtras.getLong("limit_millis")}.map{case (k,v) =>
       val millis = v.getExtras.getLong("limit_millis")
       val minutes_left = scala.math.ceil((millis - System.currentTimeMillis()) / (1000 * 60.0)).toInt
       val df = new SimpleDateFormat(
         if( minutes_left < 60 * 24){
           "HH:mm"
         }else{
           "MM/dd HH:mm"
         }
       )
       df.format(new Date(millis)) + " (" + minutes_left.toString + " " + context.getResources.getString(R.string.timers_minutes_left) + ")"
     }.foldLeft(title)(_+"\n"+_)
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
    builder.setMessage(str).setPositiveButton("YES",new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_yes()
        }
      }).setNegativeButton("NO",new DialogInterface.OnClickListener(){
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
    builder.setMessage(str).setPositiveButton("OK",new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func_done()
        }
      }).create.show()
  }

  def generalHtmlDialog(context:Context,html_id:Int){
    val builder= new AlertDialog.Builder(context)
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val html = context.getResources().getString(html_id)
    view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView].setText(Html.fromHtml(html))
    builder.setView(view)
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
        }
      });
    builder.create.show()
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
      }else if(!Globals.notify_timers.isEmpty){
        Left(Utils.makeTimerText(context))
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

