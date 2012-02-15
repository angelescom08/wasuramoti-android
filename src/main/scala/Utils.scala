package karuta.hpnpwd.wasuramoti

import _root_.android.os.Handler
import _root_.android.app.{AlertDialog,AlarmManager,NotificationManager,ProgressDialog}
import _root_.android.content.{DialogInterface,Context,Intent,SharedPreferences}
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.java.io.File
import _root_.java.util.Date
import _root_.java.text.SimpleDateFormat
import scala.collection.mutable

object Globals {
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 2
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  val global_lock = new Object()
  val notify_timers = new mutable.HashMap[Int,Intent]()
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
  var setButtonText = None:Option[Either[String,Int]=>Unit]
  var alarm_manager = None:Option[AlarmManager]
  var notify_manager = None:Option[NotificationManager]
  var progress_dialog = None:Option[ProgressDialogWithHandler]
  var is_playing = false
}

class ProgressDialogWithHandler(context:Context,handler:Handler) extends ProgressDialog(context){
  def showWithHandler(){
    handler.post(new Runnable{
      override def run(){show()}
    })
  }
  def dismissWithHandler(){
    handler.post(new Runnable{
      override def run(){dismiss()}  
    })
  }
}

object Utils {
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
  trait PrefTrait[T] { def from(s:String):T }
  implicit val IntPrefTrait = new PrefTrait[Int] { 
    def from(s : String) = s.toInt 
  }
  implicit val DoublePrefTrait = new PrefTrait[Double] { 
    def from(s : String) = s.toDouble 
  }

  def getPrefAs[T:PrefTrait](key:String,defValue:T):T = {
    if(Globals.prefs.isEmpty){
      return defValue
    }
    try{
      val v =Globals.prefs.get.getString(key,defValue.toString)
      implicitly[PrefTrait[T]].from(v)
    }catch{
      case e:NumberFormatException => defValue
    }
  }

  def deleteAllCache(context:Context){
    Globals.global_lock.synchronized{
      val files = context.getCacheDir().listFiles()
      if(files != null){
        for(f <- files){
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

