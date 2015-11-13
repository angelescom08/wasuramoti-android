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
class FudaSetPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with PreferenceCustom with View.OnClickListener{
  var listItems = new ArrayList[FudaSetWithSize]()
  var adapter = None:Option[ArrayAdapter[FudaSetWithSize]]
  var spinner = None:Option[Spinner]
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)

  val buttonMapping = Map(
      R.id.button_fudaset_edit -> editFudaSet _,
      R.id.button_fudaset_new ->  newFudaSet _,
      R.id.button_fudaset_delete ->  deleteFudaSet _,
      R.id.button_fudaset_copymerge -> copymergeFudaSet _
    )
  override def onClick(view:View){
    buttonMapping.get(view.getId).foreach{_()}
  }

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
    listItems.clear()
    for(((id,title,num),i) <- FudaListHelper.selectFudasetAll.zipWithIndex){
      listItems.add(new FudaSetWithSize(title,num))
      if(title == persisted){
        spin.setSelection(i)
      }
    }
    adapter.get.notifyDataSetChanged()
    for(id <- buttonMapping.keys){
      view.findViewById(id).setOnClickListener(this)
    }
    return view
  }
  def editFudaSetBase(is_add:Boolean,orig_fs:FudaSetWithSize=null){
    val callback = (fudaset_with_size:FudaSetWithSize)=>{
      if(is_add){
        adapter.get.add(fudaset_with_size)
        adapter.get.notifyDataSetChanged()
        spinner.get.setSelection(adapter.get.getCount-1)

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
    new FudaSetCopyMergeDialog(context).show()
  }
}
