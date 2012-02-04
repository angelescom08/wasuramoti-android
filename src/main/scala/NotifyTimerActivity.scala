package karuta.hpnpwd.wasuramoti

import _root_.android.app.Activity
import _root_.android.os.Bundle 
import _root_.android.view.View

class NotifyTimerActivity extends Activity{
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.notify_timer)
  }
  def onTimerStartClick(v:View){
    //TODO: implement the following
    println("TimerStart Clicked")
  }

}
