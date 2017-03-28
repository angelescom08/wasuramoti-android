package karuta.hpnpwd.wasuramoti

import android.support.v4.app.DialogFragment
import android.app.{AlertDialog,Dialog}
import android.os.Bundle
import android.view.LayoutInflater

object PoemDescriptionDialog{
  def newInstance(fudanum:Option[Int]):PoemDescriptionDialog = {
    val fragment = new PoemDescriptionDialog
    val args = new Bundle
    args.putSerializable("fudanum",fudanum)
    fragment.setArguments(args)
    return fragment
  }
}

class PoemDescriptionDialog extends DialogFragment with GetFudanum{
  override def onCreateDialog(saved:Bundle):Dialog = {
    val builder = new AlertDialog.Builder(getActivity)
    // TODO: implement
    val view = LayoutInflater.from(getActivity).inflate(R.layout.poem_description,null)
    builder.create
  }
}
