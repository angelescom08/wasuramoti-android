package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.Preference
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

class PrefFragment extends PreferenceFragmentCompat {
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
}
