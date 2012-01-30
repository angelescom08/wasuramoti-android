package tami.pen.wasuramoti

import _root_.android.app.Activity
import _root_.android.preference.PreferenceManager
import _root_.android.content.{Intent,Context}
import _root_.android.os.{Bundle,Handler}
import _root_.android.view.{View,Menu,MenuItem}
import _root_.android.widget.Button

import _root_.mita.nep.audio.OggVorbisDecoder
import _root_.java.lang.Runnable

import _root_.java.util.{Timer,TimerTask}

import scala.collection.mutable

class WasuramotiActivity extends Activity{
  def setButtonTextNormal(){
    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]
    read_button.setText(FudaListHelper.makeReadIndexMessage(getApplicationContext()))
  }
  def refreshKarutaPlayer(fromProperty:Boolean=false){
    Globals.player = (if(fromProperty){
      ReaderList.makeCurrentReader(getApplicationContext())
    }else{
      Globals.player.map{_.reader}
    }).flatMap(
      reader => AudioHelper.makeKarutaPlayer(getApplicationContext(),reader))
  }
  override def onCreateOptionsMenu(menu: Menu) : Boolean = {
    super.onCreateOptionsMenu(menu)
    val inflater = getMenuInflater()
    inflater.inflate(R.menu.main, menu)
    return true
  }
  override def onOptionsItemSelected(item: MenuItem) : Boolean = {
    item.getItemId match {
      case R.id.menu_shuffle => {
        Utils.confirmDialog(this,Right(R.string.menu_shuffle_confirm),_=>{
          FudaListHelper.shuffle(getApplicationContext())
          FudaListHelper.moveToFirst(getApplicationContext())
          setButtonTextNormal()
          refreshKarutaPlayer(true)
        })
      }
      case R.id.menu_move => println("move")
      case R.id.menu_fudaconf =>
        val intent = new Intent(this,classOf[tami.pen.wasuramoti.FudaConfActivity])
        startActivity(intent)
    }
    return true
  }
  override def onResume(){
    super.onResume()
    setButtonTextNormal()
    refreshKarutaPlayer(true)
  }

  override def onCreate(savedInstanceState: Bundle) {
    val context = this
    super.onCreate(savedInstanceState)
    val pinfo = getPackageManager().getPackageInfo(getPackageName(), 0)
    setTitle(getResources().getString(R.string.app_name) + " ver " + pinfo.versionName)
    Globals.database = Some(new DictionaryOpenHelper(getApplicationContext()))
    Globals.prefs = Some(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()))
    ReaderList.setDefaultReader(getApplicationContext())
    setContentView(R.layout.main)

    val read_button = findViewById(R.id.read_button).asInstanceOf[Button]
    read_button.setOnClickListener(new View.OnClickListener() {
      var timer_autoread = None:Option[Timer]
      var timer_start = None:Option[Timer]
      override def onClick(v:View) {
        if(Globals.player.isEmpty){
          Utils.messageDialog(context,Right(R.string.reader_not_found))
          return
        }
        val player = Globals.player.get
        timer_autoread.foreach(_.cancel())
        timer_autoread = None
        
        if(!timer_start.isEmpty){
          timer_start.get.cancel()
          timer_start = None
          setButtonTextNormal()
          return
        }
        if(player.is_playing){
          if(!player.is_kaminoku){
            player.stop()
            setButtonTextNormal()
          }
        }else{
          read_button.setText(R.string.now_playing)
          val handler = new Handler()
          timer_start = Some(new Timer())
          timer_start.get.schedule(new TimerTask(){
            override def run(){
              player.play(
                _ => if("SHUFFLE" == Globals.prefs.get.getString("read_order",null)){ 
                       FudaListHelper.moveNext(getApplicationContext())
                     },
                _ => {
                  refreshKarutaPlayer()
                  handler.post(new Runnable(){
                    override def run(){setButtonTextNormal()}
                  })
                  if(Globals.prefs.get.getBoolean("read_auto",false)){
                    timer_autoread = Some(new Timer())
                    timer_autoread.get.schedule(new TimerTask(){
                      override def run(){
                        handler.post(new Runnable(){
                          override def run(){onClick(v)}
                        })
                        timer_autoread.foreach(_.cancel())
                        timer_autoread = None
                      }},(Globals.prefs.get.getString("read_auto_span","0.0").toDouble*1000.0).toLong
                    )
                  }
              })
              timer_start.foreach(_.cancel())
              timer_start = None
            }},(Globals.prefs.get.getString("wav_begin_read","0.0").toDouble*1000.0).toLong)
        }
      }
    });
  }
}
