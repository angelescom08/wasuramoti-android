package karuta.hpnpwd.wasuramoti

import android.content.{Context,Intent,ComponentName}
import android.content.pm.{ResolveInfo,PackageInfo}
import android.os.{Build,StatFs}
import android.annotation.TargetApi
import android.app.{AlertDialog,ActivityManager}
import android.util.{Base64,Log}
import android.net.Uri
import android.view.{View,LayoutInflater}
import android.widget.{TextView,Button}

import java.io.{File,RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.CRC32

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
    val form = view.findViewById(R.id.bug_report_anonymous_form).asInstanceOf[Button]
    form.setOnClickListener(new View.OnClickListener(){
      override def onClick(view:View){
        showAnonymousForm(context)
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

  @TargetApi(8) // android.util.Base64 requires API >= 8
  def showAnonymousForm(context:Context){
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
        val bug_report = BugReport.createBugReport(context)
        val html = context.getResources.getString(R.string.bug_report_html,post_url,bug_report)
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

  @TargetApi(8) // android.util.Base64 and android.os.Build#CPU_ABI2 requires API >= 8
  def createBugReport(context:Context):String = {
    val bao = new java.io.ByteArrayOutputStream()
    val base = new android.util.Base64OutputStream(bao, Base64.DEFAULT|Base64.NO_WRAP)
    val gzip = new java.util.zip.GZIPOutputStream(base)
    val writer = new java.io.PrintWriter(gzip)
    writer.println("[build]")
    writer.println(s"api_level=${Build.VERSION.SDK_INT}")
    writer.println(s"release=${Build.VERSION.RELEASE}")
    if(Build.VERSION.SDK_INT >= 21){
      writer.println(s"supported_abis=${Build.SUPPORTED_ABIS.toList}")
    }else{
      writer.println(s"cpu_abi=${Build.CPU_ABI}")
      writer.println(s"cpu_abi2=${Build.CPU_ABI2}")
    }
    writer.println(s"brand=${Build.BRAND}")
    writer.println(s"manufacturer=${Build.MANUFACTURER}")
    writer.println(s"product=${Build.PRODUCT}")
    writer.println(s"model=${Build.MODEL}")
    val doWhenError = {e:Throwable =>
      Log.v("wasuramoti","error",e)
      writer.println(s"Error: ${e.toString}")
    }

    try{
      writer.println("[stb_vorbis]")
      import karuta.hpnpwd.audio.OggVorbisDecoder
      OggVorbisDecoder.loadStbVorbis()
      writer.println(s"loaded=${OggVorbisDecoder.library_loaded}")
      writer.println(s"error=${OggVorbisDecoder.unsatisfied_link_error}")
    }catch{
      case e:Throwable => doWhenError(e)
    }

    try{
      writer.println("[configuration]")
      val config = context.getResources.getConfiguration
      writer.println( s"locale=${config.locale}")
      writer.println( s"screenLayout=${config.screenLayout}")
    }catch{
      case e:Exception => doWhenError(e)
    }

    try{
      writer.println("[display_metrics]")
      val metrics = context.getResources.getDisplayMetrics
      writer.println(s"density=${metrics.density}")
      writer.println(s"scaledDensity=${metrics.scaledDensity}")
      writer.println(s"densityDpi=${metrics.densityDpi}")
      writer.println(s"xdpi=${metrics.xdpi}")
      writer.println(s"ydpi=${metrics.ydpi}")
      writer.println(s"widthPixels=${metrics.widthPixels}")
      writer.println(s"heightPixels=${metrics.heightPixels}")
    }catch{
      case e:Exception => doWhenError(e)
    }

    var pi = null:PackageInfo
    try{
      writer.println("[package_info]")
      pi = context.getPackageManager.getPackageInfo(context.getPackageName,0)
      writer.println( s"version_code=${pi.versionCode}")
      writer.println( s"version_name=${pi.versionName}")
    }catch{
      case e:Exception => doWhenError(e)
    }
    if(pi != null){
      try{
        writer.println("[application_info]")
        val ai = pi.applicationInfo
        writer.println( s"flags=${ai.flags}")
        writer.println( s"source_dir=${ai.sourceDir}")
        writer.println( s"data_dir=${ai.dataDir}")
        writer.println( s"external_storage=${Utils.getAllExternalStorageDirectories(context)}")
      }catch{
        case e:Exception => doWhenError(e)
      }
    }

    try{
      writer.println("[memory_info]")
      val rt = Runtime.getRuntime
      writer.println(s"max_memory=${rt.maxMemory/MEGA}")
      writer.println(s"total_memory=${rt.totalMemory/MEGA.toFloat}")
      writer.println(s"free_memory=${rt.freeMemory/MEGA.toFloat}")
      val am = context.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      writer.println(s"memory_class=${am.getMemoryClass}")
      val mi = new ActivityManager.MemoryInfo
      am.getMemoryInfo(mi)
      writer.println(s"avail_mem=${mi.availMem/MEGA.toFloat}")
      writer.println(s"threshold=${mi.threshold/MEGA.toFloat}")
      writer.println(s"low_memory=${mi.lowMemory}")

    }catch{
      case e:Exception => doWhenError(e)
    }
    try{
      writer.println("[disk_usage]")
      val (avail,tot) = megabytesAvailable(context.getCacheDir.getPath)
      writer.println(s"total=${tot}")
      writer.println(s"available=${avail}")
    }catch{
      case e:Exception => doWhenError(e)
    }

    try{
      writer.println("[cache_dir_info]")
      writer.println(s"cache_dir=${context.getCacheDir}")
      val filelist = listFileCRC(context.getCacheDir).mkString("|")
      writer.println(s"list_file=${filelist}")
    }catch{
      case e:Exception => doWhenError(e)
    }
    try{
      writer.println("[misc]")
      val fd_num = new File("/proc/self/fd").listFiles.length
      writer.println(s"proc_self_fd_num=$fd_num")
    }catch{
      case e:Exception => doWhenError(e)
    }
    try{
      writer.println("[shared_preference]")
      for(pref <- Array(Globals.prefs.get,context.getSharedPreferences(FudaListHelper.PREFS_NAME,0))){
        val al = pref.getAll
        for(a <- al.keySet.toArray){
          writer.println(s"${a}=${al.get(a)}")
        }
      }
    }catch{
      case e:Exception => doWhenError(e)
    }
    // TODO: dump variables

    Globals.db_lock.synchronized{
      try{
        writer.println("[sql_table]")
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
                writer.println(s"cols_${table}=${c2.getColumnNames.toList.mkString(",")}")
                var rowcount = 0
                while(!c2.isAfterLast){
                  rowcount += 1
                  val cols = mutable.Buffer[String]()
                  for(i <- 0 until c2.getColumnCount){
                    cols += c2.getString(i)
                  }
                  writer.println(s"  ${cols.mkString(",")}")
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
      writer.println("[variables]")
      writer.println(s"karuta_player=${Globals.player.map{_.toBugReport}.getOrElse("None")}")
    }catch{
      case e:Exception => doWhenError(e)
    }
    writer.close() // Maybe we don't need this since base stream is ByteArrayOutputStream
    return bao.toString
  }
}
