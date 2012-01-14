package tami.pen.wasuramoti

import _root_.android.app.{Activity,AlertDialog}
import _root_.android.preference.{PreferenceActivity,DialogPreference}
import _root_.android.os.Bundle
import _root_.android.widget.{ArrayAdapter,Spinner}
import _root_.android.util.{Log,AttributeSet}
import _root_.android.view.{View,Menu,MenuItem,ViewGroup}
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}
import _root_.android.content.{Intent,Context,DialogInterface}

import _root_.mita.nep.audio.OggVorbisDecoder
import _root_.java.io.{File,FileInputStream,FileOutputStream}
import _root_.java.util.ArrayList


class FudaListPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var listItems = new ArrayList[String]()
  var adapter = None:Option[ArrayAdapter[String]]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onBindDialogView(view:View){
    super.onBindDialogView(view)
    adapter = Some(new ArrayAdapter[String](context,android.R.layout.simple_spinner_item,listItems))
    val vw = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    println(vw)
    vw.setAdapter(adapter.get)
    listItems.add("hoobar")
    listItems.add("baz")
    listItems.add("hoge")
    listItems.add("fuga")
    adapter.get.notifyDataSetChanged()
  }
}

class FudaConfActivity extends PreferenceActivity{
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.fudaconf)
  }
  def addFudaList(view:View){
    val vw = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val ada = vw.getAdapter().asInstanceOf[ArrayAdapter[String]]
    val builder = new AlertDialog.Builder(this)
    builder.setCancelable(true)
    builder.setPositiveButton("OK",new DialogInterface.OnClickListener(){
      override def onClick(interface:DialogInterface,which:Int){
      }
      })
    val v = getLayoutInflater().inflate(R.layout.fuda_list_edit,null)
    builder.setView(v)
    builder.setMessage("hogefuga")
    val dialog = builder.create()
    dialog.show()
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
    setContentView(R.layout.main)
    val read_button = findViewById(R.id.read_button)
    read_button.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        println("Button Clicked")
        println(R.string.app_name)
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
