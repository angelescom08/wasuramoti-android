package karuta.hpnpwd.wasuramoti

import android.content.Context
import android.support.v7.preference.{DialogPreference,PreferenceDialogFragmentCompat}
import android.text.TextUtils
import android.util.AttributeSet
import android.view.{View,LayoutInflater}
import android.widget.{AdapterView,ArrayAdapter,Spinner}
import java.util.ArrayList

class FudaSetWithSize(val title:String, val num:Int){
  override def toString():String = {
    title + " (" + num + ")"
  }
  def equals(obj:FudaSetWithSize):Boolean = {
    title.equals(obj.title)
  }
}

class FudaSetPreferenceFragment extends PreferenceDialogFragmentCompat with ButtonListener {
  var listItems = new ArrayList[FudaSetWithSize]()
  var adapter = None:Option[ArrayAdapter[FudaSetWithSize]]
  var spinner = None:Option[Spinner]
  override def buttonMapping = Map(
      R.id.button_fudaset_edit -> editFudaSet _,
      R.id.button_fudaset_new ->  newFudaSet _,
      R.id.button_fudaset_delete ->  deleteFudaSet _,
      R.id.button_fudaset_copymerge -> copymergeFudaSet _,
      R.id.button_fudaset_reorder -> reorderFudaSet _
    )
  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[FudaSetPreference]
    val context = getContext
    if(positiveResult){
      val pos = spinner.get.getSelectedItemPosition()
      val title = try{
        listItems.get(pos).title
      }catch{
        case _:IndexOutOfBoundsException => return
      }
      pref.persistString(title)
      FudaListHelper.updateSkipList(context,title)
    }
    pref.notifyChangedPublic // in case that number of fudas in current fudaset changed
  }
  override def onCreateDialogView(context:Context):View = Globals.db_lock.synchronized{
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset, null)

    adapter = Some(new ArrayAdapter[FudaSetWithSize](context,android.R.layout.simple_spinner_item,listItems))
    val spin = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    spin.setAdapter(adapter.get)
    spinner =  Some(spin)
    refreshListItems
    setButtonMapping(view)
    return view
  }

  def refreshListItems(){
    val pref = getPreference.asInstanceOf[FudaSetPreference]
    val persisted = pref.getPersistedString("")
    listItems.clear()
    for((fs,i) <- FudaListHelper.selectFudasetAll.zipWithIndex){
      listItems.add(new FudaSetWithSize(fs.title,fs.set_size))
      if(fs.title == persisted){
        spinner.get.setSelection(i)
      }
    }
    adapter.get.notifyDataSetChanged()
  }

  def addFudaSetToSpinner(fs:FudaSetWithSize){
    adapter.get.add(fs)
    adapter.get.notifyDataSetChanged
    spinner.get.setSelection(adapter.get.getCount-1)
  }
  def editFudaSetBase(is_add:Boolean,orig_fs:FudaSetWithSize=null){
    val context = getContext
    val callback = (fudaset_with_size:FudaSetWithSize)=>{
      if(is_add){
        addFudaSetToSpinner(fudaset_with_size)
      }else{
        val pos = spinner.get.getSelectedItemPosition
        adapter.get.remove(orig_fs)
        adapter.get.insert(fudaset_with_size,pos)
      }
    }
    val orig_title = Option(orig_fs).map(_.title).getOrElse("")
    val dialog = new FudaSetEditDialog(context,is_add,callback,orig_title) 
    dialog.show()
  }
  def newFudaSet(){
    editFudaSetBase(true)
  }

  def editFudaSet(){
    val pos = spinner.get.getSelectedItemPosition
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.get.getItem(pos)
    editFudaSetBase(false, fs)
  }
  def deleteFudaSet(){ Globals.db_lock.synchronized{
    val context = getContext
    val pos = spinner.get.getSelectedItemPosition
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.get.getItem(pos)
    val message = context.getResources.getString(R.string.fudaset_confirmdelete,fs.title)
    Utils.confirmDialog(context,Left(message), () => Globals.db_lock.synchronized{
      val db = Globals.database.get.getWritableDatabase
      db.delete(Globals.TABLE_FUDASETS,"title = ?", Array(fs.title))
      db.close()
      adapter.get.remove(fs)
    })
  }}
  def copymergeFudaSet(){
    new FudaSetCopyMergeDialog(getContext, addFudaSetToSpinner).show()
  }
  def reorderFudaSet(){
    new FudaSetReOrderDialog(getContext, refreshListItems).show()
  }
}

class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref{
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)

  override def getAbbrValue():String = Globals.db_lock.synchronized{
    val title = getPersistedString("")
    if(!TextUtils.isEmpty(title)){
      val db = Globals.database.get.getReadableDatabase
      val cursor = db.query(Globals.TABLE_FUDASETS,Array("set_size"),"title = ?",Array(title),null,null,null,null)
      cursor.moveToFirst
      val set_size = if(cursor.getCount > 0){ cursor.getInt(0) }else{0}
      cursor.close
      //db.close
      title + " ("+set_size+")"
    }else{
      ""
    }
  }

}
