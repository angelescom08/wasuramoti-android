package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton,ListView,CompoundButton}
import android.view.{LayoutInflater}
import scala.collection.mutable

class FudaSetEditNumDialog(context:Context) extends AlertDialog(context){

  val TAG_NUM = 1

  class FudaListItem(val num:Int, val str:String){
    override def toString():String = {
      return s"${num}. ${Romanization.jap_to_local(context,str)}"
    }
    def compVal():Int = {
      return num
    }
  }


  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_num_title)
    val root = LayoutInflater.from(context).inflate(R.layout.fudasetedit_num,null)
    val container = root.findViewById(R.id.fudasetedit_num_list).asInstanceOf[LinearLayout]
    val sar = context.getResources.getStringArray(R.array.fudasetedit_numbers)
    val list_view = root.findViewById(R.id.fudaseteditnum_container).asInstanceOf[ListView]
    val filter = (constraint:CharSequence) => for((s,i)<-AllFuda.list.zipWithIndex if constraint.toString.contains((i+1).toString.last))yield{
      new FudaListItem(i+1,s)
    }
    val ordering = Ordering.by[FudaListItem,Int](_.compVal())
    val adapter = FudaSetEditUtils.filterableAdapter(context,filter,ordering)
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        btn.setTag(TAG_NUM,c.toString)
        btn.setText(c.toString)
        btn.setTextOn(c.toString)
        btn.setTextOff(c.toString)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            val cc = v.getTag(TAG_NUM).asInstanceOf[Char]
            FudaSetEditUtils.searchToggleButton(container,TAG_NUM,cc,build)
            val constraint = build.toString + (if(isChecked){cc}else{""})
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
