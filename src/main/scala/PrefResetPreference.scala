package karuta.hpnpwd.wasuramoti

import android.support.v7.app.AlertDialog
import android.support.v7.preference.{Preference,DialogPreference,PreferenceDialogFragmentCompat,ListPreference,CheckBoxPreference,EditTextPreference}
import android.util.{Log,AttributeSet}
import android.content.Context
import android.view.{LayoutInflater,ViewGroup,View}
import android.widget.{TextView,Button}

import org.xmlpull.v1.XmlPullParser

import scala.collection.mutable

class PrefResetPreferenceFragment extends PreferenceDialogFragmentCompat with View.OnClickListener{
  import PrefKeyNumeric._
  import PrefKeyBool._
  import PrefKeyStr._
  val processorMap:Map[String,PrefProcessor] =
    PrefKeyNumeric.values.map{x:PrefKeyNumeric => (x.toString,StringPrefProcessor)}.toMap ++
    PrefKeyBool.values.map{x:PrefKeyBool => (x.key,BoolPrefProcessor)} ++ 
    PrefKeyStr.values.map{x:PrefKeyStr => (x.key,StringPrefProcessor)}

  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val context = getContext
    val inflater = LayoutInflater.from(context)
    val rootView = inflater.inflate(R.layout.pref_reset,null)
    val container = rootView.findViewById[ViewGroup](R.id.pref_reset_body)
    setupRootView(inflater,container)
    builder.setView(rootView)
    super.onPrepareDialogBuilder(builder)
  }
  override def onDialogClosed(positiveResult:Boolean){
  }

  case class AttrValue(value:String, resId:Int)

  sealed trait PrefProcessor {
    def getDefault(context:Context, resId:Int):String
    def getCurrent(key:String):String
    def reset(context:Context, key:String, defaultResId:Int)
  }

  case object StringPrefProcessor extends PrefProcessor {
    override def getDefault(context:Context, resId:Int):String = {
      if(resId == -1){
        null
      }else{
        context.getResources.getString(resId)
      }
    }
    override def getCurrent(key:String):String = {
      //TODO: get correct default value
      Globals.prefs.get.getString(key,null)
    }
    override def reset(context:Context, key:String, defaultResId:Int) {
      val edit = Globals.prefs.get.edit
      if(defaultResId == -1){
        edit.remove(key)
      }else{
        edit.putString(key, context.getResources.getString(defaultResId))
      }
      edit.commit
    }
  }

  case object BoolPrefProcessor extends PrefProcessor {
    override def getDefault(context:Context, resId:Int):String = {
      context.getResources.getBoolean(resId).toString.capitalize
    }
    override def getCurrent(key:String):String = {
      //TODO: get correct default value
      Globals.prefs.get.getBoolean(key,false).toString.capitalize
    }
    override def reset(context:Context, key:String, defaultResId:Int) {
      val edit = Globals.prefs.get.edit
      edit.putBoolean(key, context.getResources.getBoolean(defaultResId))
      edit.commit
    }
  }


  def setupRootView(inflater:LayoutInflater, container:ViewGroup){
    val context = getContext
    val parser = context.getResources.getXml(R.xml.conf)
    var event = parser.next
    while(event != XmlPullParser.END_DOCUMENT){
      if(event == XmlPullParser.START_TAG){
        val attrs = mutable.Map[String,AttrValue]()
        for(index <- 0 until parser.getAttributeCount){
          val name = parser.getAttributeName(index)
          val value = parser.getAttributeValue(index)
          val resId = parser.getAttributeResourceValue(index,-1)
          attrs.put(name, AttrValue(value,resId))
        }
        for {
             keyv <- attrs.get("key")
             proc <- processorMap.get(keyv.value)
             titlev <- attrs.get("title")
            }{
            val defResId = attrs.get("defaultValue").map{_.resId}.getOrElse(-1)
            val v = genItemView(inflater, proc, keyv.value, titlev.resId, defResId)
            container.addView(v)
        }
      }
      event = parser.next
    }
  }
  
  def genItemView(inflater:LayoutInflater, processor:PrefProcessor, key:String, titleResId:Int, defResId:Int):View = {
    val context = getContext
    val view = inflater.inflate(R.layout.pref_reset_item,null)
    val titleView = view.findViewById[TextView](R.id.pref_reset_title)
    titleView.setText(context.getResources.getString(titleResId))
    val defaultView = view.findViewById[TextView](R.id.pref_reset_default)
    val defValue = processor.getDefault(context,defResId)
    defaultView.setText(defValue)
    val currentView = view.findViewById[TextView](R.id.pref_reset_current)
    val curValue = processor.getCurrent(key)
    currentView.setTag(s"CUR_PREF_VAL::${key}")
    currentView.setText(curValue)
    if(defValue != curValue){
      // TODO: don't reuse colors from others, and define our own
      currentView.setTextColor(Utils.attrColor(context,R.attr.kimarijiLogMainColor))
    }
    val btn = view.findViewById[Button](R.id.pref_reset_exec)
    btn.setTag((key,defResId))
    btn.setOnClickListener(this)
    view
  }
  override def onClick(target:View){
    val context = getContext
    val (key,defResId) = target.getTag.asInstanceOf[(String,Int)]
    val procMaybe = processorMap.get(key)
    if(defResId == -1){
      procMaybe.foreach{ proc =>
        proc.reset(context,key,defResId)
      }
    }
    val pref = getPreference.asInstanceOf[PrefResetPreference].findPreferenceInHierarchyPublic(key)
    if(pref == null){
      // no need to sync with private memebers of Preference, so directly update shared preference
      procMaybe.foreach{ proc =>
        proc.reset(context,key,defResId)
      }
    }else{
      // we have to update private members of Preference, so call their API
      pref match {
        case p:ListPreference => {
          p.setValue(context.getResources.getString(defResId))
        }
        case p:CheckBoxPreference => {
          p.setChecked(context.getResources.getBoolean(defResId))
        }
        case p:EditTextPreference => {
          p.setText(context.getResources.getString(defResId))
        }
        case _ => {
          Log.v("wasuramoti", s"WARNING: unknown class of preference to set default:${pref.getClass}")
        }
      }
    }
    procMaybe.foreach{proc =>
      val cur = proc.getCurrent(key)
      Option(target.getRootView.findViewWithTag[TextView](s"CUR_PREF_VAL::${key}")).foreach{
        _.setText(cur)
      }
    }
  }
}
class PrefResetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  def findPreferenceInHierarchyPublic(key:String):Preference = {
    //findPreferenceInHierarchy is protected
    findPreferenceInHierarchy(key)
  }

}

