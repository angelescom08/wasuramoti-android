package karuta.hpnpwd.wasuramoti

import scala.collection.mutable.Buffer

import _root_.android.preference.ListPreference
import _root_.android.content.{Context,DialogInterface}
import _root_.android.util.AttributeSet
import _root_.android.app.AlertDialog
import _root_.android.os.Environment
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
    }else{
      new External(context,path.replaceFirst("EXT:","").replaceFirst("/<>/","/"+Globals.READER_DIR+"/"))
    }
  }
}

class ReaderListPreference(context:Context, attrs:AttributeSet) extends ListPreference(context,attrs) with PreferenceCustom{
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
    val state = Environment.getExternalStorageState
    if(state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY){
      val expath = Environment.getExternalStorageDirectory
      Utils.walkDir(expath,4, f =>{
        if(f.getName == Globals.READER_DIR){
          val files = f.listFiles()
          if(files != null){
            for( i <- files if i.isDirectory() ){
              entvals += "EXT:"+i.getAbsolutePath.replaceFirst("^"+expath,"").replaceFirst("/"+Globals.READER_DIR+"/","/<>/")
              entries += i.getName
            }
          }
        }}
      )
    }
    setEntries(entries.toArray)
    setEntryValues(entvals.toArray)

    builder.setNeutralButton(R.string.button_help, new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          Utils.generalHtmlDialog(context,R.string.how_to_add_reader_html)
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
    exists(simo_num,2) && exists(kami_num,1)
  }
  def withFile(num:Int, kamisimo:Int, func:File=>Unit):Unit
  def withDecodedWav(num:Int, kamisimo:Int, func:(WavBuffer)=>Unit){
    val wav_file = File.createTempFile("wasuramoti_",Globals.CACHE_SUFFIX_WAV,context.getCacheDir())
    val decoder = new OggVorbisDecoder()
    var rval = true
    withFile(num,kamisimo,temp_file => {
      rval = decoder.decode(temp_file.getAbsolutePath(),wav_file.getAbsolutePath())
      if(rval){
        AudioHelper.withMappedShortsFromFile(wav_file,buffer => {
          val wav = new WavBuffer(buffer,wav_file,decoder)
          func(wav)
        })
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
