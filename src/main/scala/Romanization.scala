package karuta.hpnpwd.wasuramoti

import scala.util.matching.Regex
import _root_.android.content.Context
import _root_.android.widget.EditText
import _root_.android.text.Editable
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
    if(!Romanization.is_japanese(context)){
      setText(Romanization.jap_to_roma(text))
    }else{
      setText(text)
    }
  }
}

object Romanization{
  val pat_roma = new Regex("([kstnhmyrwgzdbp]?[aiueo]| +)")
  val roma_to_jap = Array(
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
    ("pa","ぱ"), ("pi","ぴ"), ("pu","ぷ"), ("pe","ぺ"), ("po","ぽ"))

  def jap_to_roma(str:String):String = {
    str.toCharArray.map(_.toString).map( x =>
      roma_to_jap.find({case (roma,jap) => jap == x }) match {
        case Some((roma,jap)) => roma
        case None => " "
      }
    ).foldLeft("")(_+_)
  }
  def roma_to_jap(str:String):String = {
    pat_roma.findAllIn(str).map(_.toString).map( x =>
      roma_to_jap.find({case (roma,jap) => roma == x }) match {
        case Some((roma,jap)) => jap
        case None => " "
      }
    ).foldLeft("")(_+_)
  }
  def is_japanese(context:Context):Boolean = {
    val loc = context.getResources.getConfiguration.locale
    loc.equals(Locale.JAPAN) || loc.equals(Locale.JAPANESE)
  }
}
