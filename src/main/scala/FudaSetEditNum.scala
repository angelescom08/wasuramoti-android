package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton,ListView,CompoundButton,RadioGroup,ArrayAdapter}
import android.view.{LayoutInflater,ViewGroup}
import scala.collection.mutable

class FudaSetEditNumDialog(context:Context) extends AlertDialog(context){

  val TAG_NUM = R.id.fudasetedit_tag_num

  case class FudaListItem(val num:Int, val str:String){
    override def toString():String = {
      return s"${num}. ${Romanization.jap_to_local(context,str)}"
    }
    def compVal():Int = {
      return num
    }
  }

  def clearAll(adapter:ArrayAdapter[FudaListItem],container:LinearLayout){
    adapter.getFilter.filter("")
    def search(vg:ViewGroup){
      for(i <- 0 until vg.getChildCount){
        vg.getChildAt(i) match {
          case v:ViewGroup => search(v)
          case v:ToggleButton => v.setChecked(false)
          case _ => Unit
        }
      }
    }
    search(container)
  }

  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_num_title)
    val root = LayoutInflater.from(context).inflate(R.layout.fudasetedit_num,null)
    val container = root.findViewById(R.id.fudasetedit_num_list).asInstanceOf[LinearLayout]
    val sar = context.getResources.getStringArray(R.array.fudasetedit_numbers)
    val list_view = root.findViewById(R.id.fudaseteditnum_container).asInstanceOf[ListView]
    val radio_group = root.findViewById(R.id.fudasetedit_num_type).asInstanceOf[RadioGroup]
    radio_group.check(R.id.fudasetedit_num_type_ones_digit)
    val filtered = (n:Int)=>radio_group.getCheckedRadioButtonId match{
      case R.id.fudasetedit_num_type_ones_digit =>
        n.toString.last
      case R.id.fudasetedit_num_type_tens_digit =>
        "%02d".format(n%100).head
    }
    val filter = (constraint:CharSequence) => for((s,i)<-AllFuda.list.zipWithIndex if constraint.toString.contains(filtered(i+1)))yield{
      new FudaListItem(i+1,s)
    }
    val ordering = Ordering.by[FudaListItem,Int](_.compVal())
    val adapter = FudaSetEditUtils.filterableAdapter(context,list_view,filter,ordering)
    radio_group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
      override def onCheckedChanged(group:RadioGroup,checkedId:Int){
        clearAll(adapter,container)
      }
    })
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        btn.setTag(TAG_NUM,c)
        btn.setText(c.toString)
        btn.setTextOn(c.toString)
        btn.setTextOff(c.toString)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            val cc = v.getTag(TAG_NUM).asInstanceOf[Char]
            FudaSetEditUtils.aggregateToggleButton(container,TAG_NUM,cc,build)
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
