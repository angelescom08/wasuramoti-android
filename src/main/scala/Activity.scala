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
    Globals.current_reader = ReaderList.makeCurrentReader(getApplicationContext())
    AudioHelper.decodeNextReadInThread(getApplicationContext())
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
        if(Globals.current_reader.isEmpty){
          Utils.messageDialog(context,Right(R.string.reader_not_found))
          return
        }
        if(!Globals.decoder_thread.isEmpty){
          Globals.decoder_thread.get.join
        }
        val audio_track = Globals.decoded_buffer.get.writeToAudioTrack()
        audio_track.play()
      }
    });
  }
}
