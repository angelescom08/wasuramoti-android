package karuta.hpnpwd.wasuramoti

import _root_.android.preference.PreferenceActivity
import _root_.android.os.Bundle
import _root_.android.view.View
import _root_.android.content.Context
import _root_.android.preference.{Preference,PreferenceManager}
class ConfActivity extends PreferenceActivity with FudaSetTrait{
  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.conf)
    findPreference("init_fudaset").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        Utils.confirmDialog(context,Right(R.string.confirm_init_fudaset), _ => {
          Globals.database.foreach{ db =>
            DbUtils.initializeFudaSets(getApplicationContext,db.getWritableDatabase,true)
          }
          finish()
        })
        return false
      }
    })
    findPreference("init_preference").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        Utils.confirmDialog(context,Right(R.string.confirm_init_preference), _ => {
          Globals.prefs.foreach{ p =>
            val ed = p.edit
            ed.clear
            ed.commit
          }
          PreferenceManager.setDefaultValues(getApplicationContext(),R.xml.conf,true)
          ReaderList.setDefaultReader(getApplicationContext())
          finish()
        })
        return false
      }
    })
  }
}

