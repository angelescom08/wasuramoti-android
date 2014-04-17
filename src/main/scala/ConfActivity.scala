package karuta.hpnpwd.wasuramoti

import _root_.android.preference.PreferenceActivity
import _root_.android.os.Bundle
import _root_.android.view.View
import _root_.android.widget.{TextView,CheckBox,CompoundButton}
import _root_.android.util.{AttributeSet,Base64}
import _root_.android.text.TextUtils
import _root_.android.content.{Context,SharedPreferences,Intent,ComponentName}
import _root_.android.content.pm.{ResolveInfo,PackageManager}
import _root_.android.preference.{Preference,PreferenceManager,EditTextPreference,ListPreference}
import _root_.android.net.Uri

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
                 "wav_threashold","wav_fadeout_simo","wav_fadein_kami","fudaset").contains(key)){
          Globals.forceRefresh = true
        }
        if(key == "hardware_accelerate"){
          // Since there is no way to disable hardware acceleration,
          // we have to restart application.
          Utils.confirmDialog(context,Right(R.string.conf_hardware_accelerate_restart_confirm),{ Unit => Utils.restartApplication(context) })
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
          Utils.restartApplication(context)
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
          Utils.restartApplication(context)
        })
        return false
      }
    })
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
        .replaceAll("%%(.*)","<font color='#CCCCFF'><i>$1</i></font>")
        .replaceAll("(https?://[a-zA-Z0-9/._%-]*)","<a href='$1'>$1</a>")
        fp.close
        val buf2 = pat2.matcher(pat1.matcher(buf).replaceAll("")).replaceAll("")
        Utils.generalHtmlDialog(context:Context,Left(buf2))

        return false
      }
    })
    findPreference("bug_report").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){
      override def onPreferenceClick(pref:Preference):Boolean = {
        if( android.os.Build.VERSION.SDK_INT < 8 ){
          Utils.messageDialog(context,Right(R.string.bug_report_not_supported))
          return false
        }
        val pm = context.getPackageManager
        val i_temp = new Intent(Intent.ACTION_VIEW)
        // dummy data to get list of activities
        i_temp.setData(Uri.parse("http://www.google.com/"))
        val list = scala.collection.JavaConversions.asScalaBuffer[ResolveInfo](pm.queryIntentActivities(i_temp,0))
        if(list.isEmpty){
          Utils.messageDialog(context,Right(R.string.browser_not_found))
        }else{
          val defaultActivities = list.filter{ info =>
            val filters = new java.util.ArrayList[android.content.IntentFilter]()
            val activities = new java.util.ArrayList[android.content.ComponentName]()
            pm.getPreferredActivities(filters,activities,info.activityInfo.packageName)
            ! activities.isEmpty
          }
          // TODO: show activity chooser
          (defaultActivities ++ list).exists{ ri =>
            val comp = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
            val intent = pm.getLaunchIntentForPackage(ri.activityInfo.packageName)
            intent.setAction(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(comp)
            val post_url = context.getResources.getString(R.string.bug_report_url)
            val bug_report = Base64.encodeToString(BugReport.createBugReport(context).getBytes("UTF-8"),Base64.DEFAULT | Base64.NO_WRAP)
            val html = context.getResources.getString(R.string.bug_report_html,post_url,bug_report)
            val dataUri = "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.getBytes("UTF-8"),Base64.DEFAULT | Base64.NO_WRAP)
            intent.setData(Uri.parse(dataUri))
            try{
              startActivity(intent)
              true
            }catch{
              case _:android.content.ActivityNotFoundException => false
            }
          }
        }
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

