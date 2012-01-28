package tami.pen.wasuramoti
import _root_.android.preference.ListPreference
import _root_.android.content.Context
import _root_.android.util.AttributeSet
import _root_.android.app.AlertDialog.Builder
import _root_.android.os.Environment
import _root_.java.io.{IOException,File}

object ReaderList{
  def makeReader(context:Context,path:String):Reader = {
    if(path.startsWith("INT:")){
      new Asset(context,path.replaceFirst("INT:",""))
    }else{
      new External(context,path.replaceFirst("EXT:","").replaceFirst("/<>/","/"+Globals.READER_DIR+"/"))
    }
  }
  abstract class Reader(context:Context){
    def basename:String
    def addSuffix(str:String,num:Int, kamisimo:Int):String = str+"_%03d_%d.ogg".format(num,kamisimo)
    def existsAll():(Boolean,String) = {
      for(num <- 0 to 100; kamisimo <- 1 to 2 if num > 0 || kamisimo == 2){
        if(!exists(num,kamisimo)){
          return(false, addSuffix(basename,num,kamisimo) + " not found")
        }
      }
      (true,"")
    }
    def exists(num:Int, kamisimo:Int):Boolean //TODO: not only check the existance of .ogg but also vaild .ogg file with identical sample rate
  }
  class Asset(context:Context,path:String) extends Reader(context){
    override def basename = new File(path).getName()
    override def exists(num:Int, kamisimo:Int):Boolean = {
      try{
        val p = Globals.ASSETS_READER_DIR+"/"+path+"/"+addSuffix(path,num,kamisimo)
        context.getAssets.open(p)
        true
      }catch{
        case e:IOException => false
      }
    }
  }
  class External(context:Context,path:String) extends Reader(context){
    override def basename = new File(path).getName()
    override def exists(num:Int, kamisimo:Int):Boolean = {
      var dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+path)
      var file = new File(dir.getAbsolutePath+"/"+addSuffix(dir.getName(),num,kamisimo))
      file.exists()
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
