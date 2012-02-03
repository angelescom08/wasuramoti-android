package karuta.hpnpwd.wasuramoti

import _root_.android.database.sqlite.{SQLiteDatabase,SQLiteOpenHelper}
import _root_.android.content.{Context,ContentValues}

class DictionaryOpenHelper(context:Context) extends SQLiteOpenHelper(context,Globals.DATABASE_NAME,null,Globals.DATABASE_VERSION){
  def insertGoshoku(db:SQLiteDatabase){
   val cv = new ContentValues()
   Utils.withTransaction(db, () => {
     for( (name_id,list) <- AllFuda.goshoku ){
       AllFuda.makeKimarijiSetFromNumList(list).foreach(_ match {case (str,_) => {
         cv.put("title",context.getResources().getString(name_id))
         cv.put("body",str)
       }})
       db.insert(Globals.TABLE_FUDASETS,null,cv)
     }
   })
  }
  override def onUpgrade(db:SQLiteDatabase,oldv:Int,newv:Int){
    if(newv == Globals.DATABASE_VERSION){
      insertGoshoku(db)
    }
  }
  override def onCreate(db:SQLiteDatabase){
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDASETS+" (id INTEGER PRIMARY KEY, title TEXT UNIQUE, body TEXT);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDALIST+" (id INTEGER PRIMARY KEY, num INTEGER UNIQUE, read_order INTEGER, skip INTEGER);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_READFILTER+" (id INTEGER PRIMARY KEY, readers_id INTEGER, num INTEGER, volume NUMERIC, pitch NUMECIR, speed NUMERIC);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_READERS+" (id INTEGER PRIMARY KEY, path TEXT);")
     Utils.withTransaction(db, () => {
       val cv = new ContentValues()
       cv.put("skip",new java.lang.Integer(0))
       for( i  <- 0 to AllFuda.list.length){
         cv.put("num",new java.lang.Integer(i))
         cv.put("read_order",new java.lang.Integer(i))
         db.insert(Globals.TABLE_FUDALIST,null,cv)
       }
       })
     Utils.withTransaction(db, () => {
       val conds = Array[(Int,(String => Boolean))](
         R.string.fudaset_title_all -> (_ => true),
         R.string.fudaset_title_one -> (_.length() == 1))
       val cv = new ContentValues()
       for( (title_id,cond) <- conds ){
         val body = AllFuda.list.filter(cond).map(_(0).toString).toSet.toList.sortWith(AllFuda.compareMusumefusahose).foldLeft("")(_+" "+_)
         cv.put("title",context.getResources().getString(title_id))
         cv.put("body",body)
         db.insert(Globals.TABLE_FUDASETS,null,cv)
       }
       })
    insertGoshoku(db) 
  }
}
