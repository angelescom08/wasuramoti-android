package tami.pen.wasuramoti
import _root_.android.content.{Context,ContentValues}
import scala.util.Random

object FudaListHelper{
  val PREFS_NAME="wasuramoti.pref"
  val KEY_PREV="fuda_prev"
  val KEY_NEXT="fuda_next"

  def getCurrentNum(context:Context):(Int,Int) = {
    val prefs = context.getSharedPreferences(PREFS_NAME,0)
    val prev = prefs.getInt(KEY_PREV,-1)
    val next = prefs.getInt(KEY_NEXT,-1)
    return(prev,next)
  }
  def putCurrentNum(context:Context,prev:Int,next:Int){
    val prefs = context.getSharedPreferences(PREFS_NAME,0)
    val editor = prefs.edit()
    editor.putInt(KEY_PREV,prev)
    editor.putInt(KEY_NEXT,next)
    editor.commit() 
  }

  def moveToFirst(context:Context){
    
  }
  def moveNext(context:Context){
  }
  def moveTo(context:Context,num:Int){
  }
  def shuffle(context:Context){
    val rand = new Random()
    val shuffled = rand.shuffle( (1 to AllFuda.list.length).toList )
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () =>
      for ( (v,i) <- shuffled.zipWithIndex ){
        val cv = new ContentValues()
        cv.put("read_order",new java.lang.Integer(v))
        db.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array((i+1).toString))
      })
    db.close()
  }
}

// vim: set ts=2 sw=2 et:
