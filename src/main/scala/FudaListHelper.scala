package karuta.hpnpwd.wasuramoti

import _root_.android.content.{Context,ContentValues}
import _root_.android.database.CursorIndexOutOfBoundsException
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
    queryNext(context, 0).foreach(
      {case (_,_,first_index,_) => putCurrentIndex(context,first_index)}
    )
  }
  def moveNext(context:Context){
    val current_index = getCurrentIndex(context)
    queryNext(context, current_index).foreach(
      {case (_,_,_,next_index) => putCurrentIndex(context,next_index)}
    )
  }

  def makeReadIndexMessage(context:Context):String = {
    val num_to_read = new java.lang.Integer(queryNumbersToRead(context))
    val body = if("RANDOM"==Globals.prefs.get.getString("read_order",null)){
      context.getResources().getString(R.string.message_readindex_random,num_to_read)
    }else{
      val current_index = new java.lang.Integer(queryCurrentIndexWithSkip(context))
      context.getResources().getString(R.string.message_readindex_shuffle,current_index,num_to_read)
    }
    (if(num_to_read < AllFuda.list.size){ 
      Globals.prefs.get.getString("fudaset","") + "\n"
    }else{
      ""
    })+body
  }

  def allReadDone(context:Context):Boolean = {
    if("RANDOM"==Globals.prefs.get.getString("read_order",null)){
      false
    }else{
      queryCurrentIndexWithSkip(context) > queryNumbersToRead(context)
    }
  }

  def queryNumbersToRead(context:Context):Int = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),"skip=0 AND num > 0",null,null,null,null,null)
    cursor.moveToFirst()
    val count = cursor.getInt(0)
    cursor.close()
    db.close()
    return count
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
  def queryNext(context:Context,index:Int):Option[(Int,Int,Int,Int)] = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num","read_order"),"skip = 0 AND read_order >= ?",Array(index.toString),null,null,"read_order ASC","2")
    try{
      cursor.moveToFirst()
      val simo_num = cursor.getInt(0)
      val simo_order = cursor.getInt(1)
      cursor.moveToNext()
      val kami_num = cursor.getInt(0)
      val kami_order = cursor.getInt(1)
      return Some((simo_num,kami_num,simo_order,kami_order))
    }catch{
      case e:CursorIndexOutOfBoundsException =>
       return None
    }finally{
      cursor.close()
      db.close()
    }
  }
  def queryIndexWithSkip(context:Context,fake_index:Int):Int = {
    val db = Globals.database.get.getReadableDatabase
    //val cursor = db.rawQuery("SELECT ( SELECT COUNT(a.read_order) FROM "+Globals.TABLE_FUDALIST+" AS a where a.read_order <= b.read_order AND skip = 0) AS rnk,read_order FROM "+Globals.TABLE_FUDALIST+" AS b WHERE skip = 0 AND rnk = ?",Array(fake_index.toString))
    val cursor = db.rawQuery("SELECT ( SELECT COUNT(a.read_order) FROM "+Globals.TABLE_FUDALIST+" AS a where a.read_order <= b.read_order AND skip = 0) AS rnk,read_order FROM "+Globals.TABLE_FUDALIST+" AS b WHERE skip = 0 AND rnk = "+fake_index,null)
    cursor.moveToFirst()
    val ret = cursor.getInt(1)
    cursor.close()
    db.close()
    return(ret)
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
