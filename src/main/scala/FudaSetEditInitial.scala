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

  class FudaListItem(val str:String){
    override def toString():String = {
      return str
    }
    def compVal():(Int,String) = {
      val head = AllFuda.musumefusahoseAll.indexOf(str(0))
      return (head, str.substring(1))
    }
  }

  def addItemsToListView(root:View){
    val container = root.findViewById(R.id.fudaseteditinitial_container).asInstanceOf[ListView]
    val fudalist = mutable.ArrayBuffer[FudaListItem]()
    val filter = new Filter(){
      override def performFiltering(constraint:CharSequence):Filter.FilterResults = {
        val results = new Filter.FilterResults
        if(TextUtils.isEmpty(constraint)){
          results.values = Array[FudaListItem]()
          results.count = 0
        }else{
          val arr = AllFuda.list.filter(s=>constraint.toString.contains(s(0))).map(new FudaListItem(_))
          results.values = arr
          results.count = arr.size
        }
        return results
      }
      override def publishResults(constraint:CharSequence,results:Filter.FilterResults){
        fudalist.clear()
        fudalist ++= results.values.asInstanceOf[Array[FudaListItem]]

        val adapter = container.getAdapter.asInstanceOf[ArrayAdapter[FudaListItem]]
        adapter.sort(Ordering.by[FudaListItem,(Int,String)](_.compVal()))
        if(results.count > 0){
          adapter.notifyDataSetChanged()
        }else{
          adapter.notifyDataSetInvalidated()
        }
      }
    }
    val adapter = new ArrayAdapter[FudaListItem](context,R.layout.my_simple_list_item_multiple_choice,JavaConversions.bufferAsJavaList(fudalist)){
      override def getFilter():Filter = {
        return filter
      }
    }
    container.setAdapter(adapter)
    adapter.sort(Ordering.by[FudaListItem,(Int,String)](_.compVal()))
  }

  def searchToggleButton(vg:ViewGroup,ignore:CharSequence,build:mutable.StringBuilder){
    for(i <- 0 until vg.getChildCount){
      vg.getChildAt(i) match {
        case v:ViewGroup => searchToggleButton(v,ignore,build)
        case v:ToggleButton => if(v.isChecked && v.getText != ignore){build.append(v.getText)}
      }
    }
  }

  override def onCreate(bundle:Bundle){
    setTitle(R.string.fudasetedit_initial_title)
    val root = LayoutInflater.from(context).inflate(R.layout.fudasetedit_initial,null)
    val container = root.findViewById(R.id.fudasetedit_initial_list).asInstanceOf[LinearLayout]
    val sar = context.getResources.getStringArray(R.array.fudasetedit_initials)
    for(s <- sar){
      val row = new LinearLayout(context)
      for(c <- s){
        val btn = new ToggleButton(context)
        btn.setText(c.toString)
        btn.setTextOn(c.toString)
        btn.setTextOff(c.toString)
        row.addView(btn)
        btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
          override def onCheckedChanged(v:CompoundButton,isChecked:Boolean){
            val build = new mutable.StringBuilder
            searchToggleButton(container,v.getText,build)
            val constraint = build.toString + (if(isChecked){v.getText}else{""})
            val lv = root.findViewById(R.id.fudaseteditinitial_container).asInstanceOf[ListView]
            lv.getAdapter.asInstanceOf[ArrayAdapter[FudaListItem]].getFilter.filter(constraint)
          }
        });
      }
      container.addView(row)
    }
    addItemsToListView(root)
    setView(root)
    super.onCreate(bundle)
  }

}
