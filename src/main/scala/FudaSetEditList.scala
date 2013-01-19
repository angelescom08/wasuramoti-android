package karuta.hpnpwd.wasuramoti

import _root_.android.app.{Dialog,AlertDialog}
import _root_.android.os.Bundle
import _root_.android.content.{Context,DialogInterface}
import _root_.android.view.{View,Window}
import _root_.android.text.Html
import _root_.android.widget.{ArrayAdapter,AdapterView,ListView,TextView,Button}
import _root_.java.util.Comparator

class FudaSetEditListDialog(context:Context,kimarijis:String,onOk:String=>Unit) extends Dialog(context,android.R.style.Theme_Black_NoTitleBar_Fullscreen){
  object SortMode extends Enumeration {
    type SortMode = Value
    val ABC,NUM = Value
    def nextMode(v:SortMode.SortMode) = SortMode((v.id+1)%SortMode.maxId)
  }
  var sort_mode = SortMode.ABC

  object ListItemMode extends Enumeration {
    type ListItemMode = Value
    val FULL,KIMARIJI = Value
    def nextMode(v:ListItemMode.ListItemMode) = ListItemMode((v.id+1)%ListItemMode.maxId)
  }
  var list_item_mode = ListItemMode.KIMARIJI

  class FudaListItem(val str:String, val fudanum:Int) {
    override def toString():String = {
      val prefix = sort_mode match{
        case SortMode.ABC => ""
        case SortMode.NUM => fudanum + ". "
        }
      val body = if(Romanization.is_japanese(context) && list_item_mode == ListItemMode.FULL){
        AllFuda.list_full(fudanum-1) + " (" + AllFuda.author(fudanum-1)  + ")"
      }else{
        Romanization.jap_to_local(context,str)
      }
      prefix + body
    }
    def compareTo(that:FudaListItem):Int = {
      sort_mode match{
        case SortMode.ABC => str.compareTo(that.str)
        case SortMode.NUM => fudanum.compareTo(that.fudanum)
      }
    }
    def equals(that:FudaListItem):Boolean = {
      str.equals(that.str) && fudanum.equals(that.fudanum)
    }
  }
  val fudalistitem_comp = new Comparator[FudaListItem](){
    override def compare(lhs:FudaListItem,rhs:FudaListItem):Int = {
      lhs.compareTo(rhs)
    }
    override def equals(obj:Any):Boolean = {
      this.equals(obj)
    }
  }
  def checkListBy(f:FudaListItem=>Boolean){
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[ListView]
    val adapter = container_view.getAdapter().asInstanceOf[ArrayAdapter[FudaListItem]]
    for( pos <- 0 until container_view.getCount ){
      val r = f(adapter.getItem(pos))
      container_view.setItemChecked(pos,r)
    }
  }

  def addItemsToListView(){
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[ListView]
    val have_to_read = AllFuda.makeHaveToRead(kimarijis)
    val fudalist = AllFuda.list.zipWithIndex.map{ x => new FudaListItem(x._1,x._2+1) }
    val adapter = new ArrayAdapter[FudaListItem](context,R.layout.my_simple_list_item_multiple_choice,fudalist)
    adapter.sort(fudalistitem_comp)
    container_view.setAdapter(adapter)
    checkListBy(x=>have_to_read.contains(x.str))
  }

  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    setContentView(R.layout.fudasetedit_list)
    setTitle(R.string.button_fudasetedit_list)
    findViewById(R.id.button_invert).asInstanceOf[Button].setText(
      Html.fromHtml(context.getString(R.string.button_invert))
    )
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[ListView]
    addItemsToListView()
    val get_num_list = ()=> {
      val poss = container_view.getCheckedItemPositions()
      val adapter = container_view.getAdapter().asInstanceOf[ArrayAdapter[FudaListItem]]
      (0 until poss.size()).filter{poss.valueAt(_)}.map{ poss.keyAt(_) }.map{ adapter.getItem(_).fudanum }.toList
    }

    val update_fudanum = ()=> {
      findViewById(R.id.fudaseteditlist_fudanum).asInstanceOf[TextView].setText(get_num_list().length.toString)
    }
    update_fudanum()

    container_view.setOnItemClickListener(new AdapterView.OnItemClickListener(){
        override def onItemClick(a:AdapterView[_],v:View,i:Int,l:Long){
          update_fudanum()
        }
      })

    val invert_select = () => {
      for( pos <- 0 until container_view.getCount ){
        val q = container_view.isItemChecked(pos)
        container_view.setItemChecked(pos,!q)
      }
      update_fudanum()
    }

    val show_full = () => {
      list_item_mode = ListItemMode.nextMode(list_item_mode)
      val adapter = container_view.getAdapter().asInstanceOf[ArrayAdapter[FudaListItem]]
      adapter.notifyDataSetChanged()
    }
    val sort_order = () => {
      sort_mode = SortMode.nextMode(sort_mode)
      val adapter = container_view.getAdapter().asInstanceOf[ArrayAdapter[FudaListItem]]
      val num_list = get_num_list().toSet
      adapter.sort(fudalistitem_comp)
      adapter.notifyDataSetChanged()
      // I could not find a way to preserve checkbox state after sort() is called.
      // Therefore I do it by hand.
      checkListBy(x=>num_list.contains(x.fudanum))
    }


    findViewById(R.id.button_invert).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val items = context.getResources().getStringArray(R.array.fudaseteditlist_menuitems)
        val values = context.getResources().getStringArray(R.array.fudaseteditlist_menuitems_values)
        val builder = new AlertDialog.Builder(context)
        builder.setItems(items.map{_.asInstanceOf[CharSequence]},new DialogInterface.OnClickListener(){
            override def onClick(d:DialogInterface,position:Int){
              if(position > values.length){
                return
              }
              values(position) match{
                case "INVERT_SELECT" => invert_select()
                case "SHOW_FULL" => show_full()
                case "SORT_ORDER" => sort_order()
              }
              d.dismiss()
            }
          })
        builder.setNeutralButton(context.getResources().getString(R.string.button_cancel),new DialogInterface.OnClickListener(){
            override def onClick(d:DialogInterface,position:Int){
              d.dismiss()
            }
          })
        builder.create.show()
      }
    })
    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        dismiss()
      }
    })
    findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val body = AllFuda.makeKimarijiSetFromNumList(get_num_list()) match {
          case None => ""
          case Some((s,_)) => s
        }
        onOk(body)
        dismiss()
      }
    })
  }
}