package karuta.hpnpwd.wasuramoti

import android.content.Context
import android.widget.TextView
import android.util.AttributeSet

// Android 4.1 and 4.1.1 has following bug:
//   Calling TextView.setText(Html.fromHtml(...)) for some specific text raises IndexOutOfBoundsException
//   which is cased by android.text.MeasuredText or android.text.StaticLayout
// This bug was fixed in 4.1.2. However we have to cope with this bug for Android 4.1/4.1.1 users.
// The following way to patch TextView seems the easiest way to avoid exception.
// See following URL for more information:
//   https://issuetracker.google.com/code/p/android/issues/detail?id=35466
//   https://issuetracker.google.com/code/p/android/issues/detail?id=35412
//   https://issuetracker.google.com/code/p/android/issues/detail?id=34872
//
// Therefore, *ALL* the TextView which uses Html.fromHtml() have to use this class.

class PatchedTextView(context:Context, attrs:AttributeSet) extends TextView(context, attrs) {
  def this(context:Context, attrs:AttributeSet, def_style:Int) = this(context, attrs)
  override def onMeasure(widthMeasureSpec:Int, heightMeasureSpec:Int) {
    try{
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }catch{
      case _:IndexOutOfBoundsException =>
        setText(getText.toString)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }
  override def setGravity(gravity:Int){
    try{
      super.setGravity(gravity)
    }catch{
      case _:IndexOutOfBoundsException =>
        setText(getText.toString)
        super.setGravity(gravity)
    }
  }
  override def setText(text:CharSequence, typ:TextView.BufferType) {
    try{
      super.setText(text, typ)
    }catch{
      case _:IndexOutOfBoundsException =>
        setText(text.toString)
    }
  }
}
