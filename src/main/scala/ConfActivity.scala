package karuta.hpnpwd.wasuramoti

import _root_.android.preference.PreferenceActivity
import _root_.android.os.Bundle

class ConfActivity extends PreferenceActivity with FudaSetTrait{
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    addPreferencesFromResource(R.xml.conf)
  }
}

// vim: set ts=2 sw=2 et:


