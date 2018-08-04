package karuta.hpnpwd.wasuramoti

import android.app.Dialog
import android.content.{Context,DialogInterface}
import android.os.Bundle
import android.view.{View,LayoutInflater}
import android.widget.{TextView,EditText}
import android.text.TextUtils

import android.support.v4.app.DialogFragment

import java.io.File

class ScanReaderConfDialogFragment extends DialogFragment {
  val self = this
  override def onCreateDialog(state:Bundle):Dialog = {
    new ScanReaderConfDialog(getContext)
  }
  class ScanReaderConfDialog(context:Context) extends CustomAlertDialog(context){
    override def doWhenClose():Boolean = {
      val pathForm = findViewById[EditText](R.id.scan_reader_additional)
      val path = pathForm.getText.toString
      if(TextUtils.isEmpty(path) || new File(path).isDirectory){
        val edit = Globals.prefs.get.edit
        edit.putString("scan_reader_additional",path)
        edit.commit
        val reqFragment = RequirePermission.getFragment(self.getFragmentManager)
        if(reqFragment.checkRequestMarshmallowPermission(RequirePermission.REQ_PERM_PREFERENCE_SCAN)){
          ReaderList.showReaderListPref(getFragmentManager,true)
        }
        return true
      }else{
        CommonDialog.messageDialog(context,Right(R.string.scan_reader_invalid_path))
        return false
      }
    }
    override def onCreate(bundle:Bundle){
      val view = LayoutInflater.from(context).inflate(R.layout.scan_reader_conf,null)
      setView(view)
      setTitle(R.string.scan_reader_title)
      
      val desc = context.getResources.getString(R.string.scan_reader_description,Globals.READER_SCAN_DEPTH_MAX.toString)
      view.findViewById[TextView](R.id.scan_reader_description).setText(desc)
      val list = Utils.getAllExternalStorageDirectories(context).map{_.toString}.mkString("\n")
      val tv = view.findViewById[TextView](R.id.scan_reader_list)
      tv.setText(list)
      tv.setTextColor(Utils.attrColor(context,R.attr.scanReaderListColor))
      val path_form = view.findViewById[EditText](R.id.scan_reader_additional)
      path_form.setText(Globals.prefs.get.getString("scan_reader_additional","/sdcard"))

      // overwrite AlertDialog so that it does not close dialog on button click
      // Reference: https://github.com/android/platform_frameworks_base/blob/master/core/java/com/android/internal/app/AlertController.java
      // tell AlertDialog to show the buttons
      setButton(DialogInterface.BUTTON_NEUTRAL,context.getResources.getString(R.string.button_help),null.asInstanceOf[DialogInterface.OnClickListener])
      
      // the button instance will be generated in AlertDialog.onCreate()
      super.onCreate(bundle)

      // overwrite the button's behavior
      val neutral = findViewById[View](android.R.id.button3)
      neutral.setOnClickListener(new View.OnClickListener{
        override def onClick(v:View){
          CommonDialog.generalHtmlDialog(context,Right(R.string.how_to_add_reader_html))
        }
      })
    }
  }
}

