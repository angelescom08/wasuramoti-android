package karuta.hpnpwd.wasuramoti

import android.content.Context

import scala.collection.mutable

object PlayHistoryDialog {
  def genHtml(context:Context):Either[String,Int] = {
    if(Utils.isRandom) {
      return Right(R.string.play_history_disabled)
    }
    val history = FudaListHelper.getPlayHistory(context)
    val rows = mutable.Buffer[String]()
    for((num,i) <- history.zipWithIndex){
      rows += s"${i+1}. ${Utils.kimarijiToHtml(context,num,false)}"
    }
    return Left(rows.mkString("<br>"))
  }
}
