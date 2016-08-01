package karuta.hpnpwd.wasuramoti

import scala.collection.mutable.Buffer
import scala.collection.JavaConversions

import android.preference.ListPreference
import android.content.{Context,DialogInterface}
import android.util.AttributeSet
import android.app.{AlertDialog,ProgressDialog,Activity}
import android.os.{Environment,AsyncTask}
import android.view.{Gravity,View}
import android.widget.{ArrayAdapter,Button}
import java.io.{IOException,File,FileOutputStream}
import karuta.hpnpwd.audio.OggVorbisDecoder

object ReaderList{
  def setDefaultReader(context:Context){
    if(!Globals.prefs.get.contains("reader_path")){
      for(p <- context.getAssets.list(Globals.ASSETS_READER_DIR)){
        val reader_path = "INT:" + p
        val reader = makeReader(context,reader_path)
        if(reader.canReadAll._1){
          Globals.prefs.get.edit.putString("reader_path",reader_path).commit
          return
        }
      }
    }
  }
  def makeCurrentReader(context:Context):Option[Reader] = {
    Option(Globals.prefs.get.getString("reader_path",null)).map{
       makeReader(context,_)
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
      FudaListHelper.updateReaders(getEntryValues)

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
      val prev_value = getValue
      super.onDialogClosed(positiveResult)
      val cur_value = getValue
      val activity = context.asInstanceOf[ConfActivity]
      if(Utils.isExternalReaderPath(cur_value) && !activity.checkRequestMarshmallowPermission(activity.REQ_PERM_PREFERENCE_CHOOSE_READER)){
        setValue(prev_value) // cancel
      }else{
        val (ok,message,joka_upper,joka_lower) = ReaderList.makeReader(context,cur_value).canReadAll
        if(!ok){
          Utils.messageDialog(context,Left(message))
          setValue(prev_value) // cancel
        }else{
          FudaListHelper.saveRestoreReadOrderJoka(prev_value,cur_value,joka_upper,joka_lower)
        }
      }
    }else{
      super.onDialogClosed(positiveResult)
    }
  }
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val entvals = Buffer[CharSequence]()
    val entries = Buffer[CharSequence]()

    for(x <- context.getAssets.list(Globals.ASSETS_READER_DIR)){
      entvals += "INT:"+x
      entries += x
    }
    // I don't think switching by scanFileSystem flag is correct way to do this
    // TODO: override Preference and switch which AlertDialog to show
    val bundle = getExtras
    if(bundle.getBoolean("scanFileSystem",false)){
      new SearchDirectoryTask().execute(new AnyRef())
      bundle.remove("scanFileSystem")
    }else{
      for(x <- FudaListHelper.selectNonInternalReaders){
        entvals += x
        entries += new File(x).getName
      }
    }

    setEntries(entries.toArray)
    setEntryValues(entvals.toArray)
    adapter = Some(new ArrayAdapter[CharSequence](context,android.R.layout.simple_spinner_dropdown_item,JavaConversions.bufferAsJavaList(entries)))
    builder.setAdapter(adapter.get,null)

    val ctitle = android.view.LayoutInflater.from(context).inflate(R.layout.reader_list_pref_title,null)
    ctitle.findViewById(R.id.btn_audio_decode_test).asInstanceOf[Button].setOnClickListener(new View.OnClickListener(){
      override def onClick(view:View){
        new AudioDecodeTestDialog(context).show
      }
    })
    builder.setCustomTitle(ctitle)

    builder.setNeutralButton(R.string.button_config, new DialogInterface.OnClickListener(){
        override def onClick(dialog:DialogInterface,which:Int){
          new ScanReaderConfDialog(context,{()=>showDialogPublic(true)}).show
        }
      })

    super.onPrepareDialogBuilder(builder)
  }

  def showDialogPublic(scanFileSystem:Boolean){
    if(scanFileSystem){
      getExtras.putBoolean("scanFileSystem",true)
    }
    showDialog(null)
  }
}

class OggDecodeFailException(s:String) extends Exception(s){
}

abstract class Reader(context:Context,val path:String){
  type AssetOrFile = Either[String,File]
  def basename:String = new File(path).getName()
  def addSuffix(str:String,num:Int, kamisimo:Int):String = str+"_%03d_%d.ogg".format(num,kamisimo)
  def canRead(num:Int, kamisimo:Int):(Boolean,String) //TODO: not only check the existance of .ogg but also vaild .ogg file with identical sample rate
  def bothReadable(cur_num:Int, next_num:Int):(Boolean,String) = {
    val maxn = AllFuda.list.length + 1
    val pr = AudioHelper.genReadNumKamiSimoPairs(context,cur_num,next_num)
      .filter{_._1 != maxn}
      .map{case(rn,sk,_) => (rn,sk)}
      .toSet
    val pairs = pr.map{case(rn,sk) => canRead(rn,sk)}
    if(pairs.isEmpty){
      (false,context.getResources.getString(R.string.player_none_reason_dont_have_to_read))
    }else{
      if(pairs.forall{_._1}){
        (true,null)
      }else{
        (false,context.getResources.getString(R.string.player_none_reason_not_readable) + " " + pairs.filter{_._2 != null}.map{_._2}.mkString("\n"))
      }
    }
  }
  def withAssetOrFile(num:Int, kamisimo:Int, func:AssetOrFile=>Unit):Unit
  def withDecodedWav(num:Int, kamisimo:Int, func:(WavBuffer)=>Unit){
    val decoder = new OggVorbisDecoder()
    withAssetOrFile(num,kamisimo,asset_or_file => {
      val buffer = asset_or_file match{
        case Left(asset) => decoder.decodeAssetToShortBuffer(context,asset)
        case Right(file) => decoder.decodeFileToShortBuffer(file.getAbsolutePath())
      }
      if(buffer != null && decoder.data_length > 0){
        val wav = new WavBuffer(buffer,decoder,num,kamisimo)
        func(wav)
      }else{
        throw new OggDecodeFailException("decode failed: "+asset_or_file.fold(identity,_.getName))
      }
    })
  }
  def canReadAll():(Boolean,String,Boolean,Boolean) = {
    val joka_upper = canRead(0,1)._1
    val joka_lower = canRead(0,2)._1
    for(num <- 1 to 100; kamisimo <- 1 to 2){
      canRead(num,kamisimo) match {
        case (false,reason) => 
          return(false, context.getResources.getString(R.string.cannot_read_audio,addSuffix(basename,num,kamisimo)) + "\n" + reason,
            joka_upper,joka_lower)
        case (true,_) => // do nothing
      }
    }
    (true,"",joka_upper,joka_lower)
  }
}
class Asset(context:Context,path:String) extends Reader(context,path){
  def getAssetPath(num:Int, kamisimo:Int):String = Globals.ASSETS_READER_DIR+"/"+path+"/"+addSuffix(path,num,kamisimo)

  override def canRead(num:Int, kamisimo:Int):(Boolean,String) = {
    try{
      val fp = context.getAssets.open(getAssetPath(num,kamisimo))
      fp.close()
      (true,null)
    }catch{
      case e:IOException => (false,e.getMessage)
    }
  }
  override def withAssetOrFile(num:Int, kamisimo:Int, func:AssetOrFile=>Unit){
    if(android.os.Build.VERSION.SDK_INT >= 9){
      // functions in asset_manager.h and asset_manager_jni.h is only available in Android >= 2.3
      val assetPath = getAssetPath(num,kamisimo)
      func(Left(assetPath))
    }else{
      val temp_dir = context.getCacheDir()
      val temp_file = File.createTempFile("wasuramoti_",Globals.CACHE_SUFFIX_OGG,temp_dir)
      val asset_fd = context.getAssets.openFd(getAssetPath(num,kamisimo))
      val finstream = asset_fd.createInputStream()
      new FileOutputStream(temp_file).getChannel().transferFrom(
        finstream.getChannel(), 0, asset_fd.getLength())
      func(Right(temp_file))
      finstream.close()
      asset_fd.close()
      temp_file.delete()
    }
  }
}
abstract class ExtAbsBase(context:Context,path:String) extends Reader(context,path){
  def getFile(num:Int,kamisimo:Int):File

  override def canRead(num:Int, kamisimo:Int):(Boolean,String) = {
    val file = getFile(num,kamisimo)
    if(file.canRead){
      (true,null)
    }else{
      val reason_id = if(file.exists){
        R.string.cannot_read_audio_reason_maybe_permission
      }else{
        R.string.cannot_read_audio_reason_not_found
      }
      (false,context.getResources.getString(reason_id,file.getPath))
    }
  }
  override def withAssetOrFile(num:Int, kamisimo:Int, func:AssetOrFile=>Unit){
    func(Right(getFile(num,kamisimo)))
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
