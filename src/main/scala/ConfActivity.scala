package karuta.hpnpwd.wasuramoti

import _root_.android.preference.PreferenceActivity
import _root_.android.os.Bundle
import _root_.android.view.{View,ViewGroup}
import _root_.android.widget.{TextView,CheckBox,CompoundButton,LinearLayout}
import _root_.android.util.{AttributeSet}
import _root_.android.text.TextUtils
import _root_.android.content.{Context,SharedPreferences}
import _root_.android.preference.{Preference,PreferenceManager,EditTextPreference,ListPreference}

import java.util.regex.Pattern

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
    // layout_id must be <LinearLayout android:layout_height="wrap_content" ..>
    val f = (isChecked:Boolean) => {
      root_view.foreach{ root =>
        val layout = root.findViewById(layout_id)
        if(layout != null){
          val lp = layout.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
          if(isChecked){
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            layout.setVisibility(View.VISIBLE)
          }else{
            lp.height = 0
            layout.setVisibility(View.INVISIBLE)
          }
          layout.setLayoutParams(lp)
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
      case "audio_stream_type" => get_entry_from_value(R.array.conf_audio_stream_type_entryValues,R.array.conf_audio_stream_type_entries)
      case _ => value
    }
  }
}

class ConfActivity extends PreferenceActivity with WasuramotiBaseTrait {
  var listener = None:Option[SharedPreferences.OnSharedPreferenceChangeListener] // You have to hold the reference globally since SharedPreferences keeps listeners in a WeakHashMap

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    Utils.setStatusBarForLolipop(this)
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    addPreferencesFromResource(R.xml.conf)
    listener = Some(new SharedPreferences.OnSharedPreferenceChangeListener{
      override def onSharedPreferenceChanged(prefs:SharedPreferences, key:String){
        key match{
          case "read_order_each"|"reader_path"|"read_order_joka"|"joka_enable"|
            "wav_begin_read"|"wav_end_read"|"wav_span_simokami"|"wav_threshold"|
            "wav_fadeout_simo"|"wav_fadein_kami"|"fudaset" =>
            Globals.forceRefresh = true
          case "read_order" =>
            FudaListHelper.shuffleAndMoveToFirst(getApplicationContext)
            Globals.forceRefresh = true
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
        if(pref != null && classOf[PreferenceCustom].isAssignableFrom(pref.getClass)){
          pref.asInstanceOf[Preference with PreferenceCustom].notifyChangedPublic()
        }
      }
    })
    Globals.prefs.get.registerOnSharedPreferenceChangeListener(listener.get)
    findPreference("show_credits").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        val suffix = if(Romanization.is_japanese(context)){".ja"}else{""}
        val fp = context.getAssets.open("README"+suffix)
        val pat1 = Pattern.compile(".*__BEGIN_CREDITS__",Pattern.DOTALL)
        val pat2 = Pattern.compile("__END_CREDITS__.*",Pattern.DOTALL)
        val buf = TextUtils.htmlEncode(Utils.readStream(fp))
        .replaceAll("\n","<br>\n")
        .replaceAll(" ","\u00A0")
        .replaceAll("(&lt;.*?&gt;)","<b>$1</b>")
        .replaceAll("%%(.*)","<font color='#FFFF99'><i>$1</i></font>")
        .replaceAll("(https?://[a-zA-Z0-9/._%-]*)","<font color='#FFFF99'><a href='$1'>$1</a></font>")
        fp.close
        val buf2 = pat2.matcher(pat1.matcher(buf).replaceAll("")).replaceAll("")
        Utils.generalHtmlDialog(context:Context,Left(buf2))

        return false
      }
    })
    findPreference("bug_report").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        BugReport.showBugReportDialog(context)
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

