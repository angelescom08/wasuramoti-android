package tami.pen.wasuramoti

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView
import _root_.android.util.Log
import _root_.android.view.View
import _root_.android.media.{AudioManager,AudioFormat,AudioTrack}

import _root_.mita.nep.audio.OggVorbisDecoder
import _root_.java.io.{File,FileInputStream,FileOutputStream}

class WasuramotiActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.mainlayout);
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