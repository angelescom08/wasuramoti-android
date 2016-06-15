package karuta.hpnpwd.wasuramoti

import android.preference.DialogPreference
import android.os.Bundle
import android.content.Context
import android.util.AttributeSet
import android.text.TextUtils
import android.app.AlertDialog
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{TextView,CheckBox,Button,EditText}
import scala.collection.mutable

class MemorizationPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  val TAG_PANEL_ALL = "panel_all"
  val TAG_PANEL_FUDASET = "panel_fudaset"
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

  def setMemCountAndButtonHandler(panel:View,onlyInFudaset:Boolean) = {
    val (count,count_not,reset_cond) = if(onlyInFudaset){
      val c = FudaListHelper.queryNumbersToRead("= 2")
      val cn = FudaListHelper.queryNumbersToRead("= 0")
      val rc = "WHERE skip = 2"
      (c,cn,rc)
    }else{
      val c = FudaListHelper.queryNumbersToReadAlt("memorized = 1")
      val cn = FudaListHelper.queryNumbersToReadAlt("memorized = 0")
      val rc = ""
      (c,cn,rc)
    }
    Option(panel.findViewWithTag("memorized_count")).foreach{ v =>
      v.asInstanceOf[TextView].setText(count.toString)
    }
    Option(panel.findViewWithTag("not_memorized_count")).foreach{ v =>
      v.asInstanceOf[TextView].setText(count_not.toString)
    }
    Option(panel.findViewWithTag("save_memorized")).foreach{ v =>
      if(count > 0){
        v.setEnabled(true)
        v.setOnClickListener(new View.OnClickListener(){
            override def onClick(view:View){
              new MemorizationFudaSetDialog(context,onlyInFudaset,true,setMemCountAndButtonHandlerAll).show()
            }
        })
      }else{
        v.setEnabled(false)
      }
    }
    Option(panel.findViewWithTag("save_not_memorized")).foreach{ v =>
      if(count_not > 0){
        v.setEnabled(true)
        v.setOnClickListener(new View.OnClickListener(){
            override def onClick(view:View){
              new MemorizationFudaSetDialog(context,onlyInFudaset,false,setMemCountAndButtonHandlerAll).show()
            }
        })
      }else{
        v.setEnabled(false)
     }}
    Option(panel.findViewWithTag("reset_memorized")).foreach{ v =>
      if(count > 0){
        v.setEnabled(true)
        v.setOnClickListener(new View.OnClickListener(){
            override def onClick(view:View){
              Utils.confirmDialog(context,Right(R.string.memorization_mode_reset_confirm), {()=>
                FudaListHelper.resetMemorized(reset_cond)
                FudaListHelper.updateSkipList(context)
                Utils.messageDialog(context,Right(R.string.memorization_mode_reset_done))
                setMemCountAndButtonHandlerAll()
              })}})
      }else{
        v.setEnabled(false)
      }
    }
    
  }
  def setMemCountAndButtonHandlerAll(){
    root_view.foreach{rv =>
      Option(rv.findViewWithTag(TAG_PANEL_FUDASET)).foreach{ panel =>
        setMemCountAndButtonHandler(panel,true)
      }
      Option(rv.findViewWithTag(TAG_PANEL_ALL)).foreach{ panel =>
        setMemCountAndButtonHandler(panel,false)
      }
    }
  }

  def genPanel(onlyInFudaset:Boolean):View = {
    val (tag,title) = if(onlyInFudaset){
      val tt = "** " + Globals.prefs.get.getString("fudaset","") + " **"
      (TAG_PANEL_FUDASET,tt)
    }else{
      val tt = context.getResources.getString(R.string.memorization_all_poem)
      (TAG_PANEL_ALL,tt)
    }

    val panel = LayoutInflater.from(context).inflate(R.layout.memorization_panel,null)
    panel.setTag(tag)
    setMemCountAndButtonHandler(panel,onlyInFudaset)

    val title_view = panel.findViewWithTag("memorization_panel_title").asInstanceOf[TextView]
    title_view.setText(title)

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

class MemorizationFudaSetDialog(context:Context,
  onlyInFudaset:Boolean,
  memorized:Boolean,
  callback:()=>Unit)
  extends AlertDialog(context) with CustomAlertDialogTrait{

  override def doWhenClose(view:View){
    val title_view = view.findViewById(R.id.memorization_fudaset_name).asInstanceOf[EditText]
    val title = title_view.getText.toString
    if(TextUtils.isEmpty(title)){
      Utils.messageDialog(context,Right(R.string.fudasetedit_titleempty),{()=>show()})
      return
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
      case None => // do nothing, this should not happen
      case Some((kimari,st_size))=>
        Utils.writeFudaSetToDB(title,kimari,st_size,true)
        Utils.messageDialog(context,Right(R.string.memorization_fudaset_created))
    }
    callback()
  }
  override def onCreate(state:Bundle){
    val root = LayoutInflater.from(context).inflate(R.layout.memorization_fudaset, null)
    val title_id = if(memorized){
      R.string.memorization_fudaset_title_memorized
    }else{
      R.string.memorization_fudaset_title_notyet
    }
    setTitle(title_id)
    setViewAndButton(root)
    super.onCreate(state) 
  }
}

