package karuta.hpnpwd.wasuramoti

import android.preference.DialogPreference
import android.content.Context
import android.util.AttributeSet
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,CheckBox,Button}
import scala.collection.mutable

class MemorizationPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val TAG_DEFAULT_MEMORIZED = "memorized_count"
  val TAG_DEFAULT_NOT_YET = "not_memorized_count"
  val TAG_POSTFIX_ALL = "_all"
  val TAG_POSTFIX_FUDASET = "_fudaset"
  var root_view = None:Option[View]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getAbbrValue():String={
    if(getPersistedBoolean(false)){
      context.getResources.getString(R.string.message_enabled)
    }else{
      context.getResources.getString(R.string.message_disabled)
    }
  }
  def getWidgets(view:View) = {
    val enable = view.findViewById(R.id.memorization_mode_enable).asInstanceOf[CheckBox]
    enable
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      root_view.foreach{ view =>
        val edit = getEditor
        val enable = getWidgets(view)
        val is_c = enable.isChecked
        edit.putBoolean(getKey,is_c)
        if(is_c){
          var changed = false
          if(!YomiInfoUtils.showPoemText){
            YomiInfoUtils.setPoemTextVisibility(edit,true)
            changed = true
          }
          if(!Globals.prefs.get.getBoolean("yomi_info_show_info_button",true)){
            edit.putBoolean("yomi_info_show_info_button",true)
            changed = true
          }
          if(changed){
            Utils.messageDialog(context,Right(R.string.memorization_warn_yomi_info_view))
          }
        }
        edit.commit
        notifyChangedPublic
        Globals.forceRestart = true
        FudaListHelper.updateSkipList(context)
      }
    }
    super.onDialogClosed(positiveResult)
  }

  def setMemCount(view:View,onlyInFudaset:Boolean){
    val (postfix,count,count_not) = if(onlyInFudaset){
      val c = FudaListHelper.queryNumbersToRead("= 2")
      val cn = FudaListHelper.queryNumbersToRead("= 0")
      (TAG_POSTFIX_FUDASET,c,cn)
    }else{
      val c = FudaListHelper.countNumbersInFudaList("memorized = 1 AND num > 0")
      val cn = FudaListHelper.countNumbersInFudaList("memorized = 0 AND num >0")
      (TAG_POSTFIX_ALL,c,cn)
    }
    Option(view.findViewWithTag(TAG_DEFAULT_MEMORIZED+postfix)).foreach{ v =>
      v.asInstanceOf[TextView].setText(count.toString)
    }
    Option(view.findViewWithTag(TAG_DEFAULT_NOT_YET+postfix)).foreach{ v =>
      v.asInstanceOf[TextView].setText(count_not.toString)
    }
  }
  def setMemCountAll(){
    root_view.foreach{rv =>
      if(FudaListHelper.isBoundedByFudaset){
        setMemCount(rv,true)
      }
      setMemCount(rv,false)
    }
  }

  def genPanel(onlyInFudaset:Boolean):View = {
    val (postfix,reset_cond,title) = if(onlyInFudaset){
      val rc = "WHERE skip = 2"
      val tt = "** " + Globals.prefs.get.getString("fudaset","") + " **"
      (TAG_POSTFIX_FUDASET,rc,tt)
    }else{
      val rc = ""
      val tt = context.getResources.getString(R.string.memorization_all_poem)
      (TAG_POSTFIX_ALL,rc,tt)
    }

    val panel = LayoutInflater.from(context).inflate(R.layout.memorization_panel,null)
    panel.findViewWithTag(TAG_DEFAULT_MEMORIZED).setTag(TAG_DEFAULT_MEMORIZED+postfix)
    panel.findViewWithTag(TAG_DEFAULT_NOT_YET).setTag(TAG_DEFAULT_NOT_YET+postfix)
    setMemCount(panel,onlyInFudaset)

    val title_view = panel.findViewWithTag("memorization_panel_title").asInstanceOf[TextView]
    title_view.setText(title)

    val btn_reset = panel.findViewWithTag("reset_memorized").asInstanceOf[Button]
    btn_reset.setOnClickListener(new View.OnClickListener(){
        override def onClick(view:View){
          Utils.confirmDialog(context,Right(R.string.memorization_mode_reset_confirm), {()=>
            FudaListHelper.resetMemorized(reset_cond)
            FudaListHelper.updateSkipList(context)
            Utils.messageDialog(context,Right(R.string.memorization_mode_reset_done))
            setMemCountAll()
          })}})
    panel
  }

  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val view = LayoutInflater.from(context).inflate(R.layout.memorization_conf,null)
    root_view = Some(view)
    val enable = getWidgets(view)
    enable.setChecked(getPersistedBoolean(false))

    val container = view.findViewById(R.id.memorization_panel_container).asInstanceOf[ViewGroup]
    if(FudaListHelper.isBoundedByFudaset){
      container.addView(genPanel(true))
    }
    container.addView(genPanel(false))
    view
  }

}

