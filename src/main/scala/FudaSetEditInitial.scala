package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton,ListView,CompoundButton}
import android.view.LayoutInflater
import scala.collection.mutable

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
        btn.setTag(TAG_INITIAL,c)
        val tt = Romanization.jap_to_local(context,c.toString)
        btn.setText(tt)
        btn.setTextOn(tt)
        btn.setTextOff(tt)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            val cc = v.getTag(TAG_INITIAL).asInstanceOf[Char]
            FudaSetEditUtils.searchToggleButton(container,TAG_INITIAL,cc,build)
            if(isChecked){build.append(cc)}
            val constraint = build.toString
            adapter.getFilter.filter(constraint)
          }
        });
      }
      list_view.setAdapter(adapter)
      container.addView(row)
    }
    setView(root)
    super.onCreate(bundle)
  }

}
