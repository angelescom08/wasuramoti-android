package karuta.hpnpwd.wasuramoti

import _root_.android.content.{Context,Intent,ComponentName}
import _root_.android.content.pm.{ResolveInfo,PackageInfo}
import _root_.android.os.{Build,StatFs}
import _root_.android.app.{AlertDialog,ActivityManager}
import _root_.android.util.{Base64,Log}
import _root_.android.net.Uri
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.TextView

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
    // we don't use java.nio.file.Files.readAllBytes() nor java.io.RandomAccessFile.readFully() since it fully read file to memory.
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

  def showBugReportDialog(context:Context){
    val builder = new AlertDialog.Builder(context)
    val view = LayoutInflater.from(context).inflate(R.layout.bug_report_dialog,null)
    val mail = view.findViewById(R.id.developer_mail_addr).asInstanceOf[TextView]
    Utils.setUnderline(mail)
    mail.setOnClickListener(new View.OnClickListener(){
      override def onClick(view:View){
        sendMailToDeveloper(context) 
      }
    })

    builder.setNegativeButton(android.R.string.cancel,null)
      .setTitle(R.string.conf_bug_report)
      .setView(view)
    Utils.showDialogAndSetGlobalRef(builder.create)
  }

  def sendMailToDeveloper(context:Context){
    val address = context.getResources.getString(R.string.developer_mail_addr)
    val subject = context.getResources.getString(R.string.bug_report_subject)
    val uri = s"mailto:${address}?subject=${Uri.encode(subject)}"
    val intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(uri))
    context.startActivity(intent) 
  }

  def showAnonymousBugReport(context:Context){
    if( android.os.Build.VERSION.SDK_INT < 8 ){
      Utils.messageDialog(context,Right(R.string.bug_report_not_supported))
      return
    }
    val pm = context.getPackageManager
    // dummy data to get list of activities
    val i_temp = new Intent(Intent.ACTION_VIEW,Uri.parse("http://www.google.com/"))
    val list = scala.collection.JavaConversions.asScalaBuffer[ResolveInfo](pm.queryIntentActivities(i_temp,0))
    if(list.isEmpty){
      Utils.messageDialog(context,Right(R.string.browser_not_found))
    }else{
      val defaultActivities = list.filter{ info =>
        val filters = new java.util.ArrayList[android.content.IntentFilter]()
        val activities = new java.util.ArrayList[android.content.ComponentName]()
        pm.getPreferredActivities(filters,activities,info.activityInfo.packageName)
        ! activities.isEmpty
      }
      // TODO: show activity chooser
      (defaultActivities ++ list).exists{ ri =>
        val comp = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
        val intent = pm.getLaunchIntentForPackage(ri.activityInfo.packageName)
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setComponent(comp)
        val post_url = context.getResources.getString(R.string.bug_report_url)
        val mail_addr = context.getResources.getString(R.string.developer_mail_addr)
        val bug_report = Base64.encodeToString(BugReport.createBugReport(context).getBytes("UTF-8"),Base64.DEFAULT | Base64.NO_WRAP)
        val html = context.getResources.getString(R.string.bug_report_html,mail_addr,post_url,bug_report)
        val dataUri = "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.getBytes("UTF-8"),Base64.DEFAULT | Base64.NO_WRAP)
        intent.setData(Uri.parse(dataUri))
        try{
          context.startActivity(intent)
          true
        }catch{
          case _:android.content.ActivityNotFoundException => false
        }
      }
    }
    return
  }

  def createBugReport(context:Context):String = {
    val bld = new mutable.StringBuilder
    bld ++= "[build]\n"
    bld ++= s"api_level=${Build.VERSION.SDK_INT}\n"
    bld ++= s"release=${Build.VERSION.RELEASE}\n"
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
      val rt = Runtime.getRuntime
      bld ++= s"max_memory=${rt.maxMemory/MEGA}\n"
      bld ++= s"total_memory=${rt.totalMemory/MEGA.toFloat}\n"
      bld ++= s"free_memory=${rt.freeMemory/MEGA.toFloat}\n"
      val am = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      bld ++= s"memory_class=${am.getMemoryClass}\n"
      val mi = new ActivityManager.MemoryInfo
      am.getMemoryInfo(mi)
      bld ++= s"avail_mem=${mi.availMem/MEGA.toFloat}\n"
      bld ++= s"threshold=${mi.threshold/MEGA.toFloat}\n"
      bld ++= s"low_memory=${mi.lowMemory}\n"

    }catch{
      case e:Exception => doWhenError(e)
    }
    try{
      bld ++= "[disk_usage]\n"
      val (avail,tot) = megabytesAvailable(context.getCacheDir.getPath)
      bld ++= s"total=${tot}\n"
      bld ++= s"available=${avail}\n"
    }catch{
      case e:Exception => doWhenError(e)
    }

    try{
      bld ++= "[cache_dir_info]\n"
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
            c.moveToNext
            // fudalist is large so we don't bugreport (in order to avoid exceeding browser's url length limit)
            if(table != "fudalist"){
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
            }
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
