package karuta.hpnpwd.wasuramoti

import android.widget.{Button,LinearLayout}
import android.util.AttributeSet
import android.content.Context
import android.text.TextUtils
import android.view.{View,ViewGroup,LayoutInflater}


object YomiInfoButtonList{
  abstract class OnClickListener{
    def onClick(btn:View,tag:String)
  }
}

class YomiInfoButtonList(context:Context,attrs:AttributeSet) extends LinearLayout(context,attrs) {
  var m_on_click_listener:YomiInfoButtonList.OnClickListener = null
  def setOnClickListener(listener:YomiInfoButtonList.OnClickListener){
    m_on_click_listener = listener
  }
  def genButton(yiv:Option[YomiInfoView], tag:String,text:String,enabled:Boolean):Button = {
    val button = LayoutInflater.from(context).inflate(R.layout.yomi_info_search_dialog_button,null).asInstanceOf[Button]
    button.setTag(tag)
    button.setText(text)
    button.setEnabled(enabled)
    val drawable = tag.split("_").head match{
      case YomiInfoSearchDialog.PREFIX_REPLAY => R.drawable.ic_action_replay
      case YomiInfoSearchDialog.PREFIX_DISPLAY | YomiInfoSearchDialog.PREFIX_MEMORIZE => Utils.getButtonDrawableId(yiv,tag)
      case YomiInfoSearchDialog.PREFIX_KIMARIJI => R.drawable.ic_action_storage
      case YomiInfoSearchDialog.PREFIX_SWITCH => R.drawable.ic_action_refresh
      case YomiInfoSearchDialog.PREFIX_SEARCH => R.drawable.ic_action_web_site
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
        val lay = LayoutInflater.from(context).inflate(R.layout.yomi_info_search_dialog_row,null).asInstanceOf[LinearLayout]
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
