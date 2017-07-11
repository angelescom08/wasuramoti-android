package karuta.hpnpwd.wasuramoti

import android.preference.PreferenceActivity
import android.os.Bundle
import android.view.{View,ViewGroup}
import android.widget.{TextView,CheckBox,CompoundButton,LinearLayout}
import android.util.{AttributeSet}
import android.text.TextUtils
import android.content.{Context,SharedPreferences,Intent}
import android.preference.{Preference,EditTextPreference,ListPreference}

import java.util.regex.Pattern

class ConfActivity extends PreferenceActivity with WasuramotiBaseTrait with RequirePermissionTrait{

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    Utils.initGlobals(getApplicationContext())
    if(Globals.prefs.get.getBoolean("light_theme", false)){
      setTheme(R.style.Wasuramoti_MainTheme_Light)
    }
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    addPreferencesFromResource(R.xml.conf)
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
        .replaceAll("%%(.*)","<font color='?attr/creditSpecialColor'><i>$1</i></font>")
        .replaceAll("(https?://[a-zA-Z0-9/._%-]*)","<font color='?attr/creditSpecialColor'><a href='$1'>$1</a></font>")
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

  override def onPause(){
    super.onPause()
    // We have to close all the dialog to avoid window leak
    // without this, window leak occurs when rotating the device when dialog is shown.
    Utils.dismissAlertDialog()
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
}

