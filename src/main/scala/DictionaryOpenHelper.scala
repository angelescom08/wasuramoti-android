package karuta.hpnpwd.wasuramoti

import _root_.android.database.sqlite.{SQLiteDatabase,SQLiteOpenHelper}
import _root_.android.content.{Context,ContentValues}

object DbUtils{
  def initializeFudaSets(context:Context,db:SQLiteDatabase,delete_all:Boolean=false){
    if(delete_all){
      db.delete(Globals.TABLE_FUDASETS,"1",null)
    }
    insertInits(context,db)
    insertGoshoku(context,db)
  }
  def insertGoshoku(context:Context,db:SQLiteDatabase){
    val cv = new ContentValues()
    Utils.withTransaction(db, () => {
      for( (name_id,list) <- AllFuda.goshoku ){
        AllFuda.makeKimarijiSetFromNumList(list).foreach(_ match {case (str,_) => {
          cv.put("title",context.getResources().getString(name_id))
          cv.put("body",str)
          val num = AllFuda.makeHaveToRead(str).size
          cv.put("set_size",new java.lang.Integer(num))
        }})
        db.insert(Globals.TABLE_FUDASETS,null,cv)
      }
    })
  }
  def insertInits(context:Context,db:SQLiteDatabase){
    Utils.withTransaction(db, () => {
      val conds = Array[(Int,(String => Boolean))](
        R.string.fudaset_title_all -> (_ => true),
        R.string.fudaset_title_one -> (_.length() == 1))
      val cv = new ContentValues()
      for( (title_id,cond) <- conds ){
        val body = AllFuda.list.filter(cond).map(_(0).toString).toSet.toList.sortWith(AllFuda.compareMusumefusahose).mkString(" ")
        cv.put("title",context.getResources().getString(title_id))
        cv.put("body",body)
        val num = AllFuda.makeHaveToRead(body).size
        cv.put("set_size",new java.lang.Integer(num))
        db.insert(Globals.TABLE_FUDASETS,null,cv)
      }
      })
  }
}

class DictionaryOpenHelper(context:Context) extends SQLiteOpenHelper(context,Globals.DATABASE_NAME,null,Globals.DATABASE_VERSION){
  val CREATE_FUDALIST = "CREATE TABLE "+Globals.TABLE_FUDALIST+" (id INTEGER PRIMARY KEY, num INTEGER UNIQUE, read_order INTEGER, have_to_read INTEGER);"
  override def onUpgrade(db:SQLiteDatabase,oldv:Int,newv:Int){
    if(newv == Globals.DATABASE_VERSION){
      Utils.withTransaction(db, () => {
        db.execSQL("ALTER TABLE "+Globals.TABLE_FUDALIST+" RENAME TO fudalist_old;")
        db.execSQL(CREATE_FUDALIST)
        db.execSQL("INSERT INTO "+Globals.TABLE_FUDALIST+"(id,num,read_order,have_to_read) SELECT id,num,read_order,CASE skip WHEN 1 THEN 0 ELSE 1 END FROM fudalist_old;")
        db.execSQL("DROP TABLE fudalist_old;")
        db.execSQL("ALTER TABLE "+Globals.TABLE_FUDASETS+" ADD COLUMN set_size INTEGER;")
        val cursor = db.query(Globals.TABLE_FUDASETS,Array("id","body"),null,null,null,null,null,null)
        cursor.moveToFirst
        for( i <- 0 until cursor.getCount ){
          val id = cursor.getLong(0)
          val body = cursor.getString(1)
          val num = AllFuda.makeHaveToRead(body).size
          db.execSQL("UPDATE "+Globals.TABLE_FUDASETS+" SET set_size = " + num + " WHERE id = " + id + ";")
          cursor.moveToNext
        }
        cursor.close
      })
      //does not work here. TODO: where can we do VACUUM?
      //db.execSQL("VACUUM;")
    }
  }
  override def onCreate(db:SQLiteDatabase){
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDASETS+" (id INTEGER PRIMARY KEY, title TEXT UNIQUE, body TEXT, set_size INTEGER);")
     db.execSQL(CREATE_FUDALIST)
     db.execSQL("CREATE TABLE "+Globals.TABLE_READFILTER+" (id INTEGER PRIMARY KEY, readers_id INTEGER, num INTEGER, volume NUMERIC, pitch NUMECIR, speed NUMERIC);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_READERS+" (id INTEGER PRIMARY KEY, path TEXT);")
     Utils.withTransaction(db, () => {
       val cv = new ContentValues()
       cv.put("have_to_read",new java.lang.Integer(1))
       for( i  <- 0 to AllFuda.list.length){
         cv.put("num",new java.lang.Integer(i))
         cv.put("read_order",new java.lang.Integer(i))
         db.insert(Globals.TABLE_FUDALIST,null,cv)
       }
       })
    DbUtils.initializeFudaSets(context,db)
  }
}
