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
      case R.id.menu_restart => {
        FudaListHelper.shuffle(getApplicationContext())
        FudaListHelper.moveToFirst(getApplicationContext())
      }
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
    val maybe_reader = ReaderList.makeCurrentReader(getApplicationContext())
    Globals.player = maybe_reader.flatMap(
      reader => AudioHelper.makeKarutaPlayer(getApplicationContext(),reader))
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
        if(Globals.player.isEmpty){
          Utils.messageDialog(context,Right(R.string.reader_not_found))
          return
        }
        read_button.setText(R.string.now_playing)
        Globals.player.get.play(
          _ => FudaListHelper.moveNext(getApplicationContext()),
          _ => {
            val reader = ReaderList.makeCurrentReader(getApplicationContext())
            Globals.player = AudioHelper.makeKarutaPlayer(getApplicationContext(),reader.get)
            //read_button.setText(FudaListHelper.makeReadIndexMessage(getApplicationContext()))
        }
        )
      }
    });
  }
}
