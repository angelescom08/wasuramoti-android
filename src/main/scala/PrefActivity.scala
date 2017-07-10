package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.Preference
import android.content.SharedPreferences
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat

class PrefActivity extends AppCompatActivity {
  override def onCreate(state:Bundle){
    super.onCreate(state)
    Utils.initGlobals(getApplicationContext())
    if(Globals.prefs.get.getBoolean("light_theme", false)){
      setTheme(R.style.Wasuramoti_PrefTheme_Light)
    }
    setContentView(R.layout.pref_activity)
  }
}

class PrefFragment extends PreferenceFragmentCompat with SharedPreferences.OnSharedPreferenceChangeListener {
  override def onCreatePreferencesFix(state:Bundle, rootKey:String){
    addPreferencesFromResource(R.xml.pref)
  }
  override def onDisplayPreferenceDialog(pref:Preference){
    val fragment = pref match {
      case _:ReadOrderPreference => PrefWidgets.newInstance[ReadOrderPreferenceFragment](pref.getKey)
      case _ => null
    }
    if(fragment != null){
      fragment.setTargetFragment(this, 0)
      fragment.show(getFragmentManager, "android.support.v7.preference.PreferenceFragment.DIALOG")
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
          Utils.messageDialog(context,Right(R.string.conf_use_opensles_not_supported))
        }
      case _ =>
    }
    val pref = findPreference(key)
    if(pref != null && classOf[CustomPref].isAssignableFrom(pref.getClass)){
      pref.asInstanceOf[Preference with CustomPref].notifyChangedPublic()
    }
  }
}
