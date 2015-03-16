package karuta.hpnpwd.wasuramoti

import _root_.android.content.{Context,ContentValues}
import _root_.android.database.CursorIndexOutOfBoundsException
import _root_.android.text.TextUtils
import scala.util.Random

// According to one of the Android framework engineer, there is no need to close the database in content provider.
// In fact, if we close it manually with SQLiteDatabase.close() method, there seems to occur exception such as
// `java.lang.IllegalStateException: Cannot perform this operation because the connection pool has been closed.`
// Therefore we comment out all the SQLiteDatabase.close() method for instance acquired by getReadableDatabase().
// However, as for instance acquired by getWritableDatabase(), we explicitly call SQLiteDatabase.close() since document of SQLiteOpenHelper says so.
// Additionally, we use Globals.db_lock.synchronized{ ... } to make all the function thread safe to avoid SQLiteDatabase.close() called during query.
// See the following URL for more details:
//   https://groups.google.com/forum/#!msg/android-developers/NwDRpHUXt0U/jIam4Q8-cqQJ
//   http://stackoverflow.com/questions/4547461/closing-the-database-in-a-contentprovider
//   http://d.hatena.ne.jp/ukiki999/20100524/p1 [Japanese]

object FudaListHelper{
  val PREFS_NAME="wasuramoti.pref"
  val KEY_CURRENT_INDEX="fuda_current_index"
  var current_index_with_skip = None:Option[Int]
  var numbers_to_read = None:Option[Int]
  var numbers_of_karafuda = None:Option[Int]

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
    current_index_with_skip = None
  }

  def moveToFirst(context:Context){
    val fst = if(Utils.readFirstFuda){
      0
    }else{
      1
    }
    queryNext(context, fst).foreach(
      {case (_,_,first_index,_) => putCurrentIndex(context,first_index)}
    )
  }
  def movePrevOrNext(context:Context,is_next:Boolean){
    val current_index = getCurrentIndex(context)
    queryPrevOrNext(context, current_index,is_next).foreach{
      x => putCurrentIndex(context,x._4)
    }
  }
  def moveNext(context:Context){
    movePrevOrNext(context,true)
  }
  def movePrev(context:Context){
    movePrevOrNext(context,false)
  }

  def makeReadIndexMessage(context:Context):String = {
    val num_to_read = getOrQueryNumbersToReadAlt(context)
    val num_of_kara = getOrQueryNumbersOfKarafuda(context)
    val set_name = if(num_to_read == AllFuda.list.size + Utils.incTotalRead && num_of_kara == 0){
      ""
    }else{
      Globals.prefs.get.getString("fudaset","") + "\n"
    }
    val kara_and_order = {
      val kara = if(num_of_kara > 0){
        Some(context.getResources.getString(R.string.message_karafuda_num,new java.lang.Integer(num_of_kara)))
      }else{
        None
      }
      val order = Utils.getReadOrder match {
        case Utils.ReadOrder.PoemNum => Some(context.getResources.getString(R.string.message_in_fudanum_order))
        case _ => None
      }
      val s = Array(kara,order).collect{case Some(x)=>x}.mkString(" , ")
      if(TextUtils.isEmpty(s)){
        s
      }else{
        s + "\n"
      }
    }
    val body = if(Utils.isRandom){
      // we have to show result of getOrQueryNumbersToRead instead of getOrQueryNumbersToReadAlt when random mode,
      // however, quering DB again takes some cost, so we just decrease incTotalRead
      context.getResources.getString(R.string.message_readindex_random,
        new java.lang.Integer(num_to_read - Utils.incTotalRead)
      )
    }else{
      val current_index = getOrQueryCurrentIndexWithSkip(context)
      val (index_s,total_s) = Utils.makeDisplayedNum(current_index,num_to_read)
      if(current_index > num_to_read){
        context.getResources.getString(R.string.message_readindex_done,
          new java.lang.Integer(total_s))
      }else{
        context.getResources.getString(R.string.message_readindex_shuffle,
          new java.lang.Integer(index_s),
          new java.lang.Integer(total_s))
      }
    }
    set_name + kara_and_order + body
  }

  def allReadDone(context:Context):Boolean = {
    if(Utils.isRandom){
      false
    }else{
      getOrQueryCurrentIndexWithSkip(context) > getOrQueryNumbersToReadAlt(context)
    }
  }

  def isLastFuda(context:Context):Boolean = {
    if(Utils.isRandom){
      false
    }else{
      getOrQueryCurrentIndexWithSkip(context) == getOrQueryNumbersToReadAlt(context)
    }
  }

  def queryNumbersToRead(context:Context,cond:String):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),"skip "+cond+" AND num > 0",null,null,null,null,null)
    cursor.moveToFirst()
    val count = cursor.getInt(0)
    cursor.close()
    //db.close()
    return count
  }

  def queryCurrentIndexWithSkip(context:Context):Int = Globals.db_lock.synchronized{
    val current_index = getCurrentIndex(context)
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),"skip <= 0 AND read_order <= ?",Array(current_index.toString),null,null,null,null)
    cursor.moveToFirst()
    val index = cursor.getInt(0)
    cursor.close()
    //db.close()
    if(current_index == AllFuda.list.length + 1){
      return(index+1)
    }else{
      return(index)
    }
  }
  def queryPrevOrNext(context:Context,index:Int,is_next:Boolean,current_only:Boolean=false):Option[(Int,Int,Int,Int)] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val (op,order) = if(is_next){ (">=","ASC") }else{ ("<=","DESC") }
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num","read_order"),"skip <= 0 AND read_order "+op+" ?",Array(index.toString),null,null,"read_order "+order,"2")
    try{
      cursor.moveToFirst()
      val simo_num = cursor.getInt(0)
      val simo_order = cursor.getInt(1)
      val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
      if(current_only || (is_next && cursor.getCount == 1 && !roe.contains("NEXT"))){
        val maxn = AllFuda.list.length + 1
        return Some((simo_num,maxn,simo_order,maxn))
      }
      cursor.moveToNext()
      val kami_num = cursor.getInt(0)
      val kami_order = cursor.getInt(1)
      return Some((simo_num,kami_num,simo_order,kami_order))
    }catch{
      case _:CursorIndexOutOfBoundsException =>
       return None
    }finally{
      cursor.close()
      //db.close()
    }
  }

  def queryIndexFromFudaNum(context:Context,fudanum:Int):Option[Int] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("read_order"),"skip <= 0 AND num = ?",Array(fudanum.toString),null,null,null,"1")
    try{
      cursor.moveToFirst()
      val index = cursor.getInt(0)
      return Some(index)
    }catch{
      case _:CursorIndexOutOfBoundsException =>
       return None
    }finally{
      cursor.close()
      //db.close()
    }
  }

  def queryNext(context:Context,index:Int):Option[(Int,Int,Int,Int)] = {
    queryPrevOrNext(context,index,true)
  }
  def queryPrev(context:Context,index:Int):Option[(Int,Int,Int,Int)] = {
    queryPrevOrNext(context,index,false)
  }
  def queryIndexWithSkip(context:Context,fake_index:Int):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.rawQuery("SELECT ( SELECT COUNT(a.read_order) FROM "+Globals.TABLE_FUDALIST+" AS a where a.read_order <= b.read_order AND skip <= 0) AS rnk,read_order FROM "+Globals.TABLE_FUDALIST+" AS b WHERE skip <= 0 AND rnk = "+fake_index,null)
    cursor.moveToFirst()
    val ret = cursor.getInt(1)
    cursor.close()
    //db.close()
    return(ret)
  }

  def getHaveToReadFromDB(cond:String):Set[String] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),"skip "+cond+" AND num > 0",null,null,null,null,null)
    cursor.moveToFirst
    val have_to_read = ( 0 until cursor.getCount ).map{ x=>
      val r = AllFuda.list(cursor.getInt(0)-1)
      cursor.moveToNext
      r
    }.toSet
    cursor.close
    //db.close
    have_to_read
  }

  def getNotYetRead(context:Context,index:Int):Set[String] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cond = if(getOrQueryNumbersOfKarafuda(context) > 0){
      // When Karafuda is enabled, we treat all the fuda is supposed to be read
      // since the Kimariji will be the hint to which Karafuda is choosed.
      "OR (read_order < "+index+" AND skip = 1)"
    }else{
      "AND skip = 0" 
    }
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),"num > 0 AND ( read_order > ? " + cond + ")",Array(index.toString),null,null,null,null)
    cursor.moveToFirst 
    val notyetread = ( 0 until cursor.getCount ).map{ x=>
      val r = AllFuda.list(cursor.getInt(0)-1)
      cursor.moveToNext
      r
    }.toSet
    cursor.close()
    //db.close 
    notyetread
  }
  def getFudanumIndex(context:Context,fudanum:Int):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("read_order"),"num = ?",Array(fudanum.toString),null,null,null,"1")
    cursor.moveToFirst
    val r = cursor.getInt(0)
    cursor.close()
    // db.close()
    r
  }
  def getKimarijiAtIndex(context:Context,fudanum:Int,index:Option[Int]):String = {
    val target_index = index.getOrElse(getFudanumIndex(context,fudanum))
    val notyetread = getNotYetRead(context,target_index)
    TrieUtils.calcKimariji(notyetread,AllFuda.list(fudanum-1))
  }
  def getAlreadyReadFromKimariji(context:Context,fudanum:Int,kimari:String):Array[(String,Int)] = Globals.db_lock.synchronized{
    val current_index = getFudanumIndex(context,fudanum)
    val db = Globals.database.get.getReadableDatabase
    val nums = TrieUtils.makeNumListFromKimariji(kimari)
    if(nums.isEmpty){
      return new Array[(String,Int)](0)
    }
    val cursor = db.rawQuery("SELECT num,read_order FROM " + Globals.TABLE_FUDALIST + " WHERE skip <=0 AND read_order < "+current_index+" AND num IN ("+nums.mkString(",")+") ORDER BY read_order ASC",null)
    cursor.moveToFirst 
    val alreadyread = ( 0 until cursor.getCount ).map{ x=>
      val n = AllFuda.list(cursor.getInt(0)-1)
      val r = cursor.getInt(1)
      cursor.moveToNext
      (n,r)
    }
    cursor.close()
    //db.close()
    alreadyread.toArray
  }

  def getKimarijis(context:Context,fudanum:Int):(String,String,String) = {
    val kimari_all = AllFuda.list(fudanum-1)
    val kimari_in_fudaset = if(FudaListHelper.getOrQueryNumbersToRead(context) < 100){
      FudaListHelper.getKimarijiAtIndex(context,fudanum,Some(-1))
    }else{
      kimari_all
    }
    val kimari_cur = if(Utils.isRandom){
      kimari_in_fudaset
    }else{
      FudaListHelper.getKimarijiAtIndex(context,fudanum,None)
    }
    (kimari_all,kimari_cur,kimari_in_fudaset)
  }

  def chooseKarafuda():Int={
    val have_to_read = getHaveToReadFromDB("= 0")
    val not_read = AllFuda.list.toSet -- have_to_read
    val kara = TrieUtils.makeKarafuda(have_to_read, not_read, 1)
    AllFuda.getFudaNum(kara.head)
  }

  def queryRandom(context:Context):Int = Globals.db_lock.synchronized{
    val num_read = getOrQueryNumbersToRead(context)
    val num_kara = getOrQueryNumbersOfKarafuda(context)
    if(num_kara > 0){
      val rand = new Random()
      val r = rand.nextDouble * num_read.toDouble
      if(r < num_kara.toDouble){
        return chooseKarafuda()
      }
    }
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),"skip = 0 AND num > 0",null,null,null,"random()","1")
    cursor.moveToFirst()
    val num = cursor.getInt(0)
    cursor.close()
    //db.close()
    return num
  }

  def shuffleAndMoveToFirst(context:Context){
     shuffle(context)
     moveToFirst(context)
  }

  def shuffle(context:Context){ Globals.db_lock.synchronized{
    if(Globals.prefs.get.getBoolean("karafuda_enable",false)){
      updateSkipList()
    }
    val rand = new Random()
    val num_list = (1 to AllFuda.list.length).toList 
    val shuffled = Utils.getReadOrder match {
      case Utils.ReadOrder.PoemNum => num_list
      case _ => rand.shuffle(num_list)
    }
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () =>
      for ( (v,i) <- shuffled.zipWithIndex ){
        val cv = new ContentValues()
        cv.put("read_order",new java.lang.Integer(v))
        db.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array((i+1).toString))
      })
    db.close()
  }}
  def updateSkipList(title:String=null){ Globals.db_lock.synchronized{
    val fudaset_title = if(title == null){
      Globals.prefs.get.getString("fudaset","")
    }else{
      title
    }
    val dbr = Globals.database.get.getReadableDatabase
    val cursor = dbr.query(Globals.TABLE_FUDASETS,Array("title","body"),"title = ?",Array(fudaset_title),null,null,null,null)
    val have_to_read = if( cursor.getCount > 0 ){
      cursor.moveToFirst()
      val body = cursor.getString(1)
      TrieUtils.makeHaveToRead(body)
    }else{
      AllFuda.list.toSet
    }
    cursor.close()
    //dbr.close()

    val skip_temp = AllFuda.list.toSet -- have_to_read
    val karafuda = if(Globals.prefs.get.getBoolean("karafuda_enable",false)){
      val kara_num = Globals.prefs.get.getInt("karafuda_append_num",0)
      TrieUtils.makeKarafuda(have_to_read,skip_temp,kara_num)
    }else{
      Set()
    }
    val skip = skip_temp -- karafuda
    val dbw = Globals.database.get.getWritableDatabase
    Utils.withTransaction(dbw, ()=>
      for((ss,flag) <- Array((karafuda,-1),(have_to_read,0),(skip,1))){
        for( s <- ss ){
          val cv = new ContentValues()
          cv.put("skip",new java.lang.Integer(flag))
          val num = AllFuda.getFudaNum(s)
          dbw.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array(num.toString))
        }
      })
    dbw.close()
    numbers_to_read = None
    numbers_of_karafuda = None
    current_index_with_skip = None
  }}

  def getOrQueryCurrentIndexWithSkip(context:Context):Int = {
    current_index_with_skip.getOrElse{
      val r = queryCurrentIndexWithSkip(context)
      current_index_with_skip = Some(r)
      r
    }
  }
  def getOrQueryNumbersToRead(context:Context):Int = {
    numbers_to_read.getOrElse{
      val r = queryNumbersToRead(context,"<= 0")
      numbers_to_read = Some(r)
      r
    }
  }
  def getOrQueryNumbersToReadAlt(context:Context):Int = {
    val r = getOrQueryNumbersToRead(context)
    r + Utils.incTotalRead()
  }
  def getOrQueryNumbersOfKarafuda(context:Context):Int = {
    numbers_of_karafuda.getOrElse{
      val r = queryNumbersToRead(context,"= -1")
      numbers_of_karafuda = Some(r)
      r
    }
  }
  def getOrQueryFudaNumToRead(context:Context,offset:Int):Option[Int] = {
    val r = offset match{
      case 0 =>
        if(!Globals.player.isEmpty){
          Some(Globals.player.get.cur_num)
        }else{
          val current_index = getCurrentIndex(context)
          queryPrevOrNext(context, current_index, true, true).map{_._1}
        }
      case 1 =>
        if(!Globals.player.isEmpty){
          Some(Globals.player.get.next_num)
        }else{
          val current_index = getCurrentIndex(context)
          queryNext(context, current_index).map{_._2}
        }
      case -1 =>
        val current_index = getCurrentIndex(context)
        queryPrev(context, current_index).map{_._2}
      case _ =>
        throw new Exception("offset must be between -1 and 2: " + offset)
    }
    if(r == Some(AllFuda.list.length + 1) ||
      ! Utils.readFirstFuda && r == Some(0)
    ){
      None
    }else{
      r
    }
  }
}

// vim: set ts=2 sw=2 et:
