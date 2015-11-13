package karuta.hpnpwd.wasuramoti

import _root_.android.content.{Context,ContentValues}
import _root_.android.database.CursorIndexOutOfBoundsException
import _root_.android.database.sqlite.SQLiteDatabase
import _root_.android.text.TextUtils

import scala.util.Random
import scala.collection.mutable

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

  // Since current index is updated frequently, we save it to separate file for performance.
  // However, the performance effect may be very very small so we may merge to main preference.
  val PREFS_NAME="wasuramoti.pref"
  val KEY_CURRENT_INDEX="fuda_current_index"

  // query cache
  var current_index_with_skip = None:Option[Int]
  var numbers_to_read = None:Option[Int]
  var numbers_of_karafuda = None:Option[Int]
  var numbers_of_memorized = None:Option[Int]

  def invalidateQueryCacheExceptKarafuda(){
    numbers_to_read = None
    numbers_of_memorized = None
    current_index_with_skip = None
  }

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

  def updateCurrentIndexWithSkip(context:Context,index:Option[Int]=None){
    val current_index = index.getOrElse{getOrQueryCurrentIndexWithSkip(context)}
    val new_index = queryIndexWithSkip(current_index)
    FudaListHelper.putCurrentIndex(context,new_index)
  }

  def moveToFirst(context:Context){
    val fst = if(Utils.readFirstFuda){
      0
    }else{
      1
    }
    queryNext(fst).foreach(
      {case (_,_,first_index,_) => putCurrentIndex(context,first_index)}
    )
  }
  def movePrevOrNext(context:Context,is_next:Boolean){
    val current_index = getCurrentIndex(context)
    queryPrevOrNext(current_index,is_next).foreach{
      x => putCurrentIndex(context,x._4)
    }
  }
  def moveNext(context:Context){
    movePrevOrNext(context,true)
  }
  def movePrev(context:Context){
    movePrevOrNext(context,false)
  }

  // this does not consider karafuda
  def isBoundedByFudaset():Boolean = {
    val num_to_read = getOrQueryNumbersToRead
    val num_memorized = if(Globals.prefs.get.getBoolean("memorization_mode",false)){
      getOrQueryNumbersOfMemorized
    }else{
      0
    }
    num_to_read + num_memorized != AllFuda.list.size
  }

  def makeReadIndexMessage(context:Context):String = {
    val num_to_read = getOrQueryNumbersToReadAlt()
    val num_of_kara = getOrQueryNumbersOfKarafuda()

    val set_name = if(!isBoundedByFudaset && num_of_kara == 0){
      ""
    }else{
      val t = Globals.prefs.get.getString("fudaset","")
      if(TextUtils.isEmpty(t)){
        ""
      }else{
        t + "\n"
      }
    }
    val kara_memorized_order = {
      val kara = if(num_of_kara > 0){
        Some(context.getResources.getString(R.string.message_karafuda_num,new java.lang.Integer(num_of_kara)))
      }else{
        None
      }
      val memorized = if(Globals.prefs.get.getBoolean("memorization_mode",false)){
        val num = getOrQueryNumbersOfMemorized
        Some(context.getResources.getString(R.string.message_memorized_num,new java.lang.Integer(num)))
      }else{
        None
      }
      val order = Utils.getReadOrder match {
        case Utils.ReadOrder.PoemNum => Some(context.getResources.getString(R.string.message_in_fudanum_order))
        case _ => None
      }
      val s = Array(order,kara,memorized).flatten.mkString(", ")
      if(TextUtils.isEmpty(s)){
        ""
      }else{
        s + "\n"
      }
    }
    val body = if(Utils.isRandom){
      // we have to show result of getOrQueryNumbersToRead instead of getOrQueryNumbersToReadAlt when random mode,
      // however, querying DB again takes some cost, so we just decrease incTotalRead
      context.getResources.getString(R.string.message_readindex_random,
        new java.lang.Integer(num_to_read - Utils.incTotalRead)
      )
    }else{
      val current_index = getOrQueryCurrentIndexWithSkip(context)
      val (index_s,total_s) = Utils.makeDisplayedNum(current_index,num_to_read)
      if(current_index > num_to_read){
        context.getResources.getString(R.string.message_readindex_done,
          new java.lang.Integer(total_s))
      }else if(Globals.prefs.get.getBoolean("show_current_index",true)){
        context.getResources.getString(R.string.message_readindex_shuffle,
          new java.lang.Integer(index_s),
          new java.lang.Integer(total_s))
      }else{
        context.getResources.getString(R.string.message_readindex_onlytotal,
          new java.lang.Integer(total_s))
      }
    }
    set_name + kara_memorized_order + body
  }

  def allReadDone(context:Context):Boolean = {
    if(Utils.isRandom){
      false
    }else{
      getOrQueryCurrentIndexWithSkip(context) > getOrQueryNumbersToReadAlt()
    }
  }

  def isLastFuda(context:Context):Boolean = {
    if(Utils.isRandom){
      false
    }else{
      getOrQueryCurrentIndexWithSkip(context) == getOrQueryNumbersToReadAlt()
    }
  }
  // [0,101]
  def countNumbersInFudaList(cond:String):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),cond,null,null,null,null,null)
    cursor.moveToFirst()
    val count = cursor.getInt(0)
    cursor.close()
    //db.close()
    return count
  }
  
  // [0,100]
  def queryNumbersToRead(cond:String):Int = {
    countNumbersInFudaList(s"skip $cond AND num > 0")
  }

  // [0,102] 
  def queryCurrentIndexWithSkip(context:Context):Int = {
    val current_index = getCurrentIndex(context)
    val index = countNumbersInFudaList(s"skip <= 0 AND read_order <= $current_index")
    if(current_index == AllFuda.list.length + 1){
      // only occurs when incTotalRead == 1
      return(index+1)
    }else{
      return(index)
    }
  }
  def queryPrevOrNext(index:Int,is_next:Boolean,current_only:Boolean=false):Option[(Int,Int,Int,Int)] = Globals.db_lock.synchronized{
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

  // [0,100]
  def queryIndexFromFudaNum(fudanum:Int):Option[Int] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    rawQueryGetInt(db,0,s"SELECT read_order FROM ${Globals.TABLE_FUDALIST} WHERE skip <=0 AND num=${fudanum} LIMIT 1")
  }

  def queryNext(index:Int):Option[(Int,Int,Int,Int)] = {
    queryPrevOrNext(index,true)
  }
  def queryPrev(index:Int):Option[(Int,Int,Int,Int)] = {
    queryPrevOrNext(index,false)
  }

  def rawQueryGetInt(db:SQLiteDatabase,column:Int,query:String):Option[Int] = {
    val cursor = db.rawQuery(query,null)
    cursor.moveToFirst()
    val res = if(cursor.getCount > 0){
      Some(cursor.getInt(column))
    }else{
      None
    }
    cursor.close()
    res
  }

  // input:  [0,102]
  // output: [0,100]
  def queryIndexWithSkip(fake_index:Int):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    // TODO: use string interpolation
    rawQueryGetInt(db,1,"SELECT ( SELECT COUNT(a.read_order) FROM "+Globals.TABLE_FUDALIST+" AS a where a.read_order <= b.read_order AND skip <= 0) AS rnk,read_order FROM "+Globals.TABLE_FUDALIST+" AS b WHERE skip <= 0 AND rnk = "+fake_index)
    .orElse(rawQueryGetInt(db,0,"SELECT MAX(read_order) FROM "+Globals.TABLE_FUDALIST+" WHERE skip <= 0"))
    .getOrElse(0)
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

  def getNotYetRead(index:Int):Set[String] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cond = if(getOrQueryNumbersOfKarafuda() > 0){
      // When Karafuda is enabled, we treat all the fuda is supposed to be read
      // since the Kimariji will be the hint to which Karafuda was chosen.
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
  def getFudanumIndex(fudanum:Int):Int = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("read_order"),"num = ?",Array(fudanum.toString),null,null,null,"1")
    cursor.moveToFirst
    val r = cursor.getInt(0)
    cursor.close()
    // db.close()
    r
  }
  def getKimarijiAtIndex(fudanum:Int,index:Option[Int]):String = {
    val target_index = index.getOrElse(getFudanumIndex(fudanum))
    val notyetread = getNotYetRead(target_index)
    TrieUtils.calcKimariji(notyetread,AllFuda.list(fudanum-1))
  }
  def getAlreadyReadFromKimariji(fudanum:Int,kimari:String):Array[(String,Int)] = Globals.db_lock.synchronized{
    val current_index = getFudanumIndex(fudanum)
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

  def getKimarijis(fudanum:Int):(String,String,String) = {
    val kimari_all = AllFuda.list(fudanum-1)
    val kimari_in_fudaset = if(isBoundedByFudaset){
      FudaListHelper.getKimarijiAtIndex(fudanum,Some(-1))
    }else{
      kimari_all
    }
    val kimari_cur = if(Utils.disableKimarijiLog){
      kimari_in_fudaset
    }else{
      FudaListHelper.getKimarijiAtIndex(fudanum,None)
    }
    (kimari_all,kimari_cur,kimari_in_fudaset)
  }

  def chooseKarafuda():Int={
    val have_to_read = getHaveToReadFromDB("= 0")
    val not_read = AllFuda.list.toSet -- have_to_read
    val kara = TrieUtils.makeKarafuda(have_to_read, not_read, 1)
    AllFuda.getFudaNum(kara.head)
  }

  def queryRandom():Option[Int] = Globals.db_lock.synchronized{
    val num_read = getOrQueryNumbersToRead()
    val num_kara = getOrQueryNumbersOfKarafuda()
    if(num_kara > 0){
      val rand = new Random()
      val r = rand.nextDouble * num_read.toDouble
      if(r < num_kara.toDouble){
        return Some(chooseKarafuda())
      }
    }
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),"skip = 0 AND num > 0",null,null,null,"random()","1")
    val num = if(cursor.getCount == 0){
      None
    }else{
      cursor.moveToFirst()
      Some(cursor.getInt(0))
    }
    cursor.close()
    //db.close()
    return num
  }

  def shuffleAndMoveToFirst(context:Context){
     shuffle(context)
     moveToFirst(context)
  }

  def shuffle(context:Context){ Globals.db_lock.synchronized{
    if(Globals.prefs.get.getBoolean("karafuda_enable",false) ||
      Globals.prefs.get.getBoolean("memorization_mode",false)
      ){
      updateSkipList(context)
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

  // Note: we have to always call this function after memorization_mode has been changed
  def updateSkipList(context:Context,title:String=null){ Globals.db_lock.synchronized{
    val fudaset_title = if(title == null){
      Globals.prefs.get.getString("fudaset","")
    }else{
      title
    }
    val dbr = Globals.database.get.getReadableDatabase

    val memorized:Set[String] = {
      if(Globals.prefs.get.getBoolean("memorization_mode",false)){
        val cursor = dbr.query(Globals.TABLE_FUDALIST,Array("num"),"memorized = 1 AND num > 0",null,null,null,null,null)
        cursor.moveToFirst()
        val r = ( 0 until cursor.getCount ).map{ x=>
          val n = AllFuda.list(cursor.getInt(0)-1)
          cursor.moveToNext
          n
        }
        cursor.close()
        r.toSet
      }else{
        Set()
      }
    }

    val have_to_read_all = {
      // TODO: this is redundant query when fudaset_title is empty
      val cursor = dbr.query(Globals.TABLE_FUDASETS,Array("title","body"),"title = ?",Array(fudaset_title),null,null,null,null)
      val r = if( cursor.getCount > 0 ){
        cursor.moveToFirst()
        val body = cursor.getString(1)
        TrieUtils.makeHaveToRead(body)
      }else{
        AllFuda.list.toSet
      }
      cursor.close()
      r
    }

    //dbr.close()

    val skip_temp = AllFuda.list.toSet -- have_to_read_all
    val karafuda = if(
      Globals.prefs.get.getBoolean("karafuda_enable",false) &&
      ! Globals.prefs.get.getBoolean("memorization_mode",false) // disable karafuda when memorization mode
    ){
      val kara_num = Globals.prefs.get.getInt("karafuda_append_num",0)
      TrieUtils.makeKarafuda(have_to_read_all--memorized,skip_temp--memorized,kara_num)
    }else{
      Set()
    }
    val skip = skip_temp -- karafuda
    val have_to_read = have_to_read_all -- memorized
    val memorized_skip = memorized & have_to_read_all

    val dbw = Globals.database.get.getWritableDatabase
    Utils.withTransaction(dbw, ()=>
      for((ss,flag) <- Array((karafuda,-1),(have_to_read,0),(skip,1),(memorized_skip,2))){
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
    numbers_of_memorized = None
    current_index_with_skip = None
    updateCurrentIndexWithSkip(context)
    Globals.forceRefresh = true
  }}

  def getOrQueryCurrentIndexWithSkip(context:Context):Int = {
    current_index_with_skip.getOrElse{
      val r = queryCurrentIndexWithSkip(context)
      current_index_with_skip = Some(r)
      r
    }
  }
  def getOrQueryNumbersToRead():Int = {
    numbers_to_read.getOrElse{
      val r = queryNumbersToRead("<= 0")
      numbers_to_read = Some(r)
      r
    }
  }
  def getOrQueryNumbersToReadAlt():Int = {
    val r = getOrQueryNumbersToRead()
    r + Utils.incTotalRead()
  }
  def getOrQueryNumbersOfKarafuda():Int = {
    numbers_of_karafuda.getOrElse{
      val r = queryNumbersToRead("= -1")
      numbers_of_karafuda = Some(r)
      r
    }
  }
  def getOrQueryNumbersOfMemorized():Int = {
    numbers_of_memorized.getOrElse{
      val r = queryNumbersToRead("= 2")
      numbers_of_memorized = Some(r)
      r
    }
  }

  def getOrQueryFudaNumToRead(context:Context,offset:Int):Option[Int] = {
    val r = offset match{
      case 0 =>
        Globals.player.map{_.cur_num}
        .orElse{
          val current_index = getCurrentIndex(context)
          queryPrevOrNext(current_index, true, true).map{_._1}
        }
      case 1 =>
        Globals.player.map{_.next_num}
        .orElse{
          val current_index = getCurrentIndex(context)
          queryNext(current_index).map{_._2}
        }
      case -1 =>
        val current_index = getCurrentIndex(context)
        if(Globals.player.isEmpty && queryPrevOrNext(current_index, true, true).isEmpty){
          // This means it is last fuda and cur_view.cur_num is None.
          // It occurs when in either one of the following condition holds.
          // (1) go last poem -> memorization mode -> click `memorized` -> swipe rightwards
          // (2) go last poem -> play the poem -> restart app
          queryPrevOrNext(current_index, false, true).map{_._1}
        }else{
          queryPrev(current_index).map{_._2}
        }
      case _ =>
        throw new Exception("offset must be between -1 and 2: " + offset)
    }
    // TODO: Since Reader.audioFileExists is more strict than before,
    //       Globals.player will be empty for these cases, and might not have to check these conditions.
    if(r == Some(AllFuda.list.length + 1) ||
      ! Utils.readFirstFuda && r == Some(0)
    ){
      None
    }else{
      r
    }
  }

  def getMemoraziedFlag(db:SQLiteDatabase,fudanum:Int):Boolean = {
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("memorized"),"num = ?",Array(fudanum.toString),null,null,null,"1")
    cursor.moveToFirst
    val r = cursor.getInt(0)
    cursor.close()
    return r == 1
  }

  def isMemorized(fudanum:Int):Boolean = {
    getMemoraziedFlag(Globals.database.get.getReadableDatabase,fudanum)
  }
  def switchMemorized(fudanum:Int){ Globals.db_lock.synchronized {
    if(fudanum == 0){
      // TODO: include joka to memorization mode
      return
    }
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () => {
        // we disable karafuda mode when memorize mode is on, so we don't have to consider skip = -1
        val (newm,news) = if(getMemoraziedFlag(db,fudanum)){
          (0,0)
        }else{
          (1,2)
        }
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDALIST} SET memorized = $newm, skip = $news WHERE num = '$fudanum'")
    })
    db.close()
  } }

  def resetMemorizedAll(){ Globals.db_lock.synchronized {
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () => {
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDALIST} SET memorized = 0")
    })
    db.close()
  } }

  def selectFudasetByTitle(title:String):(Long,String) = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDASETS,Array("id","body"),"title = ?",Array(title),null,null,null,null)
    cursor.moveToFirst()
    val did = cursor.getLong(0)
    val body = cursor.getString(1)
    cursor.close()
    //db.close()
    (did,body)
  }
  def selectFudasetAll():Array[(String,Int)] = {
    val res = mutable.Buffer[(String,Int)]()
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDASETS,Array("title","set_size"),null,null,null,null,null,null)
    cursor.moveToFirst()
    for( i <- 0 until cursor.getCount ){
      res += ((cursor.getString(0), cursor.getInt(1)))
      cursor.moveToNext()
    }
    cursor.close()
    //db.close()
    res.toArray
  }
}

// vim: set ts=2 sw=2 et:
