package tami.pen.wasuramoti
import _root_.android.preference.ListPreference
import _root_.android.content.Context
import _root_.android.util.AttributeSet
import _root_.android.app.AlertDialog.Builder
import _root_.android.os.Environment
import scala.util.matching.Regex

class ReaderListPreference(context:Context, attrs:AttributeSet) extends ListPreference(context,attrs){
  override def onPrepareDialogBuilder(builder:Builder){
    var entries = for(i <- context.getAssets.list("reader"))yield ("INT:"+i).asInstanceOf[CharSequence]
    val state = Environment.getExternalStorageState
    if(state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY){
      var seq = Seq[String]()
      val expath = Environment.getExternalStorageDirectory
      val pat_rev = new Regex("^" + expath)
      val pat_tag = new Regex("/"+ Globals.READER_DIR + "/")
      Utils.walkDir(expath,5, f =>{
        if(f.getName == Globals.READER_DIR){
          for( i <- f.listFiles() ){
            if(i.isDirectory){
              seq ++= Seq("EXT:"+pat_tag.replaceFirstIn(pat_rev.replaceFirstIn(i.getAbsolutePath(),""),"/<>/"))
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
