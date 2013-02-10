package karuta.hpnpwd.wasuramoti

import _root_.android.preference.{PreferenceActivity,DialogPreference}
import _root_.android.os.Bundle
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{TextView,RadioGroup,RadioButton}
import _root_.android.util.AttributeSet
import _root_.android.content.{Context,SharedPreferences}
import _root_.android.preference.{Preference,PreferenceManager,EditTextPreference,ListPreference}

trait PreferenceCustom extends Preference{
  self:{ def getKey():String; def onBindView(v:View); def notifyChanged()} =>
  abstract override def onBindView(v:View) {
    v.findViewWithTag("conf_current_value").asInstanceOf[TextView].setText(getAbbrValue())
    super.onBindView(v)
  }
  def getAbbrValue():String = {
    Globals.prefs.get.getString(getKey(),"")
  }
  // We have to call notifyChanged() to to reflect the change to view.
  // However, notifyChanged() is protected method. Therefore we use this method.
  def notifyChangedPublic(){
    super.notifyChanged()
  }
}
class EditTextPreferenceCustom(context:Context,aset:AttributeSet) extends EditTextPreference(context,aset) with PreferenceCustom{
  // value of AttributeSet can only be acquired in constructor
  val unit = aset.getAttributeResourceValue(null,"unit",-1) match{
    case -1 => ""
    case x => context.getResources.getString(x)
  }
  override def getAbbrValue():String = {
    Globals.prefs.get.getString(getKey(),"") + unit
  }
}
class ListPreferenceCustom(context:Context,aset:AttributeSet) extends ListPreference(context,aset) with PreferenceCustom{
  override def getAbbrValue():String = {
    val key = getKey()
    val value = getValue()
    val resources = context.getResources
    val get_entry_from_value = (values:Int,entries:Int) => {
      val index = resources.getStringArray(values).indexOf(value)
      resources.getStringArray(entries)(index)
    }
    key match{
      case "read_order" => get_entry_from_value(R.array.conf_read_order_entryValues,R.array.conf_read_order_entries)
      case "read_order_each" => get_entry_from_value(R.array.conf_read_order_each_entryValues,R.array.conf_read_order_each_entries_abbr)
      case "audio_track_mode" => get_entry_from_value(R.array.conf_audio_track_entryValues,R.array.conf_audio_track_entries)
      case _ => value
    }
  }
}

class JokaOrderPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  // We can set defaultValue="..." in src/main/res/xml/conf.xml
  // and reflect it to actual preference by calling:
  //   PreferenceManager.setDefaultValues(context, R.xml.conf, true)
  // As for custom DialogPreference, we have to override
  // onSetInitialValue() and onGetDefaultValue() to get it to work.
  // See 'Building a Custom Preference' in android developer document: 
  //   http://developer.android.com/guide/topics/ui/settings.html#Custom
  // However this solution does not seem to work in Android 4.x+
  // Therefore we use this old dirty hack.
  val DEFAULT_VALUE = "upper_1,lower_1"
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      persistString(Array(R.id.conf_joka_upper_num,R.id.conf_joka_lower_num).map{ rid =>
        val btn = root_view.get.findViewById(rid).asInstanceOf[RadioGroup].getCheckedRadioButtonId()
        root_view.get.findViewById(btn).getTag()
      }.mkString(","))
    }
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.read_order_joka,null)
    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)
    for( t <- getPersistedString(DEFAULT_VALUE).split(",")){
      view.findViewWithTag(t).asInstanceOf[RadioButton].toggle()
    }
    return view
  }
  override def getAbbrValue():String = {
    getPersistedString(DEFAULT_VALUE).split(",").map{ x =>{
      val Array(ul,num) = x.split("_")
      context.getResources.getString(ul match{
        case "upper" => R.string.conf_upper_abbr
        case "lower" => R.string.conf_lower_abbr
      }) + num
    }}.mkString("")
  }
}

class ConfActivity extends PreferenceActivity with FudaSetTrait{
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    addPreferencesFromResource(R.xml.conf)
    listener = Some(new SharedPreferences.OnSharedPreferenceChangeListener{
      override def onSharedPreferenceChanged(sharedPreferences:SharedPreferences, key:String){
        if(Array("read_order_each","reader_path","read_order_joka","wav_span_simokami",
                 "wav_threashold","wav_fadeout_simo","wav_fadein_kami").contains(key)){
          Globals.forceRefresh = true
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

