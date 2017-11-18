package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.app.Dialog
import android.content.{DialogInterface,Context}
import android.widget.{CheckBox,TextView}
import android.view.LayoutInflater
import android.text.Html
import android.text.method.LinkMovementMethod

import android.support.v7.app.AlertDialog
import android.support.v4.app.{FragmentActivity,DialogFragment,Fragment,FragmentManager}

import scala.reflect.ClassTag

object CommonDialog {

  object TargetType extends Enumeration {
    type TargetType = Value
    val ACTIVITY, FRAGMENT = Value
  }

  object DialogType extends Enumeration {
    type DialogType = Value
    val MESSAGE, CONFIRM, HTML, CHECKBOX, LIST = Value
  }

  // We get IllegalStateException when we try to commit the FragmentTransaction, or call DialogFrament#show, which actually calls commit(), after onSaveInstanceState.
  // The easy workaround is using commitAllowingStateLoss() instead of commit(), however, it is a last resort.
  // Instead, we save the state to member variable when the activity is not visible (after onPause()), and show dialog in onResumeFragments()
  // reference:
  //   https://medium.com/inloop/demystifying-androids-commitallowingstateloss-cb9011a544cc
  //   http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
  //   https://stackoverflow.com/questions/10114324/show-dialogfragment-from-onactivityresult
  //   https://stackoverflow.com/questions/8040280/how-to-handle-handler-messages-when-activity-fragment-is-paused
  trait DialogStateHandler{
    self: FragmentActivity =>
    // since we only have to show latest dialog, we don't need queue
    var commonDialogState:Bundle = null
    var isCommonDialogShowable:Boolean = false
    override def onResumeFragments(){
      self.onResumeFragments()
      isCommonDialogShowable = true
      if(commonDialogState != null){
        showMessageDialogFromBundle(commonDialogState)
      }
      commonDialogState = null
    }
    override def onPause(){
      isCommonDialogShowable = false
      self.onPause()
    }
    // this should be used for Dialog which has a possibility that to be shown when Activity is not visible. e.g. when Activity is in background, or the device is locked.
    def showMessageDialogOrEnqueue(arg:Either[String,Int]){
      val bundle = new Bundle
      val message = getStringOrResource(this,arg)
      bundle.putString("message", message)
      bundle.putSerializable("dialog_type",DialogType.MESSAGE)
      if(isCommonDialogShowable){
        showMessageDialogFromBundle(bundle)
      }else{
        commonDialogState = bundle.clone.asInstanceOf[Bundle]
      }
    }
    def showMessageDialogFromBundle(bundle:Bundle){
      val fragment = new CommonDialogFragment
      val manager = self.getSupportFragmentManager
      fragment.setArguments(bundle)
      fragment.show(manager, "common_dialog_base")
    }
    override def onSaveInstanceState(state:Bundle){
      state.putBundle("common_dialog_state",commonDialogState)
      self.onSaveInstanceState(state)
    }
    override def onCreate(state:Bundle){
      self.onCreate(state)
      if(state != null){
        commonDialogState = state.getBundle("common_dialog_state")
      }
    }
  }
  

  trait CallbackListener{
    def onCommonDialogCallback(bundle:Bundle)
  }

  trait CustomDialog extends CallbackListener{
    def customCommonDialog(bundle:Bundle, builder:AlertDialog.Builder)
  }

  trait WrappableDialog {
    var callbackListener:CallbackListener = null
    var extraArguments:Bundle = null
  }

  class DialogWrapperFragment extends DialogFragment {
    override def onCreateDialog(state:Bundle):Dialog = {
      val args = super.getArguments
      val classTag = args.getSerializable("class_tag").asInstanceOf[ClassTag[Dialog]]
      val extraArgs = args.getBundle("extra_args")
      val dialog = classTag.runtimeClass.getConstructor(classOf[Context]).newInstance(getContext).asInstanceOf[Dialog]
      val callbackTarget = args.getSerializable("callback_target").asInstanceOf[TargetType.TargetType] match {
        case TargetType.ACTIVITY => getActivity
        case TargetType.FRAGMENT => getTargetFragment
        case null => null
        case _ => null //TODO: read the same problem below
      }
      if(dialog.isInstanceOf[WrappableDialog]){
        val wrappable = dialog.asInstanceOf[WrappableDialog]
        wrappable.callbackListener = callbackTarget.asInstanceOf[CallbackListener] 
        wrappable.extraArguments = extraArgs
        
      }
      dialog
    }
  }

  // NOTE: The dialog class which uses showWrappedDialog and showWrappedDialogWithCallback method
  //       shuld be annotated by @KeepConstructor. This is maybe because we pass ClassTag as serializable,
  //       and Proguard cannot determine that we need the constructor.

  def showWrappedDialog[C <: Dialog](manager:FragmentManager,extraArgs:Bundle=new Bundle)(implicit tag:ClassTag[C]){
    wrappedDialogBase(Right(manager),extraArgs)
  }

  def showWrappedDialogWithCallback[C <: Dialog](parent:CallbackListener,extraArgs:Bundle=new Bundle)(implicit tag:ClassTag[C]){
    wrappedDialogBase(Left(parent),extraArgs)
  }

  def wrappedDialogBase[C <: Dialog](
    target:Either[CallbackListener,FragmentManager],extraArgs:Bundle=new Bundle)
  (implicit tag:ClassTag[C]){
    val fragment = new DialogWrapperFragment
    val bundle = new Bundle
    bundle.putSerializable("class_tag",tag)
    bundle.putBundle("extra_args",extraArgs)
    if(target.isLeft){
      val parent = target.left.get
      bundle.putSerializable("callback_target", parent match {
        case _:Fragment => TargetType.FRAGMENT
        case _:FragmentActivity => TargetType.ACTIVITY
      })
      if(parent.isInstanceOf[Fragment]){
        fragment.setTargetFragment(parent.asInstanceOf[Fragment], 0)
      }
    }
    val manager = target match {
      case Left(parent) => getContextAndManager(parent)._2
      case Right(manager) => manager
    }
    val name = tag.toString.toLowerCase.replaceAllLiterally(".","_")
    fragment.setArguments(bundle)
    fragment.show(manager, name)
  }

  class CommonDialogFragment extends DialogFragment {
    override def onCreateDialog(state:Bundle):Dialog = {
      val args = super.getArguments
      val callbackTarget = args.getSerializable("callback_target").asInstanceOf[TargetType.TargetType] match {
        case TargetType.ACTIVITY => getActivity
        case TargetType.FRAGMENT => getTargetFragment
        case null => null
        case _ => null //TODO: this should not match but there's MatchError report here.
                       //      we should find out how that error has be occured
      }
      val callbackBundle = Option(args.getBundle("callback_bundle")).getOrElse(new Bundle)
      val dialogType = args.getSerializable("dialog_type").asInstanceOf[DialogType.DialogType]
      val listener = if(callbackTarget != null && callbackTarget.isInstanceOf[CallbackListener]){
        if(dialogType == DialogType.CHECKBOX){
          new DialogInterface.OnClickListener(){
            override def onClick(interface:DialogInterface,which:Int){
              val dialog = interface.asInstanceOf[Dialog]
              val bundle = callbackBundle.clone.asInstanceOf[Bundle]
              val checkbox = dialog.findViewById(R.id.checkbox_dialog_checkbox).asInstanceOf[CheckBox]
              bundle.putBoolean("checked",checkbox.isChecked)
              bundle.putInt("which",which)
              callbackTarget.asInstanceOf[CallbackListener].onCommonDialogCallback(bundle)
            }
          }
        }else{
          new DialogInterface.OnClickListener(){
            override def onClick(interface:DialogInterface,which:Int){
              callbackTarget.asInstanceOf[CallbackListener].onCommonDialogCallback(callbackBundle)
            }
          }
        }
      }else{
        null
      }
      val message = args.getString("message")
      val context = getContext
      val builder = new AlertDialog.Builder(context)
      dialogType match {
        case DialogType.MESSAGE =>
          builder.setPositiveButton(android.R.string.ok,listener)
          builder.setMessage(message)
        case DialogType.CONFIRM => 
          builder.setPositiveButton(android.R.string.yes,listener)
          builder.setNegativeButton(android.R.string.no,null)
          builder.setMessage(message)
        case DialogType.HTML =>
          builder.setPositiveButton(android.R.string.ok,listener)
          val view = LayoutInflater.from(context).inflate(R.layout.general_scroll,null)
          val txtview = view.findViewById(R.id.general_scroll_body).asInstanceOf[TextView]
          txtview.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,message)))
          // this makes "<a href='...'></a>" clickable
          txtview.setMovementMethod(LinkMovementMethod.getInstance)
          builder.setView(view)
        case DialogType.CHECKBOX =>
          builder.setPositiveButton(android.R.string.ok,listener)
          builder.setNegativeButton(android.R.string.no,listener)
          val view = LayoutInflater.from(context).inflate(R.layout.general_checkbox_dialog,null)
          val vtext = view.findViewById(R.id.checkbox_dialog_text).asInstanceOf[TextView]
          val vcheckbox = view.findViewById(R.id.checkbox_dialog_checkbox).asInstanceOf[CheckBox]
          vtext.setText(message)
          vcheckbox.setText(args.getString("message_checkbox"))
          builder.setView(view)
        case DialogType.LIST =>
          builder.setNegativeButton(R.string.button_cancel,null)
          builder.setTitle(message)
          val items = context.getResources().getStringArray(args.getInt("items_id"))
          builder.setItems(items.map{_.asInstanceOf[CharSequence]},new DialogInterface.OnClickListener(){
            override def onClick(d:DialogInterface,position:Int){
              val bundle = callbackBundle.clone.asInstanceOf[Bundle]
              bundle.putInt("position",position)
              callbackTarget.asInstanceOf[CallbackListener].onCommonDialogCallback(bundle)
              d.dismiss()
            }
          })
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
  // TODO: replace Context with FragmentManager since casting Context to FragmentActivity is not always safe
  def baseDialog(dialogType:DialogType.DialogType,context:Context,message:Either[String,Int]){
    val manager = context.asInstanceOf[FragmentActivity].getSupportFragmentManager
    val fragment = new CommonDialogFragment
    val bundle = new Bundle
    bundle.putString("message", getStringOrResource(context,message))
    bundle.putSerializable("dialog_type",dialogType)
    fragment.setArguments(bundle)
    fragment.show(manager, "common_dialog_message")
  }

  def messageDialogWithCallback(
    parent:CallbackListener,
    message:Either[String,Int],
    callbackBundle:Bundle
    ){
      baseDialogWithCallback(DialogType.MESSAGE,parent,message,callbackBundle)
  }
  def confirmDialogWithCallback(
    parent:CallbackListener,
    message:Either[String,Int],
    callbackBundle:Bundle
     ){
      baseDialogWithCallback(DialogType.CONFIRM,parent,message,callbackBundle)
  }
  def generalListDialogWithCallback(
    parent:CallbackListener,
    title:Either[String,Int],
    items_id:Int,
    callbackBundle:Bundle
  ){
    val extraArgs = new Bundle
    extraArgs.putInt("items_id",items_id)
    baseDialogWithCallback(DialogType.LIST,parent,title,callbackBundle,extraArgs)

  }

  def generalCheckBoxConfirmDialogWithCallback(
    parent:CallbackListener,
    message:Either[String,Int],
    message_checkbox:Either[String,Int],
    callbackBundle:Bundle
    ){
      val extraArgs = new Bundle
      val (context,_) = getContextAndManager(parent)
      extraArgs.putString("message_checkbox",getStringOrResource(context, message_checkbox))
      baseDialogWithCallback(DialogType.CHECKBOX,parent,message,callbackBundle,extraArgs)
  }

  def getContextAndManager(parent:CallbackListener) = {
    parent match {
      case fragment:Fragment => (fragment.getContext,fragment.getFragmentManager)
      case activity:FragmentActivity => (activity,activity.getSupportFragmentManager)
    }
  }

  def baseDialogWithCallback(
    dialogType:DialogType.DialogType,
    parent:CallbackListener,
    message:Either[String,Int],
    callbackBundle:Bundle,
    extraArgs:Bundle = null
    ){
    val fragment = new CommonDialogFragment 
    val bundle = new Bundle
    val (context,manager) = getContextAndManager(parent)
    bundle.putString("message", getStringOrResource(context, message))
    bundle.putBundle("callback_bundle", callbackBundle)
    bundle.putSerializable("callback_target", parent match {
      case _:Fragment => TargetType.FRAGMENT
      case _:FragmentActivity => TargetType.ACTIVITY
    })
    bundle.putSerializable("dialog_type",dialogType)
    if(extraArgs != null){
      bundle.putAll(extraArgs)
    }
    fragment.setArguments(bundle)
    if(parent.isInstanceOf[Fragment]){
      fragment.setTargetFragment(parent.asInstanceOf[Fragment], 0)
    }
    fragment.show(manager, "common_dialog_base")
  }

}
