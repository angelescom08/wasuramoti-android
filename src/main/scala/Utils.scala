package tami.pen.wasuramoti

import _root_.android.app.AlertDialog
import _root_.android.content.{DialogInterface,Context,SharedPreferences}
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.java.io.File
import scala.collection.Iterable

object Globals {
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 1
  val READER_DIR = "wasuramoti_reader"
  val ASSETS_READER_DIR="reader"
  var database = None:Option[DictionaryOpenHelper]
  var prefs = None:Option[SharedPreferences]
  var player = None:Option[KarutaPlayer]
}

object Utils {
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
  def messageDialog(context:Context,arg:Either[String,Int]){
    val builder = new AlertDialog.Builder(context)
    val str = arg match {
      case Left(x) => x
      case Right(x) => context.getResources().getString(x)
    }
    builder.setMessage(str).setPositiveButton("OK",new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
        }
      }).create.show()
  }
  def walkDir(f:File,depth:Int,func:(File)=>Unit){
    if(depth == 0){
      return
    }
    for( i <- f.listFiles ){
      func(i)
      if( i.isDirectory ){
        walkDir(i,depth - 1,func)
      }
    }
  }
}

