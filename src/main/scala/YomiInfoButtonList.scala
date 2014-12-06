package karuta.hpnpwd.wasuramoti

import _root_.android.widget.{Button,TableLayout,TableRow}
import _root_.android.util.AttributeSet
import _root_.android.content.Context
import _root_.android.text.TextUtils
import _root_.android.view.{View,ViewGroup,Gravity,LayoutInflater}


object YomiInfoButtonList{
  abstract class OnClickListener{
    def onClick(btn:View,tag:String)
  }
}

class YomiInfoButtonList(context:Context,attrs:AttributeSet) extends TableLayout(context,attrs) {
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
      case YomiInfoSearchDialog.PREFIX_DISPLAY => Utils.getButtonDrawableId(yiv,tag)
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

    if(Utils.isScreenWide(context)){
      for(ar<-text_and_tags.grouped(2)){
        val lay = LayoutInflater.from(context).inflate(R.layout.yomi_info_search_dialog_row,null).asInstanceOf[TableRow]
        for((text,tag,enabled)<-ar if ! TextUtils.isEmpty(text)){
          val button = genButton(yiv,tag,text,enabled)
          val params = new TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
          lay.addView(button,params)
        }
        val lparam = new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT,1.0f)
        lay.setGravity(Gravity.CENTER)
        addView(lay,lparam)
      }
    }else{
      for((text,tag,enabled)<-text_and_tags if ! TextUtils.isEmpty(text)){
        val button = genButton(yiv,tag,text,enabled)
        button.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(button)
      }
    }
  }
}
