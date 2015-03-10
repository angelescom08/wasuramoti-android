package karuta.hpnpwd.wasuramoti

import _root_.android.app.AlertDialog
import _root_.android.content.{Context,DialogInterface}
import _root_.android.os.Bundle
import _root_.android.view.{LayoutInflater,View}
import _root_.android.widget.{TextView,EditText}
import _root_.android.graphics.Color
import _root_.android.text.TextUtils
import _root_.java.io.File

class ScanReaderConfDialog(context:Context) extends AlertDialog(context){
  override def onCreate(bundle:Bundle){
    val view = LayoutInflater.from(context).inflate(R.layout.scan_reader_conf,null)
    setView(view)
    setTitle(R.string.scan_reader_title)
    val desc = context.getResources.getString(R.string.scan_reader_description,Globals.READER_SCAN_DEPTH_MAX.toString)
    view.findViewById(R.id.scan_reader_description).asInstanceOf[TextView].setText(desc)
    val list = Utils.getAllExternalStorageDirectories(context).map{_.toString}.mkString("\n")
    val tv = view.findViewById(R.id.scan_reader_list).asInstanceOf[TextView]
    tv.setText(list)
    tv.setTextColor(Color.YELLOW)
    val path_form = view.findViewById(R.id.scan_reader_additional).asInstanceOf[EditText]
    path_form.setText(Globals.prefs.get.getString("scan_reader_additional","/sdcard"))
    val listener = new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          which match{
            case DialogInterface.BUTTON_POSITIVE => {
              val path = path_form.getText.toString
              if(TextUtils.isEmpty(path) || new File(path).isDirectory){
                val edit = Globals.prefs.get.edit
                edit.putString("scan_reader_additional",path)
                edit.commit
                dismiss()
              }else{
                // we have to re-show this dialog since BUTTON_{POSITIVE,NEGATIVE,NEUTRAL} closes the dialog
                Utils.messageDialog(context,Right(R.string.scan_reader_invalid_path),{()=>show()})
              }
            }
            case DialogInterface.BUTTON_NEGATIVE => {
              cancel()
            }
            case DialogInterface.BUTTON_NEUTRAL => {
              Utils.generalHtmlDialog(context,Right(R.string.how_to_add_reader_html),{()=>show()})
            }
            case _ => {
            }
          }
        }
      }
    setButton(DialogInterface.BUTTON_POSITIVE,context.getResources.getString(android.R.string.ok),listener)
    setButton(DialogInterface.BUTTON_NEGATIVE,context.getResources.getString(android.R.string.cancel),listener)
    setButton(DialogInterface.BUTTON_NEUTRAL,context.getResources.getString(R.string.button_help),listener)
    super.onCreate(bundle)
  }
}
