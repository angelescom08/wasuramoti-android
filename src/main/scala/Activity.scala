package tami.pen.wasuramoti

import _root_.android.app.Activity
import _root_.android.content.{Intent,Context,ContentValues}
import _root_.android.database.sqlite.{SQLiteDatabase,SQLiteOpenHelper}
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.os.Bundle
import _root_.android.view.{View,Menu,MenuItem}
import _root_.android.widget.Button

import _root_.java.io.{File,FileInputStream,FileOutputStream}
import _root_.mita.nep.audio.OggVorbisDecoder

class DictionaryOpenHelper(context:Context) extends SQLiteOpenHelper(context,Globals.DATABASE_NAME,null,Globals.DATABASE_VERSION){
  override def onUpgrade(db:SQLiteDatabase,oldv:Int,newv:Int){
  }
  override def onCreate(db:SQLiteDatabase){
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDASETS+" (id INTEGER PRIMARY KEY, title TEXT UNIQUE, body TEXT);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDALIST+" (id INTEGER PRIMARY KEY, num INTEGER UNIQUE, read_order INTEGER, skip INTEGER);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_READFILTER+" (id INTEGER PRIMARY KEY, readers_id INTEGER, num INTEGER, volume NUMERIC, pitch NUMECIR, speed NUMERIC);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_READERS+" (id INTEGER PRIMARY KEY, path TEXT);")
     val cv = new ContentValues()
     cv.put("skip",new java.lang.Integer(0))
     Utils.withTransaction(db, () => 
       for( i  <- 0 to AllFuda.list.length){
         cv.put("num",new java.lang.Integer(i))
         cv.put("read_order",new java.lang.Integer(i))
         db.insert(Globals.TABLE_FUDALIST,null,cv)
       })
  }
}

class WasuramotiActivity extends Activity {
  override def onCreateOptionsMenu(menu: Menu) : Boolean = {
    super.onCreateOptionsMenu(menu)
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.main, menu)
    return true
  }
  override def onOptionsItemSelected(item: MenuItem) : Boolean = {
    item.getItemId match {
      case R.id.menu_restart => println("restart")
      case R.id.menu_move => println("move")
      case R.id.menu_fudaconf =>
        val intent = new Intent(this,classOf[tami.pen.wasuramoti.FudaConfActivity])
        startActivity(intent)
      case R.id.menu_config => println("config")
    }
    return true
  }
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    Globals.database = Some(new DictionaryOpenHelper(getApplicationContext()))
    setContentView(R.layout.main)
    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]

    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDALIST,Array("count(*)"),"skip=0",null,null,null,null,null)
    cursor.moveToFirst()
    val count = cursor.getInt(0)
    cursor.close()
    db.close()
    read_button.setText("Numbers to Read:" + count)

    read_button.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        println("Button Clicked")
        FudaListHelper.shuffle(getApplicationContext())
        println(FudaListHelper.queryRandom(getApplicationContext()))
        println(FudaListHelper.queryRandom(getApplicationContext()))
        println(FudaListHelper.queryRandom(getApplicationContext()))
        println(FudaListHelper.queryRandom(getApplicationContext()))
        println(FudaListHelper.queryRandom(getApplicationContext()))
        FudaListHelper.moveToFirst(getApplicationContext())
        println(FudaListHelper.queryCurrentIndexWithSkip(getApplicationContext()))
        println(FudaListHelper.queryNext(getApplicationContext(),FudaListHelper.getCurrentIndex(getApplicationContext())))
        FudaListHelper.moveNext(getApplicationContext())
        println(FudaListHelper.queryCurrentIndexWithSkip(getApplicationContext()))
        println(FudaListHelper.queryNext(getApplicationContext(),FudaListHelper.getCurrentIndex(getApplicationContext())))
        FudaListHelper.moveNext(getApplicationContext())
        println(FudaListHelper.queryCurrentIndexWithSkip(getApplicationContext()))
        println(FudaListHelper.queryNext(getApplicationContext(),FudaListHelper.getCurrentIndex(getApplicationContext())))
        FudaListHelper.moveNext(getApplicationContext())
        println(FudaListHelper.queryCurrentIndexWithSkip(getApplicationContext()))
        println(FudaListHelper.queryNext(getApplicationContext(),FudaListHelper.getCurrentIndex(getApplicationContext())))
        val IN_FILE = "reader/yamajun/yamajun_001_2.ogg"
        val asset_fd = getAssets().openFd(IN_FILE)
        for( j <- getAssets().list("reader")){
          println("assets:"+j)
        }
        println("assetssize:"+getAssets.list("reader").length)
        val finstream = asset_fd.createInputStream()
        val temp_dir = getApplicationContext().getCacheDir()
        val temp_file = File.createTempFile("wasuramoti_up",".ogg",temp_dir)
        new FileOutputStream(temp_file).getChannel().transferFrom(
          finstream.getChannel(), 0, asset_fd.getLength()
        )
        finstream.close()
        val wav_file = File.createTempFile("wasuramoti_up",".wav",temp_dir)
        val hoobar = new OggVorbisDecoder()
        hoobar.decode(temp_file.getAbsolutePath(),wav_file.getAbsolutePath())
        println("Ogg Info: " + hoobar.channels + ", " + hoobar.rate + ", " + hoobar.max_amplitude)
        val conf_channels = if(hoobar.channels==1){AudioFormat.CHANNEL_CONFIGURATION_MONO}else{AudioFormat.CHANNEL_CONFIGURATION_STEREO}
        try{

           val audit = new AudioTrack( AudioManager.STREAM_VOICE_CALL,
              hoobar.rate.toInt,
              conf_channels,
              AudioFormat.ENCODING_PCM_16BIT,
              wav_file.length.toInt,
              AudioTrack.MODE_STATIC );
           val bar = new Array[Byte](wav_file.length.toInt)
           val fin = new FileInputStream(wav_file)
           fin.read(bar) 
           fin.close()
           var offset = 0
           var len = 0
           do{
             len = audit.write(bar,offset,bar.length-offset)
             offset += len
           }while( len > 0 && offset < bar.length)
           audit.setNotificationMarkerPosition(wav_file.length.toInt / 2 - 22050)
           audit.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener(){
           override def onPeriodicNotification(audio_track:AudioTrack){
             println("Periodic Notification")
           }
           override def onMarkerReached(audio_track:AudioTrack){
             println("Marker Reached")
             println(audio_track.getPlaybackHeadPosition)
             println(wav_file.length)
           }
             })
           audit.play()
        }catch{
       //   case e => println(e)
            case e => throw e
        }
      }
    });
  }
}
