package karuta.hpnpwd.wasuramoti

import android.database.sqlite.{SQLiteDatabase,SQLiteOpenHelper}
import android.content.{Context,ContentValues}

object DbUtils{
  def initializeFudaSets(context:Context,db:SQLiteDatabase,delete_all:Boolean=false){
    if(delete_all){
      db.delete(Globals.TABLE_FUDASETS,"1",null)
    }
    insertAllAndGoshoku(context,db)
  }
  def insertAllAndGoshoku(context:Context,db:SQLiteDatabase){
    val cv = new ContentValues()
    Utils.withTransaction(db, () => {
      for( ((name_id,list),index) <-(Seq((R.string.fudaset_title_all,1 to 100)) ++ AllFuda.goshoku).zipWithIndex ){
        TrieUtils.makeKimarijiSetFromNumList(list).foreach(_ match {case (str,_) => {
          cv.put("title",context.getResources().getString(name_id))
          cv.put("body",str)
          cv.put("set_size",new java.lang.Integer(list.length))
          cv.put("set_order",new java.lang.Integer(index+1))
        }})
        db.insert(Globals.TABLE_FUDASETS,null,cv)
      }
    })
  }
}

class DictionaryOpenHelper(context:Context) extends SQLiteOpenHelper(context,Globals.DATABASE_NAME,null,Globals.DATABASE_VERSION){
  // TODO: use string interpolation
  override def onUpgrade(db:SQLiteDatabase,oldv:Int,newv:Int){
    if(oldv < 3){
      Utils.withTransaction(db, () => {
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_FUDASETS} ADD COLUMN set_size INTEGER;")
        val cursor = db.query(Globals.TABLE_FUDASETS,Array("id","body"),null,null,null,null,null,null)
        cursor.moveToFirst
        for( i <- 0 until cursor.getCount ){
          val id = cursor.getLong(0)
          val body = cursor.getString(1)
          val num = TrieUtils.makeHaveToRead(body).size
          db.execSQL(s"UPDATE ${Globals.TABLE_FUDASETS} SET set_size = " + num + " WHERE id = " + id + ";")
          cursor.moveToNext
        }
        cursor.close
      })
    }
    if(oldv < 4){
      Utils.withTransaction(db, () => {
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_FUDALIST} ADD COLUMN memorized INTEGER;")
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDALIST} SET memorized=0;")
      })
    }
    if(oldv < 5){
      Utils.withTransaction(db, () => {
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_FUDASETS} ADD COLUMN set_order INTEGER;")
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDASETS} SET set_order=id;")
      })
    }
    if(oldv < 6){
      Utils.withTransaction(db, () => {
        // we dont have to do UPDATE .. SET new columns since this table was never used
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_READERS} ADD COLUMN joka_upper INTEGER;")
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_READERS} ADD COLUMN joka_lower INTEGER;")
        db.execSQL(s"CREATE UNIQUE INDEX unique_readers_path ON ${Globals.TABLE_READERS} (path);")
      })
    }
    if(oldv < 7){
      Utils.withTransaction(db, () => {
        db.execSQL(s"ALTER TABLE ${Globals.TABLE_FUDALIST} ADD COLUMN torifuda_reverse INTEGER;")
        db.execSQL(s"UPDATE ${Globals.TABLE_FUDALIST} SET torifuda_reverse=0;")
      })
    }
  }
  override def onCreate(db:SQLiteDatabase){
     db.execSQL(s"CREATE TABLE ${Globals.TABLE_FUDASETS} (id INTEGER PRIMARY KEY, title TEXT UNIQUE, body TEXT, set_size INTEGER, set_order INTEGER);")
     db.execSQL(s"CREATE TABLE ${Globals.TABLE_FUDALIST} (id INTEGER PRIMARY KEY, num INTEGER UNIQUE, read_order INTEGER, skip INTEGER, memorized INTEGER, torifuda_reverse INTEGER);")
     db.execSQL(s"CREATE TABLE ${Globals.TABLE_READFILTER} (id INTEGER PRIMARY KEY, readers_id INTEGER, num INTEGER, volume NUMERIC, pitch NUMECIR, speed NUMERIC);")
     db.execSQL(s"CREATE TABLE ${Globals.TABLE_READERS} (id INTEGER PRIMARY KEY, path TEXT UNIQUE, joka_upper INTEGER, joka_lower INTEGER);")
     Utils.withTransaction(db, () => {
       val cv = new ContentValues()
       cv.put("skip",new java.lang.Integer(0))
       cv.put("memorized",new java.lang.Integer(0))
       for( i  <- 0 to AllFuda.list.length){
         cv.put("num",new java.lang.Integer(i))
         cv.put("read_order",new java.lang.Integer(i))
         db.insert(Globals.TABLE_FUDALIST,null,cv)
       }
       })
    DbUtils.initializeFudaSets(context,db)
  }
}
