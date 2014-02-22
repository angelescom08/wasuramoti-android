package karuta.hpnpwd.wasuramoti

import _root_.android.content.Context
import _root_.android.widget.{TextView,ViewFlipper}
import _root_.android.util.AttributeSet

// Android 4.1 and 4.1.1 has following bug:
//   Calling TextView.setText(Html.fromHtml(...)) for some specific text raises IndexOutOfBoundsException
//   which is cased by android.text.MeasuredText or android.text.StaticLayout
// This bug was fixed in 4.1.2. However we have to cope with this bug for Android 4.1/4.1.1 users.
// The following way to patch TextView seems the easiest way to avoid exception.
// See following URL for more information:
//   http://code.google.com/p/android/issues/detail?id=35466
//   http://code.google.com/p/android/issues/detail?id=35412
//   http://code.google.com/p/android/issues/detail?id=34872

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

// When we use android.widget.ViewFlipper, changing the orientation a lot of times in a short time raises following exception:
//   java.lang.IllegalArgumentException: Receiver not registered: android.widget.ViewFlipper
// This bug mainly occurs in Android 2.1, but also seems to occur occasionally in Android >= 2.2
// This bug is reported in following URL:
//   https://code.google.com/p/android/issues/detail?id=6191

class SafeViewFlipper(context:Context, attrs:AttributeSet) extends ViewFlipper(context,attrs){
  override def onDetachedFromWindow(){
    try{
      super.onDetachedFromWindow()
    }catch{
      case _:IllegalArgumentException =>
        stopFlipping()
    }
  }
}
