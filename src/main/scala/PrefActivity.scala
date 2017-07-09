package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat

class PrefActivity extends AppCompatActivity {
  override def onCreate(state:Bundle){
    super.onCreate(state)
    Utils.initGlobals(getApplicationContext())
    if(Globals.prefs.get.getBoolean("light_theme", false)){
      setTheme(R.style.PreferenceFixTheme_Light)
    }
    setContentView(R.layout.pref_activity)

  }
}

class PrefFragment extends PreferenceFragmentCompat {
  override def onCreatePreferencesFix(state:Bundle, rootKey:String){
    addPreferencesFromResource(R.xml.pref)
  }
}
