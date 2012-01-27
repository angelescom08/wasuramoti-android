package tami.pen.wasuramoti
import _root_.android.content.{Context,ContentValues}
import scala.util.Random

object FudaListHelper{
  val PREFS_NAME="wasuramoti.pref"
  val KEY_CURRENT_INDEX="fuda_current_index"

  def getCurrentIndex(context:Context):Int = {
    val prefs = context.getSharedPreferences(PREFS_NAME,0)
    val index = prefs.getInt(KEY_CURRENT_INDEX,0)
    return(index)
  }
  def putCurrentIndex(context:Context,index:Int){
    val prefs = context.getSharedPreferences(PREFS_NAME,0)
    val editor = prefs.edit()
    editor.putInt(KEY_CURRENT_INDEX,index)
    editor.commit() 
  }

  def moveToFirst(context:Context){
    val (_,_,next_index,_) = queryNext(context, 0)
    putCurrentIndex(context,next_index)
  }
  def moveNext(context:Context){
    val current_index = getCurrentIndex(context)
    val (_,_,_,next_index) = queryNext(context, current_index)
    putCurrentIndex(context,next_index)
  }
  def moveTo(context:Context,num:Int){
  }
  def queryCurrentIndexWithSkip(context:Context):Int = {
    val current_index = getCurrentIndex(context)
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),"skip = 0 AND read_order <= ?",Array(current_index.toString),null,null,null,null)
    cursor.moveToFirst()
    val index = cursor.getInt(0)
    cursor.close()
    db.close()
    return(index)
  }
  def queryNext(context:Context,index:Int):(Int,Int,Int,Int) = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num","read_order"),"skip = 0 AND read_order >= ?",Array(index.toString),null,null,"read_order ASC","2")
    cursor.moveToFirst()
    val simo_num = cursor.getInt(0)
    val simo_order = cursor.getInt(1)
    cursor.moveToNext()
    val kami_num = cursor.getInt(0)
    val kami_order = cursor.getInt(1)
    cursor.close()
    db.close()
    return(simo_num,kami_num,simo_order,kami_order)
  }
  def queryRandom(context:Context):Int = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),"skip = 0 AND num > 0",null,null,null,"random()","1")
    cursor.moveToFirst()
    val num = cursor.getInt(0)
    cursor.close()
    db.close()
    return num
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
