package karuta.hpnpwd.wasuramoti

import android.app.Dialog
import android.os.Bundle
import android.content.{Context,DialogInterface}
import android.view.View
import android.widget.{ArrayAdapter,AdapterView,ListView,TextView}

object FudaSetEditListDialog {
  object SortMode extends Enumeration {
    type SortMode = Value
    val ABC,NUM = Value
    def nextMode(v:SortMode.SortMode) = SortMode((v.id+1)%SortMode.maxId)
  }
  object ListItemMode extends Enumeration {
    type ListItemMode = Value
    val FULL,KIMARIJI = Value
    def nextMode(v:ListItemMode.ListItemMode) = ListItemMode((v.id+1)%ListItemMode.maxId)
  }
  def genDialogMode(sort_mode:SortMode.SortMode,list_item_mode:ListItemMode.ListItemMode):String = {
    Array(sort_mode.toString,list_item_mode.toString).mkString(",")
  }
}

class FudaSetEditListDialog(context:Context,kimarijis:String,onOk:String=>Unit) extends Dialog(context,android.R.style.Theme_Black_NoTitleBar_Fullscreen){
  import FudaSetEditListDialog.{SortMode,ListItemMode,genDialogMode}
  var sort_mode = SortMode.ABC

  var list_item_mode = ListItemMode.KIMARIJI

  class FudaListItem(val str:String, val fudanum:Int) {
    override def toString():String = {
      val prefix = sort_mode match{
        case SortMode.ABC => ""
        case SortMode.NUM => fudanum + ". "
        }
      val body = if(list_item_mode == ListItemMode.FULL){
        val poem = AllFuda.removeInsideParens(AllFuda.get(context,R.array.list_full)(fudanum))
        val author = AllFuda.removeInsideParens(AllFuda.get(context,R.array.author)(fudanum))
        poem + " (" + author.replace(" ","") + ")"
      }else{
        Romanization.jap_to_local(context,str)
      }
      prefix + body
    }
    def compVal():(Int,String) = {
      sort_mode match{
        case SortMode.ABC => (0,str)
        case SortMode.NUM => (fudanum,"")
      }
    }
  }
  val fudalistitem_comp = Ordering.by[FudaListItem,(Int,String)](_.compVal())

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
    val have_to_read = TrieUtils.makeHaveToRead(kimarijis)
    val fudalist = AllFuda.list.zipWithIndex.map{ x => new FudaListItem(x._1,x._2+1) }
    val adapter = new ArrayAdapter[FudaListItem](context,R.layout.my_simple_list_item_multiple_choice,fudalist)
    adapter.sort(fudalistitem_comp)
    container_view.setAdapter(adapter)
    checkListBy(x=>have_to_read.contains(x.str))
  }

  override def onCreate(bundle:Bundle){
    super.onCreate(bundle)
    Globals.prefs.foreach{ p =>
      val str = p.getString("fudaset_edit_list_dlg_mode",null)
      if(str != null){
        val Array(srt,lim) = str.split(",")
        sort_mode = SortMode.withName(srt)
        list_item_mode = ListItemMode.withName(lim)
      }
    }
    setOnDismissListener(new DialogInterface.OnDismissListener(){
        override def onDismiss(di:DialogInterface){
          Globals.prefs.foreach{ p =>
            val str = genDialogMode(sort_mode,list_item_mode)
            p.edit.putString("fudaset_edit_list_dlg_mode",str).commit
          }
        }
      }
    )
    setContentView(R.layout.fudasetedit_list)
    setTitle(R.string.button_fudasetedit_list)
    val container_view = findViewById(R.id.fudaseteditlist_container).asInstanceOf[ListView]
    addItemsToListView()
    val get_num_list = ()=> {
      Utils.getCheckedItemsFromListView[FudaListItem](container_view).map(_.fudanum).toList
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
        Utils.listDialog(context,
          R.string.fudaseteditlist_menutitle,
          R.array.fudaseteditlist_menuitems,
          Array(invert_select,show_full,sort_order))
      }
    })
    findViewById(R.id.button_cancel).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        dismiss()
      }
    })
    findViewById(R.id.button_ok).setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val body = TrieUtils.makeKimarijiSetFromNumList(get_num_list()) match {
          case None => ""
          case Some((s,_)) => s
        }
        onOk(body)
        dismiss()
      }
    })
  }
}
