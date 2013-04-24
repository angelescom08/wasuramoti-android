package karuta.hpnpwd.wasuramoti

import _root_.android.content.Context
import _root_.android.widget.EditText
import _root_.android.util.AttributeSet
import _root_.java.util.Locale

class LocalizationEditText(context:Context,attrs:AttributeSet) extends EditText(context,attrs){
  def getLocalizationText():String = {
    var text = super.getText().toString()
    if(!Romanization.is_japanese(context)){
      text = Romanization.roma_to_jap(text)
    }
    return text
  }
  def setLocalizationText(text:String){
    setText(Romanization.jap_to_local(context,text))
  }
}

object Romanization{
  lazy val PAT_ROMA = "([kstnhmyrwgzdbp]?[aiueo]| +)".r
  lazy val ROMA_TO_JAP = Array(
    ("a", "あ"), ("i", "い"), ("u", "う"), ("e", "え"),  ("o","お"),
    ("ka","か"), ("ki","き"), ("ku","く"), ("ke","け"), ("ko","こ"),
    ("sa","さ"), ("si","し"), ("su","す"), ("se","せ"), ("so","そ"),
    ("ta","た"), ("ti","ち"), ("tu","つ"), ("te","て"), ("to","と"),
    ("na","な"), ("ni","に"), ("nu","ぬ"), ("ne","ね"), ("no","の"),
    ("ha","は"), ("hi","ひ"), ("hu","ふ"), ("he","へ"), ("ho","ほ"),
    ("ma","ま"), ("mi","み"), ("mu","む"), ("me","め"), ("mo","も"),
    ("ya","や"),              ("yu","ゆ"),              ("yo","よ"),
    ("ra","ら"), ("ri","り"), ("ru","る"), ("re","れ"), ("ro","ろ"),
    ("wa","わ"),              ("wo","を"),              ("nn","ん"),
    ("ga","が"), ("gi","ぎ"), ("gu","ぐ"), ("ge","げ"), ("go","ご"),
    ("za","ざ"), ("zi","じ"), ("zu","ず"), ("ze","ぜ"), ("zo","ぞ"),
    ("da","だ"), ("di","ぢ"), ("du","づ"), ("de","で"), ("do","ど"),
    ("ba","ば"), ("bi","び"), ("bu","ぶ"), ("be","べ"), ("bo","ぼ"),
    ("pa","ぱ"), ("pi","ぴ"), ("pu","ぷ"), ("pe","ぺ"), ("po","ぽ")).toMap

  lazy val JAP_TO_ROMA = ROMA_TO_JAP.collect({case x=>x.swap})

  lazy val ZENKAKU_TO_HANKAKU = Array(
    ("＊","*"), ("？","?"), ("［","["), ("］","]"),
    ("０","0"), ("１","1"), ("２","2"), ("３","3"), ("４","4"),
    ("５","5"), ("６","6"), ("７","7"), ("８","8"), ("９","9")).toMap

  def jap_to_roma(str:String):String = {
    str.toCharArray.map(_.toString).map(
      x => JAP_TO_ROMA.getOrElse(x,x)
    ).mkString
  }
  def roma_to_jap(str:String):String = {
    PAT_ROMA.findAllIn(str.toLowerCase).map(_.toString).map(
      x => ROMA_TO_JAP.getOrElse(x,x)
    ).mkString + PAT_ROMA.replaceAllIn(str,"")
  }

  def zenkaku_to_hankaku(str:String) = {
    str.toCharArray.map(_.toString).map(
      x => ZENKAKU_TO_HANKAKU.getOrElse(x,x)
    ).mkString
  }

  def is_japanese(context:Context):Boolean = {
    val loc = context.getResources.getConfiguration.locale
    loc.equals(Locale.JAPAN) || loc.equals(Locale.JAPANESE)
  }
  def jap_to_local(context:Context,text:String):String = if (is_japanese(context)){ text } else {jap_to_roma(text)}
}
