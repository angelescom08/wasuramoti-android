package tami.pen.wasuramoti

import _root_.android.app.Activity
import _root_.android.preference.PreferenceManager
import _root_.android.content.{Intent,Context}
import _root_.android.os.Bundle
import _root_.android.view.{View,Menu,MenuItem}
import _root_.android.widget.Button

import _root_.mita.nep.audio.OggVorbisDecoder

import scala.collection.mutable

class WasuramotiActivity extends Activity{
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
    }
    return true
  }
  override def onResume(){
    super.onResume()
    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]
    read_button.setText(FudaListHelper.makeReadIndexMessage(getApplicationContext()))
  }

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    Globals.database = Some(new DictionaryOpenHelper(getApplicationContext()))
    Globals.prefs = Some(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()))
    ReaderList.setDefaultReader(getApplicationContext())
    setContentView(R.layout.main)

    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]
    read_button.setOnClickListener(new View.OnClickListener() {
      override def onClick(v:View) {
        val maybe_reader = ReaderList.makeCurrentReader(getApplicationContext())
        maybe_reader match{
        case Some(reader) =>
          new Thread(new Runnable(){
            override def run(){
              val current_index = FudaListHelper.getCurrentIndex(context)
              val (cur_num,next_num,cur_order,next_order) = FudaListHelper.queryNext(context,current_index+1)
              var buf = new mutable.ArrayBuffer[Short]()
              var g_decoder:Option[OggVorbisDecoder] = None
              reader.withDecodedFile(cur_num,2,(wav_file,decoder) => {
                  buf ++= AudioHelper.readShortsFromFile(wav_file)
                 g_decoder = Some(decoder)
              })
              reader.withDecodedFile(next_num,1,(wav_file,decoder) => {
                 buf ++= AudioHelper.readShortsFromFile(wav_file)
              })
              println("buffer len:"+buf.length)
              val wav = new WavBuffer(buf.toArray,g_decoder.get)
              val audit = AudioHelper.makeAudioTrack(g_decoder.get,wav.bufferSize)
              wav.writeToAudioTrack(audit)
              audit.play()
          }
        }).start()
        case None => Utils.messageDialog(context,Right(R.string.reader_not_found))
        }
      }
    });
  }
}
