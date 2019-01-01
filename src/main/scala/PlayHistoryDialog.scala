package karuta.hpnpwd.wasuramoti

import android.content.Context

import scala.collection.mutable

object PlayHistoryDialog {
  def genHtml(context:Context):Either[String,Int] = {
    if(Utils.isRandom) {
      return Right(R.string.play_history_disabled)
    }
    // To avoid revealing what to read next, exclude current poem from history unless poem text is visible.
    // When poem text is visible, there's no meaning to hide current poem, so include it to history.
    val history = FudaListHelper.getPlayHistory(context,YomiInfoUtils.showPoemText)
    val rows = mutable.Buffer[String]()
    for((num,i) <- history.zipWithIndex){
      rows += s"${i+1}. ${Utils.kimarijiToHtml(context,num,false)}"
    }
    return Left(rows.mkString("<br>"))
  }
}
