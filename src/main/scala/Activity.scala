package tami.pen.wasuramoti

import _root_.android.app.{Activity,AlertDialog}
import _root_.android.content.{Intent,Context,DialogInterface}
import _root_.android.database.sqlite.{SQLiteDatabase,SQLiteOpenHelper}
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.os.Bundle
import _root_.android.view.{View,Menu,MenuItem}

import _root_.java.io.{File,FileInputStream,FileOutputStream}
import _root_.mita.nep.audio.OggVorbisDecoder

object Globals {
  val TABLE_FUDASETS = "fudasets"
  val TABLE_FUDALIST = "fudalist"
  val DATABASE_NAME = "wasuramoti.db"
  val DATABASE_VERSION = 1
  var database = None:Option[DictionaryOpenHelper]
  def confirmDialog(context:Context,str:String,func:()=>Unit){
    val builder = new AlertDialog.Builder(context)
    builder.setMessage(str).setPositiveButton("YES",new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
          func()
        }
      }).setNegativeButton("NO",new DialogInterface.OnClickListener(){
        override def onClick(interface:DialogInterface,which:Int){
        }
      }).create.show()

  }
}

class DictionaryOpenHelper(context:Context) extends SQLiteOpenHelper(context,Globals.DATABASE_NAME,null,Globals.DATABASE_VERSION){
  override def onUpgrade(db:SQLiteDatabase,oldv:Int,newv:Int){
  }
  override def onCreate(db:SQLiteDatabase){
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDASETS+" (id INTEGER PRIMARY KEY, title TEXT, body TEXT);")
     db.execSQL("CREATE TABLE "+Globals.TABLE_FUDALIST+" (id INTEGER PRIMARY KEY, num INTEGER, order INTEGER, volume REAL, pitch REAL, speed REAL);")
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
    Globals.database = Some(new DictionaryOpenHelper(getApplicationContext()))

    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)
    val read_button = findViewById(R.id.read_button)
    read_button.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        println("Button Clicked")
        val IN_FILE = "yamajun_001_2.ogg"
        val asset_fd = getAssets().openFd(IN_FILE)
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
           audit.play()
        }catch{
       //   case e => println(e)
            case e => throw e
        }
      }
    });
  }
}
