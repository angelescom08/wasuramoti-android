package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.{Context,DialogInterface}
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton,ListView,ArrayAdapter,Filter,CompoundButton}
import android.view.{LayoutInflater,View,ViewGroup}
import android.text.TextUtils
import scala.collection.mutable
import scala.collection.JavaConversions

class FudaSetEditInitialDialog(context:Context) extends AlertDialog(context){

  val TAG_INITIAL = 1

  class FudaListItem(val str:String){
    override def toString():String = {
      return Romanization.jap_to_local(context,str)
    }
    def compVal():(Int,String) = {
      val head = AllFuda.musumefusahoseAll.indexOf(str(0))
      return (head, str.substring(1))
    }
  }

  def searchToggleButton(vg:ViewGroup,ignore:String,build:mutable.StringBuilder){
    for(i <- 0 until vg.getChildCount){
      vg.getChildAt(i) match {
        case v:ViewGroup => searchToggleButton(v,ignore,build)
        case v:ToggleButton => 
          val cc = v.getTag(TAG_INITIAL).asInstanceOf[String]
          if(v.isChecked && cc != ignore){build.append(cc)}
      }
    }
  }

  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_initial_title)
    val root = LayoutInflater.from(context).inflate(R.layout.fudasetedit_initial,null)
    val container = root.findViewById(R.id.fudasetedit_initial_list).asInstanceOf[LinearLayout]
    val sar = context.getResources.getStringArray(R.array.fudasetedit_initials)
    val list_view = root.findViewById(R.id.fudaseteditinitial_container).asInstanceOf[ListView]
    val filter = (constraint:CharSequence) => AllFuda.list.filter(s=>constraint.toString.contains(s(0))).map(new FudaListItem(_))
    val ordering = Ordering.by[FudaListItem,(Int,String)](_.compVal())
    val adapter = FudaSetEditUtils.filterableAdapter(context,filter,ordering)
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        btn.setTag(TAG_INITIAL,c.toString)
        val tt = Romanization.jap_to_local(context,c.toString)
        btn.setText(tt)
        btn.setTextOn(tt)
        btn.setTextOff(tt)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            val cc = v.getTag(TAG_INITIAL).asInstanceOf[String]
            searchToggleButton(container,cc,build)
            val constraint = build.toString + (if(isChecked){cc}else{""})
            adapter.getFilter.filter(constraint)
          }
        });
      }
      container.addView(row)
    }
    setView(root)
    super.onCreate(bundle)
  }

}
