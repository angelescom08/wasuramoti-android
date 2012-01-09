package tami.pen.wasuramoti

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView
import _root_.android.util.Log
import _root_.android.view.View

class WasuramotiActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.mainlayout);
    val read_button = findViewById(R.id.read_button)
    read_button.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View) {
        println("Button Clicked")
      }
    });
    println("tamipw")
  }
}
