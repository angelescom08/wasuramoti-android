package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.app.Dialog
import android.content.{DialogInterface,Context}
import android.widget.{CheckBox,TextView}
import android.view.LayoutInflater
import android.text.Html
import android.text.method.LinkMovementMethod

import android.support.v7.app.AlertDialog
import android.support.v4.app.{FragmentActivity,DialogFragment,Fragment}

object CommonDialog {
  trait CallBackListener {
    def onCommonDialogCallback(bundle:Bundle)
  }

  class MessageDialogFragment extends DialogFragment {
    override def onCreateDialog(state:Bundle):Dialog = {
      val args = super.getArguments
      val message = args.getString("message")
      val builder = new AlertDialog.Builder(getContext)
      val listener = if(args.containsKey("callback_bundle")){
        val callbackBundle = args.getBundle("callback_bundle")
        new DialogInterface.OnClickListener(){
          override def onClick(interface:DialogInterface,which:Int){
            getTargetFragment.asInstanceOf[CallBackListener].onCommonDialogCallback(callbackBundle)
          }
        }
      }else{
        null
      }
      builder
        .setMessage(message)
        .setPositiveButton(android.R.string.ok,listener)
        .create
    }
  }
  class ConfirmDialogFragment extends DialogFragment {
    override def onCreateDialog(state:Bundle):Dialog = {
      val args = super.getArguments
      val message = args.getString("message")
      val callbackBundle = args.getBundle("callback_bundle")
      new AlertDialog.Builder(getContext)
        .setMessage(message)
        .setPositiveButton(android.R.string.yes,new DialogInterface.OnClickListener(){
          override def onClick(interface:DialogInterface,which:Int){
            getTargetFragment.asInstanceOf[CallBackListener].onCommonDialogCallback(callbackBundle)
          }
        })
        .setNegativeButton(android.R.string.no,null)
        .create
    }
  }

  def getStringOrResource(context:Context,arg:Either[String,Int]):String = {
    arg match {
      case Left(x) => x
      case Right(x) => context.getResources.getString(x)
    }
  }
  def messageDialog(context:Context,message:Either[String,Int],callbackBundle:Option[Bundle]=None){
    val manager = context.asInstanceOf[FragmentActivity].getSupportFragmentManager
    val fragment = new MessageDialogFragment
    val bundle = new Bundle
    bundle.putString("message", getStringOrResource(context,message))
    callbackBundle.foreach{bundle.putBundle("callback_bundle",_)}
    fragment.setArguments(bundle)
    fragment.show(manager, "common_dialog_message")
  }
  def confirmDialog(
    parent:Fragment with CallBackListener,
    message:Either[String,Int],
    callbackBundle:Bundle
  ){
    val manager = parent.getFragmentManager
    val fragment = new ConfirmDialogFragment
    val bundle = new Bundle
    bundle.putString("message", getStringOrResource(fragment.getContext, message))
    bundle.putBundle("callback_bundle", callbackBundle)
    fragment.setArguments(bundle)
    fragment.setTargetFragment(parent, 0)
    fragment.show(manager, "common_dialog_confirm")
  }
  def generalHtmlDialog(
    context:Context,
    arg:Either[String,Int],
    func_done:()=>Unit={()=>Unit},
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
  ){
    val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
    val html = getStringOrResource(context,arg)
    val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
    txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,html)))

    // this makes "<a href='...'></a>" clickable
    txtview.setMovementMethod(LinkMovementMethod.getInstance)

    val dialog = custom(new AlertDialog.Builder(context))
      .setPositiveButton(android.R.string.ok,null)
      .setView(view)
      .create
  }

  def generalCheckBoxConfirmDialog(
    context:Context,
    arg_text:Either[String,Int],
    arg_checkbox:Either[String,Int],
    func_yes:(CheckBox)=>Unit,
    custom:AlertDialog.Builder=>AlertDialog.Builder = identity
    ){
      val view = LayoutInflater.from(context).inflate(R.layout.general_checkbox_dialog,null)
      val vtext = view.findViewById(R.id.checkbox_dialog_text).asInstanceOf[TextView]
      val vcheckbox = view.findViewById(R.id.checkbox_dialog_checkbox).asInstanceOf[CheckBox]
      vtext.setText(getStringOrResource(context,arg_text))
      vcheckbox.setText(getStringOrResource(context,arg_checkbox))
      val dialog = custom(new AlertDialog.Builder(context))
        .setPositiveButton(android.R.string.ok,
          new DialogInterface.OnClickListener(){
            override def onClick(interface:DialogInterface,which:Int){
              func_yes(vcheckbox)
            }
        })
        .setNegativeButton(android.R.string.no,null)
        .setView(view)
        .create
  }

  def listDialog(
    context:Context,
    title_id:Int,
    items_id:Int,
    funcs:Array[()=>Unit]){
      val items = context.getResources().getStringArray(items_id)
      val builder = new AlertDialog.Builder(context)
      builder.setTitle(title_id)
      builder.setItems(items.map{_.asInstanceOf[CharSequence]},new DialogInterface.OnClickListener(){
          override def onClick(d:DialogInterface,position:Int){
            if(position > funcs.length){
              return
            }
            funcs(position)()
            d.dismiss()
          }
        })
      builder.setNegativeButton(R.string.button_cancel,new DialogInterface.OnClickListener(){
          override def onClick(d:DialogInterface,position:Int){
            d.dismiss()
          }
        })
  }
}
