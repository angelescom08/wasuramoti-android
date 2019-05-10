package karuta.hpnpwd.wasuramoti

import android.content.{Context,ContentValues}
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteDatabase
import android.database.Cursor
import android.text.TextUtils

import scala.collection.mutable

case class FudaSet(id:Long, title:String, body:String, set_size: Int)
case class NumWithOrder(num:Int, order:Int, torifuda_reverse:Boolean)
case class CurNext(cur:NumWithOrder, next:NumWithOrder)

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
    queryNext(fst).foreach{
      x=>putCurrentIndex(context,x.cur.order)
  }
  }
  def movePrevOrNext(context:Context,go_forward:Boolean){
    val current_index = getCurrentIndex(context)
    //TODO: maybe we don't have to check memorization_mode, and always set force_current = true
    val force_current = Globals.prefs.get.getBoolean("memorization_mode",false)
    queryTwo(current_index,go_forward,force_current).foreach{
      x=>putCurrentIndex(context,x.next.order)
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

  def makeReadIndexMessage(context:Context,fromAuto:Boolean):String = {
    val res = context.getResources
    val pref = Globals.prefs.get
    val num_to_read = getOrQueryNumbersToReadAlt()
    val num_of_kara = getOrQueryNumbersOfKarafuda()

    val set_name = if(!isBoundedByFudaset && num_of_kara == 0){
      None
    }else{
      Option(pref.getString("fudaset",null))
    }
    val kara_memorized_order = {
      val kara = if(num_of_kara > 0){
        Some(res.getString(R.string.message_karafuda_num,new java.lang.Integer(num_of_kara)))
      }else{
        None
      }
      val memorized = if(pref.getBoolean("memorization_mode",false)){
        val num = getOrQueryNumbersOfMemorized
        Some(res.getString(R.string.message_memorized_num,new java.lang.Integer(num)))
      }else{
        None
      }
      val s = Seq(kara,memorized).flatten.mkString(", ")
      if(TextUtils.isEmpty(s)){
        None
      }else{
        Some(s)
      }
    }
    val body = Some(if(Utils.isRandom){
      // we have to show result of getOrQueryNumbersToRead instead of getOrQueryNumbersToReadAlt when random mode,
      // however, querying DB again takes some cost, so we just decrease incTotalRead
      res.getString(R.string.message_readindex_random,
        new java.lang.Integer(num_to_read - Utils.incTotalRead)
      )
    }else{
      val current_index = getOrQueryCurrentIndexWithSkip(context)
      val (index_s,total_s) = Utils.makeDisplayedNum(current_index,num_to_read)
      var show_seq = false
      val str = if(current_index > num_to_read){
        res.getString(R.string.message_readindex_done,
          new java.lang.Integer(total_s))
      }else if(Globals.player.exists(_.is_replay)){
        res.getString(R.string.message_readindex_replay,
          new java.lang.Integer(total_s))
      }else if(PrefManager.getPrefBool(context,PrefKeyBool.ShowCurrentIndex)){
        show_seq = true
        res.getString(R.string.message_readindex_shuffle,
          new java.lang.Integer(index_s),
          new java.lang.Integer(total_s))
      }else{
        show_seq = true
        res.getString(R.string.message_readindex_onlytotal,
          new java.lang.Integer(total_s))
      }
      if(show_seq){
        val order = Utils.getReadOrder match {
          case Utils.ReadOrder.PoemNum | Utils.ReadOrder.Musumefusahose => res.getString(R.string.message_in_fudanum_order) + " "
          case _ => ""
        }
        order + str
      }else{
        str
      }
    })
    val status = Some(
        if(Globals.is_playing){
          if(pref.getBoolean("autoplay_enable",false) && !Globals.player.exists(_.is_replay)){
            if(pref.getBoolean("autoplay_stop",false) && Globals.autoplay_started.nonEmpty){
              val max = pref.getLong("autoplay_stop_minutes",30)
              val cur = (System.currentTimeMillis - Globals.autoplay_started.get) / 60000
              val left = max - cur
              res.getString(R.string.now_auto_playing_stop, new java.lang.Integer(left.toInt))
            }else{
              res.getString(R.string.now_auto_playing)
            }
          }else{
            res.getString(R.string.now_playing)
          }
        }else{
          if(fromAuto && Globals.player.nonEmpty){
            val sec = pref.getLong("autoplay_span",3).toInt
            res.getString(R.string.now_stopped_auto,new java.lang.Integer(sec))
          }else{
            res.getString(R.string.now_stopped)
          }
        }
      )
    Seq(set_name,kara_memorized_order,body,status).flatten.mkString("\n")
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
  def queryNumbersToReadAlt(anycond:String):Int = {
    countNumbersInFudaList(s"$anycond AND num > 0")
  }
  // [0,100]
  def queryNumbersToRead(skipcond:String):Int = {
    queryNumbersToReadAlt(s"skip $skipcond")
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

  def queryBase[T](
    index:Int,
    go_forward:Boolean,
    force_current:Boolean=false)
    (func:Cursor=>Option[T]): Option[T] = Globals.db_lock.synchronized {
    val db = Globals.database.get.getReadableDatabase
    val (op,order) = if(go_forward){ (">=","ASC") }else{ ("<=","DESC") }
    val condbase = s"skip <= 0 AND read_order $op ?"
    // in memorization mode, there might be a case that current poem might have skip = 2 
    val (cond,args) = if(force_current){
      val c = s"(memorized = 1 AND read_order = ?) OR ($condbase)" 
      (c, Array.fill(2)(index.toString))
    }else{
      (condbase,Array(index.toString))
    }

    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num","read_order", "torifuda_reverse"),cond,args,null,null,"read_order "+order,"2")
    try{
      cursor.moveToFirst()
      func(cursor)
    }catch{
      case _:CursorIndexOutOfBoundsException =>
       return None
    }finally{
      cursor.close()
      //db.close()
    }
  }

  def queryOne(index:Int, go_forward:Boolean):Option[NumWithOrder] = {
    queryBase(index,go_forward){cursor=>
      val no_cur = NumWithOrder(cursor.getInt(0),cursor.getInt(1),cursor.getInt(2)!=0)
      return Some(no_cur)
    }
  }
  def queryTwo(index:Int,go_forward:Boolean,force_current:Boolean=false):Option[CurNext] = {
    queryBase(index,go_forward,force_current){cursor=>
      val no_cur = NumWithOrder(cursor.getInt(0),cursor.getInt(1),cursor.getInt(2)!=0)
      val roe = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
      if(go_forward && cursor.getCount == 1 && !roe.contains("NEXT")){
        val maxn = AllFuda.list.length + 1
        val no_next = NumWithOrder(maxn,maxn,false)
        return Some(CurNext(no_cur,no_next))
      }
      cursor.moveToNext()
      val no_next = NumWithOrder(cursor.getInt(0),cursor.getInt(1),cursor.getInt(2)!=0)
      return Some(CurNext(no_cur,no_next))
    }
  }

  // [0,100]
  def queryIndexFromFudaNum(fudanum:Int):Option[Int] = Globals.db_lock.synchronized{
    val db = Globals.database.get.getReadableDatabase
    rawQueryGetInt(db,0,s"SELECT read_order FROM ${Globals.TABLE_FUDALIST} WHERE skip <=0 AND num=${fudanum} LIMIT 1")
  }

  def queryNext(index:Int):Option[CurNext] = {
    queryTwo(index,true)
  }

  def queryPrev(index:Int):Option[CurNext] = {
    queryTwo(index,false)
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

  def getHaveToReadFromDBAsIntAlt(anycond:String):Set[Int] = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("num"),s"$anycond AND num > 0",null,null,null,null,null)
    cursor.moveToFirst
    val have_to_read = ( 0 until cursor.getCount ).map{ x=>
      val r = cursor.getInt(0)
      cursor.moveToNext
      r
    }.toSet
    cursor.close
    //db.close
    have_to_read
  }

  def getHaveToReadFromDBAsInt(skipcond:String):Set[Int] = {
    getHaveToReadFromDBAsIntAlt(s"skip $skipcond")
  }

  def getHaveToReadFromDBAsString(skipcond:String):Set[String] = Globals.db_lock.synchronized{
    getHaveToReadFromDBAsInt(skipcond).map{fudanum => AllFuda.list(fudanum-1)}
  }

  def getNotYetRead(
    index:Int,
    kimalist:Seq[String]=AllFuda.getKimalist
  ):Set[String] = Globals.db_lock.synchronized{
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
      val r = kimalist(cursor.getInt(0)-1)
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
  def getKimarijiAtIndex(
    fudanum:Int,
    index:Option[Int],
    kimalist:Seq[String]=AllFuda.getKimalist
    ):String = {
    val target_index = index.getOrElse(getFudanumIndex(fudanum))
    val notyetread = getNotYetRead(target_index,kimalist)
    TrieUtils.calcKimariji(notyetread,kimalist(fudanum-1))
  }
  def getAlreadyReadFromKimariji(
    fudanum:Int,
    kimari:String,
    kimalist:Seq[String]=AllFuda.getKimalist
  ):Array[(String,Int)] = Globals.db_lock.synchronized{
    val current_index = getFudanumIndex(fudanum)
    val db = Globals.database.get.getReadableDatabase
    val nums = TrieUtils.makeNumListFromKimariji(kimari,kimalist)
    if(nums.isEmpty){
      return new Array[(String,Int)](0)
    }
    val cursor = db.rawQuery(s"SELECT num,read_order FROM ${Globals.TABLE_FUDALIST} WHERE skip <=0 AND read_order < ${current_index} AND num IN (${nums.mkString(",")}) ORDER BY read_order ASC",null)
    cursor.moveToFirst
    val alreadyread = ( 0 until cursor.getCount ).map{ x=>
      val n = kimalist(cursor.getInt(0)-1)
      val r = cursor.getInt(1)
      cursor.moveToNext
      (n,r)
    }
    cursor.close()
    //db.close()
    alreadyread.toArray
  }

  def getPlayHistory(context:Context):Seq[Int] = Globals.db_lock.synchronized{
    val current_index = getCurrentIndex(context)
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.rawQuery(s"SELECT num,read_order FROM ${Globals.TABLE_FUDALIST} WHERE skip <=0 AND num >0 AND read_order <= ${current_index} ORDER BY read_order ASC",null)
    cursor.moveToFirst()
    val seq = (0 until cursor.getCount).map{ x=>
      val n = cursor.getInt(0)
      cursor.moveToNext
      n
    }.toSeq
    cursor.close()
    seq
  }

  def getKimarijis(
    fudanum:Int,
    kimalist:Seq[String]=AllFuda.getKimalist
  ):(String,String,String) = {
    val kimari_all = kimalist(fudanum-1)
    val kimari_in_fudaset = if(isBoundedByFudaset){
      FudaListHelper.getKimarijiAtIndex(fudanum,Some(-1),kimalist)
    }else{
      kimari_all
    }
    val kimari_cur = if(Utils.disableKimarijiLog){
      kimari_in_fudaset
    }else{
      FudaListHelper.getKimarijiAtIndex(fudanum,None,kimalist)
    }
    (kimari_all,kimari_cur,kimari_in_fudaset)
  }

  def chooseKarafuda():Int={
    val have_to_read = getHaveToReadFromDBAsString("= 0")
    val not_read = AllFuda.list.toSet -- have_to_read
    val kara = TrieUtils.makeKarafuda(have_to_read, not_read, 1)
    AllFuda.getFudaNum(kara.head)
  }

  def queryRandom():Option[Int] = Globals.db_lock.synchronized{
    val num_read = getOrQueryNumbersToRead()
    val num_kara = getOrQueryNumbersOfKarafuda()
    if(num_kara > 0){
      val r = Globals.rand.nextDouble * num_read.toDouble
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
    Utils.throwawayRandom()
    if(Globals.prefs.get.getBoolean("karafuda_enable",false) ||
      Globals.prefs.get.getBoolean("memorization_mode",false)
      ){
      updateSkipList(context)
    }
    val num_list = (1 to AllFuda.list.length).toList
    val shuffled = Utils.getReadOrder match {
      case Utils.ReadOrder.PoemNum => num_list
      case Utils.ReadOrder.Musumefusahose => AllFuda.sortByMusumefusahose(num_list)
      case _ => Globals.rand.shuffle(num_list)
    }
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () =>
      for ( (v,i) <- shuffled.zipWithIndex ){
        val cv = new ContentValues
        cv.put("read_order",new java.lang.Integer(i+1))
        cv.put("torifuda_reverse", new java.lang.Integer(Globals.rand.nextInt(2)))
        db.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array(v.toString))
      })
    db.close()
  }}
  def rewind(context:Context,include_cur:Boolean){ Globals.db_lock.synchronized{
    movePrev(context)
    val target_index = getCurrentIndex(context)
    shufflePartial(context, target_index, include_cur)
  }}

  def shufflePartial(context:Context, from:Int, include_cur:Boolean){ Globals.db_lock.synchronized{
    Utils.throwawayRandom()
    if(Utils.getReadOrder != Utils.ReadOrder.Shuffle){
      return
    }
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () =>{
      val op = if(include_cur){ ">=" } else { ">" }
      val cursor = db.query(Globals.TABLE_FUDALIST,Array("num","read_order"),s"read_order ${op} ?",Array(from.toString),null,null,null,null)
      if(cursor.moveToFirst){
        val num_ar = mutable.Buffer[Int]()
        val order_ar = mutable.Buffer[Int]()
        for(i <- 0 until cursor.getCount){
          num_ar.append(cursor.getInt(0))
          order_ar.append(cursor.getInt(1))
          cursor.moveToNext
        }
        cursor.close
        val shuffled = Globals.rand.shuffle(order_ar)
        for((n,i) <- num_ar.zip(shuffled)){
          val cv = new ContentValues
          cv.put("read_order",new java.lang.Integer(i))
          db.update(Globals.TABLE_FUDALIST,cv,"num = ?",Array(n.toString))
        }
      }
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
    Globals.forceRefreshPlayer = true
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

  def getOrQueryFudaNumToRead(context:Context,offset:Int):Option[(Int,Boolean)] = {
    val r = offset match{
      case 0 =>
        Globals.player.map{x=>(x.cur_num,x.cur_torifuda_reverse)}
        .orElse{
          val current_index = getCurrentIndex(context)
          queryOne(current_index, true).map{x=>(x.num,x.torifuda_reverse)}
        }
      case 1 =>
        Globals.player.map{x=>(x.next_num,x.next_torifuda_reverse)}
        .orElse{
          val current_index = getCurrentIndex(context)
          queryNext(current_index).map{x=>(x.next.num,x.next.torifuda_reverse)}
        }
      case -1 =>
        val current_index = getCurrentIndex(context)
        if(Globals.player.isEmpty && queryOne(current_index, true).isEmpty){
          // This means it is last fuda and cur_view.cur_num is None.
          // It occurs when in either one of the following condition holds.
          // (1) go last poem -> memorization mode -> click `memorized` -> swipe rightwards
          // (2) go last poem -> play the poem -> restart app
          queryOne(current_index, false).map{x=>(x.num,x.torifuda_reverse)}
        }else{
          //TODO: maybe we don't have to check memorization_mode, and always set force_current = true
          val force_current = Globals.prefs.get.getBoolean("memorization_mode",false)
          queryTwo(current_index,false,force_current).map{x=>(x.next.num,x.next.torifuda_reverse)}
        }
      case _ =>
        throw new Exception("offset must be between -1 and 2: " + offset)
    }
    // TODO: Since Reader.bothReadable is more strict than before,
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

  def resetMemorized(cond:String){ Globals.db_lock.synchronized {
    val db = Globals.database.get.getWritableDatabase
    Utils.withTransaction(db, () => {
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDALIST} SET memorized = 0 ${cond}")
    })
    db.close()
  } }

  def selectFudasetByTitle(title:String):Option[FudaSet] = {
    val db = Globals.database.get.getReadableDatabase
    val cs = db.query(Globals.TABLE_FUDASETS,Array("id","title","body","set_size"),"title = ?",Array(title),null,null,null,null)
    val res = if(cs.moveToFirst){
      Some(FudaSet(cs.getLong(0),cs.getString(1),cs.getString(2),cs.getInt(3)))
    }else{
      None
    }
    cs.close
    res
  }
  def selectFudasetAll():Array[FudaSet] = {
    val res = mutable.Buffer[FudaSet]()
    val db = Globals.database.get.getReadableDatabase
    val cs = db.query(Globals.TABLE_FUDASETS,Array("id","title","body","set_size"),null,null,null,null,"set_order ASC,id ASC",null)
    cs.moveToFirst
    for( i <- 0 until cs.getCount ){
      res += FudaSet(cs.getLong(0),cs.getString(1),cs.getString(2),cs.getInt(3))
      cs.moveToNext
    }
    cs.close
    res.toArray
  }
  def queryMergedFudaset(ids:Seq[Long]):Option[(String,Int)] = {
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.rawQuery(s"SELECT body FROM ${Globals.TABLE_FUDASETS} WHERE id IN (${ids.mkString(",")})",null)
    val kimaset = mutable.Set[String]()
    cursor.moveToFirst
    for( i <- 0 until cursor.getCount ){
      val body = cursor.getString(0)
      kimaset ++= body.split(" ")
      cursor.moveToNext
    }
    cursor.close
    TrieUtils.makeKimarijiSet(kimaset.toSeq)
  }
  def isDuplicatedFudasetTitle(title:String,is_add:Boolean,data_id:Option[Long]):Boolean = {
    if(!is_add && data_id.isEmpty){
      throw new Exception(s"this method does not accept is_add=${is_add}, data_id=${data_id}")
    }
    val fs = FudaListHelper.selectFudasetByTitle(title)
    if(!is_add && fs.nonEmpty){
      data_id != fs.map{_.id}
    }else{
      is_add && fs.nonEmpty
    }
  }
  def updateReaders(paths:Array[CharSequence]){ Globals.db_lock.synchronized {
    val wdb = Globals.database.get.getWritableDatabase
    Utils.withTransaction(wdb,() => {
      for(path <- paths){
        val cv = new ContentValues
        cv.put("path",path.toString)
        // maybe we won't need putNull()'s here
        cv.putNull("joka_upper")
        cv.putNull("joka_lower")
        wdb.insertWithOnConflict(Globals.TABLE_READERS,null,cv,SQLiteDatabase.CONFLICT_IGNORE)
      }
      // delete items not in paths
      val cond = if(paths.nonEmpty){
        val placeholders = Array.fill(paths.size){"?"}.mkString(",")
        s"path NOT IN (${placeholders})"
      }else{
        null
      }
      wdb.delete(Globals.TABLE_READERS,cond,paths.map{_.toString})
    })
    wdb.close
  }}
  def saveRestoreReadOrderJoka(prev_path:String, cur_path:String, joka_upper:Boolean, joka_lower:Boolean){ Globals.db_lock.synchronized {
    if(prev_path == cur_path){
      return
    }

    val wdb = Globals.database.get.getWritableDatabase
    val (prev_upper,prev_lower) = Utils.parseReadOrderJoka
    val cv = new ContentValues()
    cv.put("path",prev_path)
    cv.put("joka_upper",new java.lang.Integer(prev_upper))
    cv.put("joka_lower",new java.lang.Integer(prev_lower))
    wdb.replace(Globals.TABLE_READERS,null,cv)
    wdb.close
    val db = Globals.database.get.getReadableDatabase
    val cs = db.query(Globals.TABLE_READERS,Array("joka_upper","joka_lower"),"path = ?",Array(cur_path),null,null,null,null)
    val edit = Globals.prefs.get.edit
    
    val up_d = if(joka_upper){1}else{0}
    val lo_d = if(joka_lower){1}else{0}
    val (upper,lower) = if(cs.getCount > 0){
      cs.moveToFirst
      val up = if(cs.isNull(0)){up_d}else{cs.getInt(0)}
      val lo = if(cs.isNull(1)){lo_d}else{cs.getInt(1)}
      (up,lo)
    }else{
      (up_d,lo_d)
    }
    edit.putString("read_order_joka",s"upper_${upper},lower_${lower}")
    edit.commit
    cs.close
  }}

  def selectNonInternalReaders():Array[String] = {
    val res = mutable.Buffer[String]()
    val db = Globals.database.get.getReadableDatabase
    val cs = db.query(Globals.TABLE_READERS,Array("path"),"path NOT LIKE 'INT:%'",null,null,null,"path ASC",null)
    cs.moveToFirst
    for( i <- 0 until cs.getCount ){
      res += cs.getString(0)
      cs.moveToNext
    }
    cs.close
    res.toArray
  }
}

// vim: set ts=2 sw=2 et:
