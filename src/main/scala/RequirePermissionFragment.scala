package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.app.Activity

import android.support.v4.app.{ActivityCompat,Fragment,FragmentManager,FragmentActivity}
import android.support.v4.content.ContextCompat

object RequirePermission {
  val REQ_PERM_MAIN_ACTIVITY = 1
  val REQ_PERM_PREFERENCE_SCAN = 2
  val REQ_PERM_PREFERENCE_CHOOSE_READER = 3
  val REQ_PERM_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
  val FRAGMENT_TAG = "require_permission_fragment"

  // add headless fragment. should be called in Activity#onCreate 
  def addFragment(manager:FragmentManager,deniedMessage:Int,deniedForeverMessage:Int){
    if(manager.findFragmentByTag(FRAGMENT_TAG) == null){
      val fragment = new RequirePermissionFragment
      val bundle = new Bundle
      bundle.putInt("denied_message",deniedMessage)
      bundle.putInt("denied_forever_message",deniedForeverMessage)
      fragment.setArguments(bundle)
      manager.beginTransaction.add(fragment,FRAGMENT_TAG).commit
    }
  }
  def getFragment(manager:FragmentManager):RequirePermissionFragment = {
    return manager.findFragmentByTag(FRAGMENT_TAG).asInstanceOf[RequirePermissionFragment]
  }
  trait OnRequirePermissionCallback {
    self:FragmentActivity =>
    def onRequirePermissionGranted(requestCode:Int)
    case class ReqPermArgs(requestCode:Int, permissions:Array[String], grantResults:Array[Int])
    var ableToHandleReqPerm:Boolean = false
    var reqPermArgs:Option[ReqPermArgs] = None 
    override def onResumeFragments(){
      self.onResumeFragments()
      ableToHandleReqPerm = true
      reqPermArgs.foreach{args =>
        RequirePermission.getFragment(self.getSupportFragmentManager)
        .onRequestPermissionsResult(args.requestCode,args.permissions,args.grantResults)
        reqPermArgs = None
      }
    }
    override def onPause(){
      ableToHandleReqPerm = false
      self.onPause()
    }
    override def onRequestPermissionsResult(requestCode:Int, permissions:Array[String], grantResults:Array[Int]){
      if(ableToHandleReqPerm){
        RequirePermission.getFragment(self.getSupportFragmentManager).onRequestPermissionsResult(requestCode,permissions,grantResults)
      }else{
        reqPermArgs = Some(ReqPermArgs(requestCode, permissions, grantResults))
      }
    }
  }
}

// this fragment will be used as headless fragment
class RequirePermissionFragment extends Fragment with CommonDialog.CallbackListener{
  import RequirePermission._

  var callback:OnRequirePermissionCallback = null

  override def onAttach(activity:Activity){
    super.onAttach(activity)
    activity match {
      case c:OnRequirePermissionCallback => callback = c
      case _ => throw new IllegalArgumentException(s"${activity.getComponentName.getClassName} must implement OnRequirePermissionCallback: ")
    }
  }
  override def onDetach(){
    callback = null
    super.onDetach()
  }
  override def onCreate(state:Bundle){
    super.onCreate(state)
    setRetainInstance(true) // won't be destroyed by activity's lifecylcle
  }

  override def onCommonDialogCallback(bundle:Bundle){
    bundle.getString("tag") match {
      case "require_permission_retry" =>
        requirePermission(bundle.getInt("req_code"))
      case "show_application_settings" =>
        val intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", getContext.getPackageName, null)
        intent.setData(uri)
        startActivity(intent)
    }
  }

  def requirePermission(reqCode:Int){
    ActivityCompat.requestPermissions(getActivity,Array(REQ_PERM_PERMISSION),reqCode)
  }

  // References:
  //   https://developer.android.com/training/permissions/requesting.html
  //   http://sys1yagi.hatenablog.com/entry/2015/11/07/185539
  //   http://quesera2.hatenablog.jp/entry/2016/04/29/165124
  //   http://stackoverflow.com/questions/30719047/android-m-check-runtime-permission-how-to-determine-if-the-user-checked-nev
  def checkRequestMarshmallowPermission(reqCode:Int):Boolean = {
    if(android.os.Build.VERSION.SDK_INT < 23){
      return true
    }
    val REQ_PERM_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
    if(PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(getContext,REQ_PERM_PERMISSION)){
      if(ActivityCompat.shouldShowRequestPermissionRationale(getActivity,REQ_PERM_PERMISSION)){
         // permission was previously denied, with never ask again turned off.
         val bundle = new Bundle
         bundle.putInt("req_code", reqCode)
         bundle.putString("tag","require_permission_retry")
         CommonDialog.messageDialogWithCallback(this,Right(R.string.read_external_storage_permission_rationale),bundle)
      }else{
         // we previously never called requestPermission, or permission was denied with never ask again turned on.
         requirePermission(reqCode)
      }
      return false
    }else{
      return true
    }
  }

  // TODO: use FragmentCompat instead of ActivityCompat in support-v13 library if we determine not to support API < 13.
  // In that case, the Fragment.onRequestPermissionsResult is called instead of FragmentActivity.onRequestPermissionsResult is called
  // so you don't need to handle it in OnRequirePermissionCallback trait
  override def onRequestPermissionsResult(requestCode:Int, permissions:Array[String], grantResults:Array[Int]){
    if(!Seq(REQ_PERM_MAIN_ACTIVITY,REQ_PERM_PREFERENCE_SCAN,REQ_PERM_PREFERENCE_CHOOSE_READER).contains(requestCode)){
      return
    }

    val REQ_PERM_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
    val deniedMessage = getArguments.getInt("denied_message")
    val deniedForeverMessage = getArguments.getInt("denied_forever_message")
    for((perm,grant) <- permissions.zip(grantResults)){
      if(perm == REQ_PERM_PERMISSION){
        if(grant == PackageManager.PERMISSION_GRANTED){
          if(callback != null){
            callback.onRequirePermissionGranted(requestCode)
          }else if(requestCode == REQ_PERM_MAIN_ACTIVITY){
            Globals.forceRefreshPlayer = true
          }
        }else{
          if(ActivityCompat.shouldShowRequestPermissionRationale(getActivity,REQ_PERM_PERMISSION)){
            // permission is denied for first time, or denied with never ask again turned off
            CommonDialog.messageDialog(getContext,Right(deniedMessage))
          }else{
            // permission is denied, with never ask again turned on
            val bundle = new Bundle
            bundle.putString("tag","show_application_settings")
            CommonDialog.confirmDialogWithCallback(this,Right(deniedForeverMessage),bundle)
          }
        }
      }
    }
  }
}
