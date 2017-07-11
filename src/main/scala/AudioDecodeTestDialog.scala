package karuta.hpnpwd.wasuramoti

import android.support.v7.app.AlertDialog
import android.content.Context
import android.os.{Bundle,Handler}
import android.view.{LayoutInflater,ViewGroup}
import android.widget.TextView

class AudioDecodeTestDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.audio_decode_test,null)
    setView(view)
    val container = view.findViewById(R.id.audio_decode_result_container).asInstanceOf[ViewGroup]
    val handler = new Handler
    val thread = new Thread(new Runnable(){
      override def run(){
        ReaderList.makeCurrentReader(context).foreach{reader =>
          for(num <- 0 to 100; kamisimo <- 1 to 2){
            val v = new TextView(context)
            val path = reader match{
              case r:ExtAbsBase => r.getFile(num,kamisimo).getPath
              case r:Asset => r.getAssetPath(num,kamisimo)
              case _ => s"${reader.path}_${num}_${kamisimo}"
            }
            try{
              reader.withDecodedWav(num,kamisimo,(wb)=>{
                v.setText(f"[OK] ${wb.audioLength/1000.0}%.2f sec ${path}%s")
                v.setTextColor(Utils.attrColor(context,R.attr.decodeTestOkColor))
              })
            }catch{
              case e:Exception =>
                v.setText(s"[ERROR] '${e.getClass.getSimpleName}: ${e.getMessage}' when decoding ${path}")
                v.setTextColor(Utils.attrColor(context,R.attr.decodeTestErrorColor))
            }
            handler.post(new Runnable(){
              override def run(){
                container.addView(v)
              }})
          }
        }
    }})
    thread.start()
    setTitle(R.string.audio_decode_test_title)
    super.onCreate(bundle)
  }
}
