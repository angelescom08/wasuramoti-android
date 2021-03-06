package karuta.hpnpwd.wasuramoti

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.{LinearLayout,ToggleButton,ListView,CompoundButton}
import android.view.{View,ViewGroup}

import scala.collection.mutable
import scala.collection.immutable.ListSet

@KeepConstructor
class FudaSetEditInitialDialog(context:Context)
  extends Dialog(context,PrefUtils.switchFullDialogTheme) with CommonDialog.WrappableDialog
  {

  val TAG_INITIAL = R.id.fudasetedit_tag_initial

  case class FudaListItem(val num:Int, val str:String){
    override def toString():String = {
      return Romanization.japToLocal(context,str)
    }
    def compVal():(Int,String) = {
      val head = AllFuda.musumefusahoseAll.indexOf(str(0))
      return (head, str.substring(1))
    }
  }

  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.fudasetedit_initial)
    setTitle(R.string.fudasetedit_initial_title)
    val container = findViewById[LinearLayout](R.id.fudasetedit_initial_list)
    val sar = context.getResources.getStringArray(R.array.fudasetedit_initials)
    val list_view = findViewById[ListView](R.id.fudaseteditinitial_container)
    val filter = (constraint:CharSequence) => for((s,i)<-AllFuda.list.zipWithIndex if constraint.toString.contains(s(0)))yield{
      new FudaListItem(i+1,s)
    }
    val ordering = Ordering.by[FudaListItem,(Int,String)](_.compVal())
    val adapter = FudaSetEditUtils.filterableAdapter(context,list_view,filter,ordering)
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        val width = context.getResources.getDimension(R.dimen.fudasetedit_letter_button_width).toInt
        btn.setLayoutParams(new ViewGroup.LayoutParams(width,ViewGroup.LayoutParams.WRAP_CONTENT))
        btn.setTag(TAG_INITIAL,c)
        val tt = Romanization.japToLocal(context,c.toString)
        btn.setText(tt)
        btn.setTextOn(tt)
        btn.setTextOff(tt)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            val cc = v.getTag(TAG_INITIAL).asInstanceOf[Char]
            FudaSetEditUtils.aggregateToggleButton(container,TAG_INITIAL,cc,build)
            if(isChecked){build.append(cc)}
            val constraint = build.toString
            adapter.getFilter.filter(constraint)
          }
        });
      }
      list_view.setAdapter(adapter)
      container.addView(row)
    }
    findViewById[View](R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        dismiss()
      }
    })
    findViewById[View](R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val list_view = findViewById[ListView](R.id.fudaseteditinitial_container)
        val bundle = new Bundle
        bundle.putString("tag","fudaset_edit_initial_done")
        bundle.putSerializable("set",ListSet(Utils.getCheckedItemsFromListView[FudaListItem](list_view).map(_.num):_*))
        callbackListener.onCommonDialogCallback(bundle)
        dismiss()
      }
    })
  }

}
