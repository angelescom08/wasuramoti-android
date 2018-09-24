package karuta.hpnpwd.wasuramoti

import android.support.v7.preference.{Preference,DialogPreference,PreferenceDialogFragmentCompat,PreferenceViewHolder}
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.RadioGroup


class ColorThemePreferenceFragment extends PreferenceDialogFragmentCompat {
  var root_view = None:Option[View]
  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[ColorThemePreference]
    val view = LayoutInflater.from(context).inflate(R.layout.color_theme_pref,null)
    val radio_group = view.findViewById[RadioGroup](R.id.conf_color_theme_group)
    for(item <- ColorThemeHelper.themes){
      LayoutInflater.from(context).inflate(R.layout.color_theme_pref_item,radio_group,true)
    }
    val iter = ColorThemeHelper.themes.iterator
    val curTag = pref.getSharedPreferences.getString(pref.getKey,ColorThemeHelper.defaultTheme.tag)
    GeneralRadioHelper.eachRadioText(radio_group, (view,radio) => {
      radio.foreach{r=>
        val item = iter.next
        r.setText(item.textId)
        r.setTag(item.tag)
        r.setId(item.itemId)
        val vg = view.asInstanceOf[ViewGroup]
        for ((tag,colorId) <- ColorThemeHelper.exampleTagToColorId){
          val color = Utils.getColorOfTheme(context,item.styleId,colorId)
          vg.findViewWithTag[View](tag).setBackgroundColor(color)
        }
      }
    })

    radio_group.check(ColorThemeHelper.get(curTag).itemId)
    root_view = Some(view)
    return view
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{view =>
        val pref = getPreference.asInstanceOf[ColorThemePreference]
        val edit = pref.getSharedPreferences.edit
        val radio_group = view.findViewById[RadioGroup](R.id.conf_color_theme_group)
        val tag = view.findViewById[View](radio_group.getCheckedRadioButtonId).getTag
        edit.putString(pref.getKey,tag.toString)
        edit.commit
        pref.notifyChangedPublic
      }
    }
  }
}

class ColorThemePreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String = {
    val tag = getPersistedString(ColorThemeHelper.defaultTheme.tag)
    val textId = ColorThemeHelper.get(tag).textId
    return context.getResources.getString(textId)
  }
}

case class ColorTheme(tag:String, isLight:Boolean, fillTorifuda:Boolean, itemId:Int, textId:Int, styleId:Int, prefStyleId:Int)

object ColorThemeHelper {
  val exampleTagToColorId = Seq(
    ("color_theme_example_generalBackgroundColor",R.attr.generalBackgroundColor),
    ("color_theme_example_actionBarDividerColor",R.attr.actionBarDividerColor),
    ("color_theme_example_mainActivityBorderColor",R.attr.mainActivityBorderColor),
    ("color_theme_example_poemTextMainColor",R.attr.poemTextMainColor),
    ("color_theme_example_poemTextFuriganaColor",R.attr.poemTextFuriganaColor),
    ("color_theme_example_torifudaEdgeColor",R.attr.torifudaEdgeColor)
    )


  val themes = Seq(
    ColorTheme("black",false,false,R.id.color_theme_black,R.string.color_theme_black,R.style.Wasuramoti_MainTheme_Black,R.style.Wasuramoti_PrefTheme_Black),
    ColorTheme("white",true,false,R.id.color_theme_white,R.string.color_theme_white,R.style.Wasuramoti_MainTheme_White,R.style.Wasuramoti_PrefTheme_White),
    ColorTheme("spring",true,true,R.id.color_theme_spring,R.string.color_theme_spring,R.style.Wasuramoti_MainTheme_Spring,R.style.Wasuramoti_PrefTheme_Spring),
    ColorTheme("summer",true,true,R.id.color_theme_summer,R.string.color_theme_summer,R.style.Wasuramoti_MainTheme_Summer,R.style.Wasuramoti_PrefTheme_Summer),
    ColorTheme("autumn",true,true,R.id.color_theme_autumn,R.string.color_theme_autumn,R.style.Wasuramoti_MainTheme_Autumn,R.style.Wasuramoti_PrefTheme_Autumn),
    ColorTheme("winter",false,true,R.id.color_theme_winter,R.string.color_theme_winter,R.style.Wasuramoti_MainTheme_Winter,R.style.Wasuramoti_PrefTheme_Winter)
    )
  val themesMap = themes.map{t => (t.tag,t)}.toMap
  val defaultTheme = themes(0)
  def get(tag:String):ColorTheme = {
    themesMap.getOrElse(tag,defaultTheme)
  }
  def getFromPref():ColorTheme = {
    get(Globals.prefs.map(_.getString("color_theme",defaultTheme.tag)).getOrElse(defaultTheme.tag))
  }
  def isLight():Boolean = {
    getFromPref.isLight
  }
}
