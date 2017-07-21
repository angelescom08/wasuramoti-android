package karuta.hpnpwd.wasuramoti

import android.widget.{Button,LinearLayout}
import android.util.AttributeSet
import android.content.Context
import android.text.TextUtils
import android.view.{View,ViewGroup,LayoutInflater}


object CommandButtonList{
  abstract class OnClickListener{
    def onClick(btn:View,tag:String)
  }
}

class CommandButtonList(context:Context,attrs:AttributeSet) extends LinearLayout(context,attrs) {
  var m_on_click_listener:CommandButtonList.OnClickListener = null
  def setOnClickListener(listener:CommandButtonList.OnClickListener){
    m_on_click_listener = listener
  }
  def genButton(yiv:Option[YomiInfoView], tag:String,text:String,enabled:Boolean):Button = {
    import CommandButtonPanel._
    val button = LayoutInflater.from(context).inflate(R.layout.command_button_panel_button,null).asInstanceOf[Button]
    button.setTag(tag)
    button.setText(text)
    button.setEnabled(enabled)
    // TODO: we should use Theme#resolveAttribute to get the drawable of theme instead of switching by isLight
    val isLight = Globals.prefs.get.getBoolean("light_theme", false)
    val drawable = tag.split("_").head match{
      case PREFIX_REWIND => 
        if(isLight){
          R.drawable.light_ic_action_previous
        }else{
          R.drawable.ic_action_previous
        }
      case PREFIX_REPLAY =>
        if(isLight){
          R.drawable.light_ic_action_replay
        }else{
          R.drawable.ic_action_replay
        }
      case PREFIX_NEXT =>
        if(isLight){
          R.drawable.light_ic_action_next
        }else{
          R.drawable.ic_action_next
        }
      case PREFIX_DISPLAY | PREFIX_MEMORIZE => Utils.getButtonDrawableId(yiv,tag)
      case PREFIX_KIMARIJI =>
        if(isLight){
          R.drawable.light_ic_action_storage
        }else{
          R.drawable.ic_action_storage
        }
      case PREFIX_SWITCH =>
        if(isLight){
          R.drawable.light_ic_action_refresh
        }else{
          R.drawable.ic_action_refresh
        }
      case PREFIX_POEM =>
        if(isLight){
          R.drawable.light_ic_action_about
        }else{
          R.drawable.ic_action_about
        }
    }
    val img = context.getResources.getDrawable(drawable)
    button.setCompoundDrawablesWithIntrinsicBounds(img,null,null,null)
    button.setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
          m_on_click_listener.onClick(v,tag)
        }
      })
    button
  }
  def addButtons(context:Context,yiv:Option[YomiInfoView], text_and_tags:Array[(String,String,Boolean)]){
    if(context.getResources.getBoolean(R.bool.button_twocolumn)){
      for(ar<-text_and_tags.grouped(2)){
        val lay = LayoutInflater.from(context).inflate(R.layout.command_button_panel_row,null).asInstanceOf[LinearLayout]
        for((text,tag,enabled)<-ar if ! TextUtils.isEmpty(text)){
          val button = genButton(yiv,tag,text,enabled)
          val params = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1.0f)
          lay.addView(button,params)
        }
        val lparams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1.0f)
        addView(lay,lparams)
      }
    }else{
      for((text,tag,enabled)<-text_and_tags if ! TextUtils.isEmpty(text)){
        val button = genButton(yiv,tag,text,enabled)
        addView(button)
      }
    }
  }
}
