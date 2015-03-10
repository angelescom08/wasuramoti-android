package karuta.hpnpwd.wasuramoti

import _root_.android.content.Context
import _root_.android.content.pm.PackageInfo
import _root_.android.os.{Build,StatFs}
import _root_.android.app.ActivityManager
import _root_.android.util.Log
import _root_.java.io.{File,RandomAccessFile}
import _root_.java.nio.ByteBuffer
import _root_.java.nio.channels.FileChannel
import _root_.java.util.zip.CRC32
import scala.collection.mutable

trait BugReportable{
  def toBugReport():String
}

object BugReport{
  val MEGA = 1024 * 1024
  def megabytesAvailable(path:String):(Float,Float) = {
    val stat = new StatFs(path);
    val bytesAvailable = stat.getBlockSize.toLong * stat.getAvailableBlocks.toLong
    val bytesTotal = stat.getBlockSize.toLong * stat.getBlockCount.toLong
    return (bytesAvailable/MEGA.toFloat,bytesTotal/MEGA.toFloat)
  }

  def getCRC(f:File):String = {
    // we don't use java.nio.file.Files.readAllBytes() nor java.io.RandomAccessFile.readFully() ssince it fully read file to memory.
    var raf = null:RandomAccessFile
    var channel = null:FileChannel
    try{
      raf = new RandomAccessFile(f,"r")
      channel = raf.getChannel
      val buffer = ByteBuffer.allocate(2048)
      val crc = new CRC32
      while(channel.read(buffer) > 0){
        buffer.flip
        val b = new Array[Byte](buffer.remaining)
        buffer.get(b)
        crc.update(b)
        buffer.clear
      }
      return f"${crc.getValue}%08X"
    }catch{
      case _:Throwable => return "00000000"
    }finally{
      if(channel != null){
        channel.close
      }
      if(raf != null){
        raf.close
      }
    }
  }

  def listFileCRC(dir:File):Array[String] = {
    dir.listFiles.collect{
      case f if f.isFile => s"(name:${f.getName},size:${f.length},crc32:${getCRC(f)})"
    }
  }
  
  def createBugReport(context:Context):String = {
    val bld = new mutable.StringBuilder
    bld ++= "[build]\n"
    bld ++= s"api_level=${Build.VERSION.SDK_INT}\n"
    bld ++= s"cpu_abi=${Build.CPU_ABI}\n"
    bld ++= s"brand=${Build.BRAND}\n"
    bld ++= s"manufacturer=${Build.MANUFACTURER}\n"
    bld ++= s"product=${Build.PRODUCT}\n"
    bld ++= s"model=${Build.MODEL}\n"
    val doWhenError = {e:Exception =>
      Log.v("wasuramoti","error",e)
      bld ++= s"Error: ${e.toString}\n"
    }

    try{
      bld ++= "[configuration]\n"
      val config = context.getResources.getConfiguration
      bld ++=  s"locale=${config.locale}\n"
      bld ++=  s"screenLayout=${config.screenLayout}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }

    try{
      bld ++= "[display_metrics]\n"
      val metrics = context.getResources.getDisplayMetrics
      bld ++= s"density=${metrics.density}\n"
      bld ++= s"scaledDensity=${metrics.scaledDensity}\n"
      bld ++= s"densityDpi=${metrics.densityDpi}\n"
      bld ++= s"xdpi=${metrics.xdpi}\n"
      bld ++= s"ydpi=${metrics.ydpi}\n"
      bld ++= s"widthPixels=${metrics.widthPixels}\n"
      bld ++= s"heightPixels=${metrics.heightPixels}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }

    var pi = null:PackageInfo
    try{
      bld ++= "[package_info]\n"
      pi = context.getPackageManager.getPackageInfo(context.getPackageName,0)
      bld ++=  s"version_code=${pi.versionCode}\n"
      bld ++=  s"version_name=${pi.versionName}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }
    if(pi != null){
      try{
        bld ++= "[application_info]\n"
        val ai = pi.applicationInfo
        bld ++=  s"flags=${ai.flags}\n"
        bld ++=  s"source_dir=${ai.sourceDir}\n"
        bld ++=  s"data_dir=${ai.dataDir}\n"
        bld ++=  s"external_storage=${Utils.getAllExternalStorageDirectories(context)}\n"
      }catch{
        case e:Exception => doWhenError(e) 
      }
    }

    try{
      bld ++= "[memory_info]\n"
      val am = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      val mi = new ActivityManager.MemoryInfo
      am.getMemoryInfo(mi)
      bld ++= s"avail_mem_mega=${mi.availMem/MEGA.toFloat}\n"
      bld ++= s"threshold_mega=${mi.threshold/MEGA.toFloat}\n"
      bld ++= s"low_memory=${mi.lowMemory}\n"

    }catch{
      case e:Exception => doWhenError(e) 
    }
    try{
      bld ++= "[disk_usage]\n"
      val (avail,tot) = megabytesAvailable(context.getCacheDir.getPath)
      bld ++= s"total_mega=${tot}\n"
      bld ++= s"available_mega=${avail}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }

    try{
      bld ++= "[cache_dir_info]\n"
      val (avail,tot) = megabytesAvailable(context.getCacheDir.getPath)
      bld ++= s"cache_dir=${context.getCacheDir}\n"
      val filelist = listFileCRC(context.getCacheDir).mkString("|")
      bld ++= s"list_file=${filelist}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }
    try{
      bld ++= "[misc]\n"
      val fd_num = new File("/proc/self/fd").listFiles.length
      bld ++= s"proc_self_fd_num=$fd_num\n"
    }catch{
      case e:Exception => doWhenError(e) 
    }
    try{
      bld ++= "[shared_preference]\n"
      for(pref <- Array(Globals.prefs.get,context.getSharedPreferences(FudaListHelper.PREFS_NAME,0))){
        val al = pref.getAll
        for(a <- al.keySet.toArray){
          bld ++= s"${a}=${al.get(a)}\n"
        }
      }
    }catch{
      case e:Exception => doWhenError(e) 
    }
    // TODO: dump variables

    Globals.db_lock.synchronized{
      try{
        bld ++= "[sql_table]\n"
        val db = Globals.database.get.getReadableDatabase
        val c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null)
        if(c.moveToFirst){
          while(!c.isAfterLast){
            val table = c.getString(0)
            val c2 = db.rawQuery(s"SELECT * from $table",null)
            if(c2.moveToFirst){
              bld ++= s"cols_${table}=${c2.getColumnNames.toList.mkString(",")}\n"
              var rowcount = 0
              while(!c2.isAfterLast){
                rowcount += 1
                val cols = mutable.Buffer[String]()
                for(i <- 0 until c2.getColumnCount){
                  cols += c2.getString(i)
                }
                bld ++= s"  ${cols.mkString(",")}\n"
                c2.moveToNext
              }
            }
            c2.close
            c.moveToNext
          }
        }
        c.close
      }catch{
        case e:Exception => doWhenError(e) 
      } 
    }

    try{
      bld ++= "[variables]\n"
      bld ++= s"karuta_player=${Globals.player.map{_.toBugReport}.getOrElse("None")}\n"
    }catch{
      case e:Exception => doWhenError(e) 
    } 
    return bld.toString
  }
}
