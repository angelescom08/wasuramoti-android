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

  object TargetType extends Enumeration {
    type TargetType = Value
    val ACTIVITY, FRAGMENT = Value
  }

  object DialogType extends Enumeration {
    type DialogType = Value
    val MESSAGE, CONFIRM, HTML = Value
  }


  trait CallbackListener {
    def onCommonDialogCallback(bundle:Bundle)
  }

  trait CustomDialog {
    def customCommonDialog(bundle:Bundle, builder:AlertDialog.Builder)
  }

  class CommonDialogFragment extends DialogFragment {
    override def onCreateDialog(state:Bundle):Dialog = {
      val args = super.getArguments
      val callbackTarget = args.getSerializable("callback_target").asInstanceOf[TargetType.TargetType] match {
        case TargetType.ACTIVITY => getActivity
        case TargetType.FRAGMENT => getTargetFragment
        case null => null
      }
      val callbackBundle = Option(args.getBundle("callback_bundle")).getOrElse(new Bundle)
      val listener = if(callbackTarget != null && callbackTarget.isInstanceOf[CallbackListener]){
        new DialogInterface.OnClickListener(){
          override def onClick(interface:DialogInterface,which:Int){
            callbackTarget.asInstanceOf[CallbackListener].onCommonDialogCallback(callbackBundle)
          }
        }
      }else{
        null
      }
      val message = args.getString("message")
      val builder = new AlertDialog.Builder(getContext)
      args.getSerializable("dialog_type").asInstanceOf[DialogType.DialogType] match {
        case DialogType.MESSAGE =>
          builder.setPositiveButton(android.R.string.ok,listener)
          builder.setMessage(message)
        case DialogType.CONFIRM => 
          builder.setPositiveButton(android.R.string.yes,listener)
          builder.setNegativeButton(android.R.string.no,null)
          builder.setMessage(message)
        case DialogType.HTML =>
          builder.setPositiveButton(android.R.string.ok,listener)
          val view = LayoutInflater.from(getContext).inflate(R.layout.general_scroll,null)
          val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
          txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(getContext,message)))
          // this makes "<a href='...'></a>" clickable
          txtview.setMovementMethod(LinkMovementMethod.getInstance)
          builder.setView(view)
      }
      if(callbackTarget.isInstanceOf[CustomDialog]){
        callbackTarget.asInstanceOf[CustomDialog].customCommonDialog(callbackBundle, builder)
      }
      builder.create
    }
  }

  def getStringOrResource(context:Context,arg:Either[String,Int]):String = {
    arg match {
      case Left(x) => x
      case Right(x) => context.getResources.getString(x)
    }
  }
  def messageDialog(context:Context,message:Either[String,Int]){
    baseDialog(DialogType.MESSAGE,context,message)
  }
  def generalHtmlDialog(context:Context,message:Either[String,Int]){
    baseDialog(DialogType.HTML,context,message)
  }
  def baseDialog(dialogType:DialogType.DialogType,context:Context,message:Either[String,Int]){
    val manager = context.asInstanceOf[FragmentActivity].getSupportFragmentManager
    val fragment = new CommonDialogFragment
    val bundle = new Bundle
    bundle.putString("message", getStringOrResource(context,message))
    bundle.putSerializable("dialog_type",dialogType)
    fragment.setArguments(bundle)
    fragment.show(manager, "common_dialog_message")
  }

  // TODO: use something like `Fragment with CallbackListener`, `FragmentActivity with CallbackListener`, `Fragmnet with CustomDialog` to assure type safety
  type EitherFragmentActivity = Either[Fragment,FragmentActivity]
  def messageDialogWithCallback(
    parent:EitherFragmentActivity,
    message:Either[String,Int],
    callbackBundle:Bundle
    ){
      baseDialogWithCallback(DialogType.MESSAGE,parent,message,callbackBundle)
  }
  def confirmDialogWithCallback(
    parent:EitherFragmentActivity,
    message:Either[String,Int],
    callbackBundle:Bundle
     ){
      baseDialogWithCallback(DialogType.CONFIRM,parent,message,callbackBundle)
  }
  def generalHtmlDialogWithCallback(
    parent:EitherFragmentActivity,
    message:Either[String,Int],
    callbackBundle:Bundle
    ){
      baseDialogWithCallback(DialogType.HTML,parent,message,callbackBundle)
  }

  def baseDialogWithCallback(
    dialogType:DialogType.DialogType,
    parent: EitherFragmentActivity,
    message:Either[String,Int],
    callbackBundle:Bundle
    ){
    val manager = parent match {
      case Left(fragment) => fragment.getFragmentManager
      case Right(activity) => activity.getSupportFragmentManager
    }
    val fragment = new CommonDialogFragment 
    val bundle = new Bundle
    bundle.putString("message", getStringOrResource(fragment.getContext, message))
    bundle.putBundle("callback_bundle", callbackBundle)
    bundle.putSerializable("callback_target", parent match {
      case Left(fragment) => TargetType.FRAGMENT
      case Right(activity) => TargetType.ACTIVITY
    })
    bundle.putSerializable("dialog_type",dialogType)
    fragment.setArguments(bundle)
    if(parent.isLeft){
      fragment.setTargetFragment(parent.left.get, 0)
    }
    fragment.show(manager, "common_dialog_base")
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
