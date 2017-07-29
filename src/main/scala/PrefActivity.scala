package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.{Preference,EditTextPreference,ListPreference,PreferenceScreen}
import android.content.{Context,SharedPreferences,Intent}
import android.util.AttributeSet
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat

class PrefActivity extends AppCompatActivity with WasuramotiBaseTrait
    with RequirePermission.OnRequirePermissionCallback
{
  override def onCreate(state:Bundle){
    super.onCreate(state)
    Utils.initGlobals(getApplicationContext())
    if(Utils.usePrefLightTheme){
      setTheme(R.style.Wasuramoti_PrefTheme_Light)
    }
    setContentView(R.layout.pref_activity)
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    RequirePermission.addFragment(getSupportFragmentManager,
      R.string.read_external_storage_permission_denied_scan,
      R.string.read_external_storage_permission_denied_forever_scan
      )
  }
  // Note: this will not be called if app was terminated in background
  override def onActivityResult(reqCode:Int, resCode:Int, data:Intent){
    super.onActivityResult(reqCode,resCode,data)
    if(reqCode == BugReport.CLEAN_PROVIDED_REQUEST){
      if(Utils.HAVE_TO_GRANT_CONTENT_PERMISSION){
        try{ 
          val file = Utils.getProvidedFile(this,Utils.ProvidedBugReport,false)
          val attachment = Utils.getProvidedUri(this,file)
          this.revokeUriPermission(attachment,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }catch{
          case _:Throwable => None
        }
      }
      Utils.cleanProvidedFile(this,true)
    }
  }
  override def onRequirePermissionGranted(requestCode:Int){
    val doScan = requestCode == RequirePermission.REQ_PERM_PREFERENCE_SCAN
    ReaderList.showReaderListPref(getSupportFragmentManager, doScan)
  }
}

object PrefFragment{
  // same as PreferenceFragmentCompat.DIALOG_FRAGMENT_TAG
  val DIALOG_FRAGMENT_TAG = "android.support.v7.preference.PreferenceFragment.DIALOG"
}

class PrefFragment extends PreferenceFragmentCompat
  with SharedPreferences.OnSharedPreferenceChangeListener
  with android.support.v7.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback {
  override def onCreatePreferencesFix(state:Bundle, rootKey:String){
    addPreferencesFromResource(R.xml.conf)
  }
  override def onDisplayPreferenceDialog(pref:Preference){
    val fragment = pref match {
      case _:ReadOrderPreference => PrefWidgets.newInstance[ReadOrderPreferenceFragment](pref.getKey)
      case _:JokaOrderPreference => PrefWidgets.newInstance[JokaOrderPreferenceFragment](pref.getKey)
      case _:AutoPlayPreference => PrefWidgets.newInstance[AutoPlayPreferenceFragment](pref.getKey)
      case _:DescriptionPreference => PrefWidgets.newInstance[DescriptionPreferenceFragment](pref.getKey)
      case _:KarafudaPreference => PrefWidgets.newInstance[KarafudaPreferenceFragment](pref.getKey)
      case _:AudioVolumePreference => PrefWidgets.newInstance[AudioVolumePreferenceFragment](pref.getKey)
      case _:EqualizerPreference => PrefWidgets.newInstance[EqualizerPreferenceFragment](pref.getKey)
      case _:ReadOrderEachPreference => PrefWidgets.newInstance[ReadOrderEachPreferenceFragment](pref.getKey)
      case _:MemorizationPreference => PrefWidgets.newInstance[MemorizationPreferenceFragment](pref.getKey)
      case _:FudaSetPreference => PrefWidgets.newInstance[FudaSetPreferenceFragment](pref.getKey)
      case _:ReaderListPreference => PrefWidgets.newInstance[ReaderListPreferenceFragment](pref.getKey)
      case _:YomiInfoPreference => PrefWidgets.newInstance[YomiInfoPreferenceFragment](pref.getKey)
      case _:BugReportPreference => PrefWidgets.newInstance[BugReportPreferenceFragment](pref.getKey)
      case _:CreditsPreference => PrefWidgets.newInstance[CreditsPreferenceFragment](pref.getKey)
      case _ => null
    }
    if(fragment != null){
      fragment.setTargetFragment(this, 0)
      fragment.show(getFragmentManager, PrefFragment.DIALOG_FRAGMENT_TAG)
    }else{
      super.onDisplayPreferenceDialog(pref)
    }
  }
  override def onResume(){
    super.onResume()
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(this)
  }
  override def onPause(){
    super.onPause()
    Globals.prefs.get.unregisterOnSharedPreferenceChangeListener(this)
  }
  override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
    val context = getContext
    key match{
      case "reader_path"|"read_order_joka"|"joka_enable"|
        "wav_begin_read"|"wav_end_read"|"wav_span_simokami"|"wav_threshold"|
        "wav_fadeout_simo"|"wav_fadein_kami"|"fudaset" =>
        Globals.forceRefreshPlayer = true
      case "show_replay_last_button" | "show_skip_button" =>
        Globals.forceReloadUI = true
      case "read_order_each" =>
        // we also have to change text of replay_last_button when read_order_each changed
        Globals.forceRefreshPlayer = true
        Globals.forceReloadUI = true
      case "read_order" =>
        FudaListHelper.shuffleAndMoveToFirst(context)
        Globals.forceRefreshPlayer = true
      case "light_theme" =>
        Globals.forceRestart = true
      case "use_opensles" =>
        if(android.os.Build.VERSION.SDK_INT <= 8 && prefs.getBoolean(key,false)){
          val edit = prefs.edit
          edit.putBoolean(key,false)
          edit.commit()
          CommonDialog.messageDialog(context,Right(R.string.conf_use_opensles_not_supported))
        }
      case _ =>
    }
    val pref = findPreference(key)
    if(pref != null && classOf[CustomPref].isAssignableFrom(pref.getClass)){
      pref.asInstanceOf[Preference with CustomPref].notifyChangedPublic()
    }
  }

  // support library does not support clicking sub-screen
  // https://stackoverflow.com/questions/34701740/preference-sub-screen-not-opening-when-using-support-v7-preference
  // the following fix does not solve the problem completely, since when back button is pressed, it closes the PrefActivity and goes back to WasuramotiActivity
  // TODO: show parent screen when back button is pressed
  override def getCallbackFragment():Fragment = {
    this
  }

  override def onPreferenceStartScreen(pref:android.support.v7.preference.PreferenceFragmentCompat,screen:PreferenceScreen):Boolean = {
    pref.setPreferenceScreen(screen)
    return true
  }
}

class EditTextPreferenceCustom(context:Context,aset:AttributeSet) extends EditTextPreference(context,aset) with CustomPref{
  // value of AttributeSet can only be acquired in constructor
  val unit = aset.getAttributeResourceValue(null,"unit",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }
  override def getAbbrValue():String = {
    getPersistedString("") + unit
  }
}
class ListPreferenceCustom(context:Context,aset:AttributeSet) extends ListPreference(context,aset) with CustomPref{
  override def getAbbrValue():String = {
    val key = getKey()
    val value = getValue()
    val resources = context.getResources
    val get_entry_from_value = (values:Int,entries:Int) => {
      try{
        val index = resources.getStringArray(values).indexOf(value)
        resources.getStringArray(entries)(index)
      }catch{
        // Upgrading from older version causes this exception since value is empty.
        case _:IndexOutOfBoundsException => ""
      }
    }
    key match{
      case "audio_stream_type" => get_entry_from_value(R.array.conf_audio_stream_type_entryValues,R.array.conf_audio_stream_type_entries)
      case _ => value
    }
  }
}

