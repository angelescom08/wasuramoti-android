package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater,View}

class AudioDecodeTestDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.audio_decode_test,null)
    setView(view)
    setTitle(R.string.audio_decode_test_title)
    super.onCreate(bundle)
  }
}
