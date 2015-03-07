package karuta.hpnpwd.wasuramoti

import scala.collection.mutable.Buffer
import scala.collection.JavaConversions

import _root_.android.preference.ListPreference
import _root_.android.content.{Context,DialogInterface}
import _root_.android.util.AttributeSet
import _root_.android.app.{AlertDialog,ProgressDialog,Activity}
import _root_.android.os.{Environment,AsyncTask}
import _root_.android.view.Gravity
import _root_.android.widget.ArrayAdapter
import _root_.java.io.{IOException,File,FileOutputStream}
import _root_.karuta.hpnpwd.audio.OggVorbisDecoder

object ReaderList{
  def setDefaultReader(context:Context){
    if(!Globals.prefs.get.contains("reader_path")){
      for(p <- context.getAssets.list(Globals.ASSETS_READER_DIR)){
        val reader_path = "INT:" + p
        val reader = makeReader(context,reader_path)
        if(reader.existsAll() match {case (b,m) => b}){
          Globals.prefs.get.edit.putString("reader_path",reader_path).commit
          return
        }
      }
    }
  }
  def makeCurrentReader(context:Context):Option[Reader] = {
    val path = Globals.prefs.get.getString("reader_path",null)
    if(path == null){
      None
    }else{
      Some(makeReader(context,path))
    }
  }
  def makeReader(context:Context,path:String):Reader = {
    if(path.startsWith("INT:")){
      new Asset(context,path.replaceFirst("INT:",""))
    }else if(path.startsWith("EXT:")){
      new External(context,path.replaceFirst("EXT:","").replaceFirst("/<>/","/"+Globals.READER_DIR+"/"))
    }else{
      new Absolute(context,path.replaceFirst("ABS:","").replaceFirst("/<>/","/"+Globals.READER_DIR+"/"))
    }
  }
}

class ReaderListPreference(context:Context, attrs:AttributeSet) extends ListPreference(context,attrs) with PreferenceCustom{
  var adapter = None:Option[ArrayAdapter[CharSequence]]
  class SearchDirectoryTask extends AsyncTask[AnyRef,Void,Boolean] {
    var progress = None:Option[ProgressDialog]
    override def onPreExecute(){
      if(context.isInstanceOf[Activity]){
        val activity = context.asInstanceOf[Activity]
        activity.runOnUiThread(new Runnable{
            override def run(){
              if(!activity.isFinishing){
                val dlg = new ProgressDialog(activity)
                dlg.setMessage(activity.getApplicationContext.getResources.getString(R.string.now_searching))
                dlg.getWindow.setGravity(Gravity.BOTTOM)
                dlg.show
                progress = Some(dlg)
              }
            }
          })
      }
    }
    override def onPostExecute(rval:Boolean){
      if(context.isInstanceOf[Activity]){
        context.asInstanceOf[Activity].runOnUiThread(new Runnable{
            override def run(){
              progress.foreach(_.dismiss())
            }
          })
      }
    }

    // the signature of doInBackground must be `java.lang.Object doInBackground(java.lang.Object[])`. check in javap command.
    // otherwise it raises AbstractMethodError "abstract method not implemented"
    override def doInBackground(unused:AnyRef*):AnyRef = {
      val paths = Utils.getAllExternalStorageDirectoriesWithUserCustom(context)
      for(path <- paths){
        Utils.walkDir(path,Globals.READER_SCAN_DEPTH_MAX, f =>{
          if(f.getName == Globals.READER_DIR){
            val files = f.listFiles()
            if(files != null){
              var entvals = getEntryValues
              var entries = getEntries
              val buf = Buffer[CharSequence]()
              for( i <- files if i.isDirectory() && ! entries.contains(i.getName)){
                entvals :+= (if(path == Environment.getExternalStorageDirectory){
                              "EXT:"+i.getAbsolutePath.replaceFirst("^"+path,"").replaceFirst("/"+Globals.READER_DIR+"/","/<>/")
                            }else{
                              "ABS:"+i.getAbsolutePath.replaceFirst("/"+Globals.READER_DIR+"/","/<>/")
                            })
                entries :+= i.getName
                buf += i.getName
              }
              setEntries(entries)
              setEntryValues(entvals)
              context.asInstanceOf[Activity].runOnUiThread(new Runnable{
                  override def run(){
                    adapter.foreach{x=>
                     for(i <- buf){
                       x.add(i)
                     }
                     x.notifyDataSetChanged()
                   }
                  }
                })
            }
          }}
        )
      }
      true.asInstanceOf[AnyRef]
    }
  }

  override def getAbbrValue():String = {
    val path = getValue()
    if(path.startsWith("INT:")){
      path.replaceFirst("INT:","")
    }else{
      new File(path).getName()
    }
  }
  override def onDialogClosed(positiveResult:Boolean){
    if(positiveResult){
      val prev_value = getValue()
      super.onDialogClosed(positiveResult)
      val (ok,message) = ReaderList.makeReader(context,getValue()).existsAll()
      if(!ok){
        Utils.messageDialog(context,Left(message))
        setValue(prev_value)
      }
    }else{
      super.onDialogClosed(positiveResult)
    }
  }
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val entvals = Buffer[CharSequence]()
    val entries = Buffer[CharSequence]()
    for(i <- context.getAssets.list(Globals.ASSETS_READER_DIR)){
      entvals += "INT:"+i
      entries += i
    }
    setEntries(entries.toArray)
    setEntryValues(entvals.toArray)
    adapter = Some(new ArrayAdapter[CharSequence](context,android.R.layout.simple_spinner_dropdown_item,JavaConversions.bufferAsJavaList(entries)))
    builder.setAdapter(adapter.get,null)
    new SearchDirectoryTask().execute(new AnyRef())

    builder.setNeutralButton(R.string.button_config, new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          new ScanReaderConfDialog(context).show
        }
      })

    super.onPrepareDialogBuilder(builder)
  }
}

class OggDecodeFailException(s:String) extends Exception(s){
}

abstract class Reader(context:Context,val path:String){
  def basename:String = new File(path).getName()
  def addSuffix(str:String,num:Int, kamisimo:Int):String = str+"_%03d_%d.ogg".format(num,kamisimo)
  def exists(num:Int, kamisimo:Int):Boolean //TODO: not only check the existance of .ogg but also vaild .ogg file with identical sample rate
  def bothExists(simo_num:Int, kami_num:Int):Boolean = {
    // TODO: The following does not consider read_order_each
    val maxn = AllFuda.list.length + 1
    (simo_num == maxn || exists(simo_num,2)) &&
    (kami_num == maxn || exists(kami_num,1)) &&
    (simo_num != maxn || kami_num != maxn)
  }
  def withFile(num:Int, kamisimo:Int, func:File=>Unit):Unit
  def withDecodedWav(num:Int, kamisimo:Int, func:(WavBuffer)=>Unit){
    val decoder = new OggVorbisDecoder()
    var rval = true
    withFile(num,kamisimo,temp_file => {
      val buffer = decoder.decodeFileToShortBuffer(temp_file.getAbsolutePath())
      if(buffer != null){
        val wav = new WavBuffer(buffer,decoder,num,kamisimo)
        func(wav)
      }else{
        throw new OggDecodeFailException("ogg decode failed: "+temp_file.getName)
      }
    })
  }
  def existsAll():(Boolean,String) = {
    for(num <- 0 to 100; kamisimo <- 1 to 2 if num > 0 || kamisimo == 2){
      if(!exists(num,kamisimo)){
        return(false, addSuffix(basename,num,kamisimo) + " not found")
      }
    }
    (true,"")
  }
}
class Asset(context:Context,path:String) extends Reader(context,path){
  def getAssetPath(num:Int, kamisimo:Int):String = Globals.ASSETS_READER_DIR+"/"+path+"/"+addSuffix(path,num,kamisimo)

  override def exists(num:Int, kamisimo:Int):Boolean = {
    try{
      val fp = context.getAssets.open(getAssetPath(num,kamisimo))
      fp.close()
      true
    }catch{
      case _:IOException => false
    }
  }
  override def withFile(num:Int, kamisimo:Int, func:File=>Unit){
    val temp_dir = context.getCacheDir()
    val temp_file = File.createTempFile("wasuramoti_",Globals.CACHE_SUFFIX_OGG,temp_dir)
    val asset_fd = context.getAssets.openFd(getAssetPath(num,kamisimo))
    val finstream = asset_fd.createInputStream()
    new FileOutputStream(temp_file).getChannel().transferFrom(
      finstream.getChannel(), 0, asset_fd.getLength())
    func(temp_file)
    finstream.close()
    asset_fd.close()
    temp_file.delete()
  }
}
abstract class ExtAbsBase(context:Context,path:String) extends Reader(context,path){
  def getFile(num:Int,kamisimo:Int):File

  override def exists(num:Int, kamisimo:Int):Boolean = {
    getFile(num,kamisimo).exists()
  }
  override def withFile(num:Int, kamisimo:Int, func:File=>Unit){
    func(getFile(num,kamisimo))
  }
}
class External(context:Context,path:String) extends ExtAbsBase(context,path){
  override def getFile(num:Int, kamisimo:Int):File = {
    val dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+path)
    val file = new File(dir.getAbsolutePath+"/"+addSuffix(dir.getName(),num,kamisimo))
    return(file)
  }
}
class Absolute(context:Context,path:String) extends ExtAbsBase(context,path){
  override def getFile(num:Int, kamisimo:Int):File = {
    val dir = new File(path)
    val file = new File(dir.getAbsolutePath+"/"+addSuffix(dir.getName(),num,kamisimo))
    return(file)
  }
}
