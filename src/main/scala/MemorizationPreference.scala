package karuta.hpnpwd.wasuramoti

import android.support.v7.preference.{PreferenceDialogFragmentCompat,DialogPreference}
import android.os.Bundle
import android.content.Context
import android.util.AttributeSet
import android.text.TextUtils
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,CheckBox,EditText}

class MemorizationPreferenceFragment extends PreferenceDialogFragmentCompat with CommonDialog.CallbackListener{
  val TAG_PANEL_ALL = "panel_all"
  val TAG_PANEL_FUDASET = "panel_fudaset"
  var root_view = None:Option[View]
  def getWidgets(view:View) = {
    val enable = view.findViewById[CheckBox](R.id.memorization_mode_enable)
    enable
  }
  override def onCommonDialogCallback(bundle:Bundle){
    bundle.getString("tag") match {
      case "reset_memorized" => 
        val context = getContext
        val reset_cond = bundle.getString("reset_cond")
        FudaListHelper.resetMemorized(reset_cond)
        FudaListHelper.updateSkipList(context)
        CommonDialog.messageDialog(context,Right(R.string.memorization_mode_reset_done))
        setMemCountAll()
      case "set_mem_count_all" =>
        setMemCountAll()
    }
  }
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[MemorizationPreference]
    if(positiveResult){
      val context = getContext // this will be null when call onDialogClosed was call by screen rotate
      root_view.foreach{ view =>
        val edit = pref.getSharedPreferences.edit
        val enable = getWidgets(view)
        val is_c = enable.isChecked
        edit.putBoolean(pref.getKey,is_c)
        if(is_c){
          if(!YomiInfoUtils.showPoemText){
            YomiInfoUtils.setPoemTextVisibility(edit,true)
            CommonDialog.messageDialog(context,Right(R.string.memorization_warn_yomi_info_view))
          }
        }
        edit.commit
        pref.notifyChangedPublic
        Globals.forceReloadUI = true
        FudaListHelper.updateSkipList(context)
      }
    }
  }

  def setMemCount(panel:View,onlyInFudaset:Boolean) = {
    val (count,count_not) = if(onlyInFudaset){
      val c = FudaListHelper.queryNumbersToRead("= 2")
      val cn = FudaListHelper.queryNumbersToRead("= 0")
      (c,cn)
    }else{
      val c = FudaListHelper.queryNumbersToReadAlt("memorized = 1")
      val cn = FudaListHelper.queryNumbersToReadAlt("memorized = 0")
      (c,cn)
    }
    panel.findViewById[TextView](R.id.memorization_panel_memorized_count).setText(count.toString)
    panel.findViewById[TextView](R.id.memorization_panel_not_memorized_count).setText(count_not.toString)
  }
  def setMemCountAll(){
    root_view.foreach{rv =>
      Option(rv.findViewWithTag[View](TAG_PANEL_FUDASET)).foreach{ panel =>
        setMemCount(panel,true)
      }
      Option(rv.findViewWithTag[View](TAG_PANEL_ALL)).foreach{ panel =>
        setMemCount(panel,false)
      }
    }
  }

  def genPanel(onlyInFudaset:Boolean):View = {
    val context = getContext
    val (tag,title,reset_cond) = if(onlyInFudaset){
      val tt = "** " + Globals.prefs.get.getString("fudaset","") + " **"
      val rc = "WHERE skip = 2"
      (TAG_PANEL_FUDASET,tt,rc)
    }else{
      val tt = context.getResources.getString(R.string.memorization_all_poem)
      val rc = ""
      (TAG_PANEL_ALL,tt,rc)
    }

    val panel = LayoutInflater.from(context).inflate(R.layout.memorization_panel,null)
    panel.setTag(tag)
    setMemCount(panel,onlyInFudaset)

    val fragment = this
    panel.findViewById[View](R.id.memorization_panel_save_memorized).setOnClickListener(new View.OnClickListener(){
      override def onClick(view:View){
        if(panel.findViewById[TextView](R.id.memorization_panel_memorized_count).getText.toString.toInt > 0){
          MemorizationFudaSetDialog.show(fragment,onlyInFudaset,true,reset_cond)
        }else{
          CommonDialog.messageDialog(context,Right(R.string.memorization_fudaset_empty))
        }
      }
    })
    
    panel.findViewById[View](R.id.memorization_panel_save_not_memorized).setOnClickListener(new View.OnClickListener(){
      override def onClick(view:View){
        if(panel.findViewById[TextView](R.id.memorization_panel_not_memorized_count).getText.toString.toInt > 0){
          MemorizationFudaSetDialog.show(fragment,onlyInFudaset,false,reset_cond)
        }else{
          CommonDialog.messageDialog(context,Right(R.string.memorization_fudaset_empty))
        }
      }
    })
    
    panel.findViewById[View](R.id.memorization_panel_reset_memorized).setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          val bundle = new Bundle
          bundle.putString("tag","reset_memorized")
          bundle.putString("reset_cond",reset_cond)
          CommonDialog.confirmDialogWithCallback(fragment,Right(R.string.memorization_mode_reset_confirm),bundle)
        }
    })

    val title_view = panel.findViewById[TextView](R.id.memorization_panel_title)
    title_view.setText(title)

    panel
  }

  override def onCreateDialogView(context:Context):View = {
    val pref = getPreference.asInstanceOf[MemorizationPreference]
    val view = LayoutInflater.from(context).inflate(R.layout.memorization_conf,null)
    root_view = Some(view)
    val enable = getWidgets(view)
    val memorization_mode = pref.getPersistedBoolean(false)
    enable.setChecked(memorization_mode)

    val container = view.findViewById[ViewGroup](R.id.memorization_panel_container)
    if(memorization_mode && FudaListHelper.isBoundedByFudaset){
      // We only show the panel which is bounded by poem set only when memorization mode is turned on.
      // That is because `skip` column is not set to 2 when memorization mode is off.
      // TODO: show the following panel also when memorization mode is on.
      container.addView(genPanel(true))
    }
    container.addView(genPanel(false))
    view
  }
}

class MemorizationPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref{
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String={
    if(getPersistedBoolean(false)){
      context.getResources.getString(R.string.message_enabled)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
}
object MemorizationFudaSetDialog {
  def show(target:CommonDialog.CallbackListener,onlyInFudaset:Boolean,memorized:Boolean,resetCond:String){
    val extraArgs = new Bundle
    extraArgs.putBoolean("only_in_fudaset",onlyInFudaset)
    extraArgs.putBoolean("memorized",memorized)
    extraArgs.putString("reset_cond",resetCond)
    CommonDialog.showWrappedDialogWithCallback[MemorizationFudaSetDialog](target,extraArgs)
  }
}

@KeepConstructor
class MemorizationFudaSetDialog(context:Context)
  extends CustomAlertDialog(context) with CommonDialog.WrappableDialog{
  // we have to get from extraArguments lazily since it is set after constructor is called
  lazy val onlyInFudaset = extraArguments.getBoolean("only_in_fudaset")
  lazy val memorized = extraArguments.getBoolean("memorized")
  lazy val resetCond = extraArguments.getString("reset_cond")

  override def doWhenClose():Boolean = {
    val title_view = findViewById[EditText](R.id.memorization_fudaset_name)
    val title = title_view.getText.toString
    if(TextUtils.isEmpty(title)){
      CommonDialog.messageDialog(context,Right(R.string.fudasetedit_titleempty))
      return false
    }
    val ids = (onlyInFudaset,memorized) match{
      case (true,true) =>
        FudaListHelper.getHaveToReadFromDBAsInt("= 2")
      case (true,false) =>
        FudaListHelper.getHaveToReadFromDBAsInt("= 0")
      case (false,true) =>
        FudaListHelper.getHaveToReadFromDBAsIntAlt("memorized = 1")
      case (false,false) =>
        FudaListHelper.getHaveToReadFromDBAsIntAlt("memorized = 0")
    }
    TrieUtils.makeKimarijiSetFromNumList(ids.toSeq) match{
      case None =>
        // do nothing, this should not happen
        return false
      case Some((kimari,st_size))=>
        Utils.writeFudaSetToDB(context,title,kimari,st_size)
        if(Option(findViewById[CheckBox](R.id.memorization_fudaset_reset)).exists{_.isChecked}){
          FudaListHelper.resetMemorized(resetCond)
          FudaListHelper.updateSkipList(context)
        }
        val bundle = new Bundle
        bundle.putString("tag","set_mem_count_all")
        callbackListener.onCommonDialogCallback(bundle)
        CommonDialog.messageDialog(context,Right(R.string.memorization_fudaset_created))
        return true
    }
  }
  override def onCreate(state:Bundle){
    val root = LayoutInflater.from(context).inflate(R.layout.memorization_fudaset, null)
    val title_id = if(memorized){
      R.string.memorization_fudaset_title_memorized
    }else{
      R.string.memorization_fudaset_title_notyet
    }
    setTitle(title_id)

    val cb = root.findViewById[CheckBox](R.id.memorization_fudaset_reset)
    if(memorized){
      cb.setChecked(true)
    }else{
      cb.setChecked(false)
      cb.setVisibility(View.GONE)
    }

    setView(root)
    super.onCreate(state) 
  }
}

