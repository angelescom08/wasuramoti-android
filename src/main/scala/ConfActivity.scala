package karuta.hpnpwd.wasuramoti

import _root_.android.preference.{PreferenceActivity}
import _root_.android.os.Bundle
import _root_.android.app.{PendingIntent,AlarmManager}
import _root_.android.view.View
import _root_.android.widget.{TextView,CheckBox,CompoundButton}
import _root_.android.util.AttributeSet
import _root_.android.content.{Context,SharedPreferences,Intent}
import _root_.android.preference.{Preference,PreferenceManager,EditTextPreference,ListPreference}

trait PreferenceCustom extends Preference{
  self:{ def getKey():String; def onBindView(v:View); def notifyChanged()} =>
  abstract override def onBindView(v:View) {
    v.findViewWithTag("conf_current_value").asInstanceOf[TextView].setText(getAbbrValue())
    super.onBindView(v)
  }
  def getAbbrValue():String = {
    // I don't now why we cannot use getPersistedString() here
    Globals.prefs.get.getString(getKey(),"")
  }
  // We have to call notifyChanged() to to reflect the change to view.
  // However, notifyChanged() is protected method. Therefore we use this method.
  def notifyChangedPublic(){
    super.notifyChanged()
  }
  def switchVisibilityByCheckBox(root_view:Option[View],checkbox:CheckBox,layout_id:Int){
    val f = (isChecked:Boolean) => {
      root_view.foreach{ root =>
        val layout = root.findViewById(layout_id)
        if(layout != null){
          layout.setVisibility(if(isChecked){View.VISIBLE}else{View.INVISIBLE})
        }
      }
    }
    f(checkbox.isChecked)
    checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
        override def onCheckedChanged(btn:CompoundButton,isChecked:Boolean){
          f(isChecked)
        }
      })
  }
}
class EditTextPreferenceCustom(context:Context,aset:AttributeSet) extends EditTextPreference(context,aset) with PreferenceCustom{
  // value of AttributeSet can only be acquired in constructor
  val unit = aset.getAttributeResourceValue(null,"unit",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }
  override def getAbbrValue():String = {
    getPersistedString("") + unit
  }
}
class ListPreferenceCustom(context:Context,aset:AttributeSet) extends ListPreference(context,aset) with PreferenceCustom{
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
      case "read_order" => get_entry_from_value(R.array.conf_read_order_entryValues,R.array.conf_read_order_entries)
      case "read_order_each" => get_entry_from_value(R.array.conf_read_order_each_entryValues,R.array.conf_read_order_each_entries_abbr)
      case "audio_track_mode" => get_entry_from_value(R.array.conf_audio_track_entryValues,R.array.conf_audio_track_entries)
      case _ => value
    }
  }
}

class ConfActivity extends PreferenceActivity with FudaSetTrait with WasuramotiBaseTrait {
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    addPreferencesFromResource(R.xml.conf)
    listener = Some(new SharedPreferences.OnSharedPreferenceChangeListener{
      override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
        if(Array("read_order_each","reader_path","read_order_joka","wav_span_simokami",
                 "wav_threashold","wav_fadeout_simo","wav_fadein_kami").contains(key)){
          Globals.forceRefresh = true
        }
        if(key == "hardware_accelerate"){
          // Since there is no way to disable hardware acceleration,
          // we have to restart application. This way totally exits application using System.exit()
          val start_activity = new Intent(context,classOf[WasuramotiActivity])
          val pending_id = 271828
          val pending_intent = PendingIntent.getActivity(context, pending_id, start_activity, PendingIntent.FLAG_CANCEL_CURRENT)
          val mgr = context.getSystemService(Context.ALARM_SERVICE).asInstanceOf[AlarmManager]
          if(mgr != null){
            Utils.confirmDialog(context,Right(R.string.conf_hardware_accelerate_restart_confirm),
              { Unit =>
                mgr.set(AlarmManager.RTC, System.currentTimeMillis+100, pending_intent)
                System.exit(0)
              })
          }
        }
        val pref = findPreference(key)
        if(pref != null && classOf[PreferenceCustom].isAssignableFrom(pref.getClass)){
          pref.asInstanceOf[Preference with PreferenceCustom].notifyChangedPublic()
        }
      }
    })
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(listener.get)
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
  override def onDestroy(){
    super.onDestroy()
    listener.foreach{ l =>
      Globals.prefs.foreach{ p =>
        p.unregisterOnSharedPreferenceChangeListener(l)
        listener = None
      }
    }
  }
}

