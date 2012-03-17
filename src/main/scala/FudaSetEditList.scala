package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.os.Bundle
import _root_.android.content.Context
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{LinearLayout,CheckBox}

class FudaSetEditListDialog(context:Context,kimarijis:String,onOk:String=>Unit) extends Dialog(context){
  val LISTITEM_PREFIX="fudaseteditlistitem_"
  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.fudasetedit_list)
    setTitle(R.string.button_fudasetedit_list)
    val inflater = LayoutInflater.from(context)
    val have_to_read = AllFuda.makeHaveToRead(kimarijis)
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[LinearLayout]
    for( (s,i) <- AllFuda.list.zipWithIndex.sortBy{ _ match { case (ss,_) => ss }}){
      val vw = inflater.inflate(R.layout.fudasetedit_list_item,null)
      vw.setTag(LISTITEM_PREFIX+(i+1))
      val cb = vw.asInstanceOf[CheckBox]
      cb.setText(Romanization.jap_to_local(context,s))
      container_view.addView(vw)
      if(have_to_read.contains(s)){
        cb.setChecked(true)
      }
    }

    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        dismiss()
      }
    })
    findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val num_list = (0 until container_view.getChildCount).map{ i => {
            val v = container_view.getChildAt(i)
            if(v != null && v.asInstanceOf[CheckBox].isChecked()){
              val tag = v.getTag().asInstanceOf[String]
              if(tag.startsWith(LISTITEM_PREFIX)){
                Some(tag.stripPrefix(LISTITEM_PREFIX).toInt)
              }else{
                None
              }
            }else
              None
            }
          }.filter{_.isDefined}.map{_.get}.toList
        val body = AllFuda.makeKimarijiSetFromNumList(num_list) match {
          case None => ""
          case Some((s,_)) => s
        }
        onOk(body)
        dismiss()
      }
    })
  }
}
