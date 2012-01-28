package tami.pen.wasuramoti
import _root_.android.preference.ListPreference
import _root_.android.content.Context
import _root_.android.util.AttributeSet
import _root_.android.app.AlertDialog.Builder
import _root_.android.os.Environment
import _root_.java.io.{IOException,File,FileOutputStream}

object ReaderList{
  def makeCurrentReader(context:Context):Reader = {
    val path = Globals.prefs.get.getString("reader_path",null)
    makeReader(context,path)
  }
  def makeReader(context:Context,path:String):Reader = {
    if(path.startsWith("INT:")){
      new Asset(context,path.replaceFirst("INT:",""))
    }else{
      new External(context,path.replaceFirst("EXT:","").replaceFirst("/<>/","/"+Globals.READER_DIR+"/"))
    }
  }
  abstract class Reader(context:Context,path:String){
    def basename:String = new File(path).getName()
    def addSuffix(str:String,num:Int, kamisimo:Int):String = str+"_%03d_%d.ogg".format(num,kamisimo)
    def exists(num:Int, kamisimo:Int):Boolean //TODO: not only check the existance of .ogg but also vaild .ogg file with identical sample rate
    def withFile(num:Int, kamisimo:Int, func:File=>Unit):Unit
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
        case e:IOException => false
      }
    }
    override def withFile(num:Int, kamisimo:Int, func:File=>Unit){
      val temp_dir = context.getCacheDir()
      val temp_file = File.createTempFile("wasuramoti",".ogg",temp_dir)
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
  class External(context:Context,path:String) extends Reader(context,path){
    def getFile(num:Int, kamisimo:Int):File = {
      val dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+path)
      val file = new File(dir.getAbsolutePath+"/"+addSuffix(dir.getName(),num,kamisimo))
      return(file)
    }
    override def exists(num:Int, kamisimo:Int):Boolean = {
      getFile(num,kamisimo).exists()
    }
    override def withFile(num:Int, kamisimo:Int, func:File=>Unit){
      func(getFile(num,kamisimo))
    }
  }
}

class ReaderListPreference(context:Context, attrs:AttributeSet) extends ListPreference(context,attrs){
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
  override def onPrepareDialogBuilder(builder:Builder){
    var entries = for(i <- context.getAssets.list(Globals.ASSETS_READER_DIR))yield ("INT:"+i).asInstanceOf[CharSequence]
    val state = Environment.getExternalStorageState
    if(state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY){
      var seq = Seq[String]()
      val expath = Environment.getExternalStorageDirectory
      Utils.walkDir(expath,5, f =>{
        if(f.getName == Globals.READER_DIR){
          for( i <- f.listFiles() ){
            if(i.isDirectory){
              seq ++= Seq("EXT:"+i.getAbsolutePath.replaceFirst("^"+expath,"").replaceFirst("/"+Globals.READER_DIR+"/","/<>/"))
            }
          }
        }}
      )
      entries ++= seq
    }
    setEntries(entries)
    setEntryValues(entries)
    super.onPrepareDialogBuilder(builder)
  }
}
