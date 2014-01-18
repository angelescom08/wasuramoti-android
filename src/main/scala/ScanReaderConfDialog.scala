package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.Context
import _root_.android.os.Bundle
import _root_.android.view.View
import _root_.android.widget.{TextView,EditText}
import _root_.android.graphics.Color
import _root_.android.text.TextUtils
import _root_.java.io.File

class ScanReaderConfDialog(context:Context) extends Dialog(context){
  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.scan_reader_conf)
    setTitle(R.string.scan_reader_title)
    val desc = context.getResources.getString(R.string.scan_reader_description,Globals.READER_SCAN_DEPTH_MAX.toString)
    findViewById(R.id.scan_reader_description).asInstanceOf[TextView].setText(desc)
    val list = Utils.getAllExternalStorageDirectories.map{_.toString}.mkString("\n")
    val tv = findViewById(R.id.scan_reader_list).asInstanceOf[TextView]
    tv.setText(list)
    tv.setTextColor(Color.YELLOW)
    val path_form = findViewById(R.id.scan_reader_additional).asInstanceOf[EditText]
    path_form.setText(Globals.prefs.get.getString("scan_reader_additional","/sdcard"))
    findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
          val path = path_form.getText.toString
          if(TextUtils.isEmpty(path) || new File(path).isDirectory){
            val edit = Globals.prefs.get.edit
            edit.putString("scan_reader_additional",path)
            edit.commit
            dismiss()
          }else{
            Utils.messageDialog(context,Right(R.string.scan_reader_invalid_path))
          }
        }
      })
    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
          dismiss()
        }
      })
    findViewById(R.id.button_help).setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
          Utils.generalHtmlDialog(context,R.string.how_to_add_reader_html)
        }
      })
  }
}
