package tami.pen.wasuramoti

import _root_.android.app.AlertDialog
import _root_.android.content.{DialogInterface,Context}
import _root_.android.database.sqlite.SQLiteDatabase

object Globals {
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val TABLE_READFILTER = "readfilter"
  val TABLE_READERS = "readers"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 1
  var database = None:Option[DictionaryOpenHelper]
}

object Utils {
  def withTransaction(db:SQLiteDatabase,func:()=>Unit){
    db.beginTransaction()
    func()
    db.setTransactionSuccessful()
    db.endTransaction
  }
  def confirmDialog(context:Context,arg:Either[String,Int],func_yes:()=>Unit,func_no:()=>Unit = ()=>()){
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
}


