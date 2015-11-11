package karuta.hpnpwd.wasuramoti

import _root_.android.app.Dialog
import _root_.android.content.Context
import _root_.android.preference.DialogPreference
import _root_.android.text.TextUtils
import _root_.android.util.AttributeSet
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{AdapterView,ArrayAdapter,Spinner}
import _root_.java.util.ArrayList

class FudaSetWithSize(val title:String, val num:Int){
  override def toString():String = {
    title + " (" + num + ")"
  }
  def equals(obj:FudaSetWithSize):Boolean = {
    title.equals(obj.title)
  }

}
class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom{
  var listItems = new ArrayList[FudaSetWithSize]()
  var adapter = None:Option[ArrayAdapter[FudaSetWithSize]]
  var spinner = None:Option[Spinner]
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

  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val pos = spinner.get.getSelectedItemPosition()
      val title = try{
        listItems.get(pos).title
      }catch{
        case _:IndexOutOfBoundsException => return
      }
      persistString(title)
      FudaListHelper.updateSkipList(context,title)
    }
    notifyChangedPublic // in case that number of fudas in current fudaset changed
    super.onDialogClosed(positiveResult)
  }
  override def onCreateDialogView():View = Globals.db_lock.synchronized{
    super.onCreateDialogView()
    // Using XML Attribute ``android:dialogLayout="@layout/fudaset"'' with ``android:onClick="..."'' does not work in Android 3.x.
    // That is because it creates each button with context instantiated from ContextThemeWrapper, and causes
    // ``java.lang.IllegalStateException: Could not find a method ... for onClick handler on view class android.widget.Button''
    // Therefore we set the layout here.
    val view = LayoutInflater.from(context).inflate(R.layout.fudaset, null)

    val persisted = getPersistedString("")
    adapter = Some(new ArrayAdapter[FudaSetWithSize](context,android.R.layout.simple_spinner_item,listItems))
    // adapter.get.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    val spin = view.findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    spin.setAdapter(adapter.get)
    spinner =  Some(spin)
    val db = Globals.database.get.getReadableDatabase
    val cursor = db.query(Globals.TABLE_FUDASETS,Array("title","set_size"),null,null,null,null,null,null)
    cursor.moveToFirst()
    listItems.clear()
    for( i <- 0 until cursor.getCount){
      val title = cursor.getString(0)
      val num = cursor.getInt(1)
      listItems.add(new FudaSetWithSize(title,num))
      if(title == persisted){
        spin.setSelection(i)
      }
      cursor.moveToNext()
    }
    cursor.close()
    // db.close()
    adapter.get.notifyDataSetChanged()
    return view
  }
}

trait FudaSetTrait{
  this:Context =>
  def getSpinnerSelected(view:View):(ArrayAdapter[FudaSetWithSize],Int) = {
    val spinner = view.getRootView().findViewById(R.id.fudaset_list).asInstanceOf[Spinner]
    val adapter = spinner.getAdapter().asInstanceOf[ArrayAdapter[FudaSetWithSize]]
    val pos = spinner.getSelectedItemPosition()
    return (adapter,pos)
  }
  def editFudaSetBase(view:View,is_add:Boolean,orig_fs:FudaSetWithSize=null){
    val (adapter,adapter_pos) = getSpinnerSelected(view)
    val dialog = new FudaSetEditDialog(this,is_add,adapter,adapter_pos,orig_fs) 
    dialog.show()
  }
  def newFudaSet(view:View){
    editFudaSetBase(view, true)
  }

  def editFudaSet(view: View){
    val (adapter,pos) = getSpinnerSelected(view)
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.getItem(pos)
    editFudaSetBase(view, false, fs)
  }
  def deleteFudaSet(view: View){ Globals.db_lock.synchronized{
    val (adapter,pos) = getSpinnerSelected(view)
    if(pos == AdapterView.INVALID_POSITION){
      return
    }
    val fs = adapter.getItem(pos)
    val message = getResources().getString(R.string.fudaset_confirmdelete,fs.title)
    Utils.confirmDialog(this,Left(message), () => Globals.db_lock.synchronized{
      val db = Globals.database.get.getWritableDatabase
      db.delete(Globals.TABLE_FUDASETS,"title = ?", Array(fs.title))
      db.close()
      adapter.remove(fs)
    })
  }}
}
