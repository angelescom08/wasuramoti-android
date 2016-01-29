package karuta.hpnpwd.wasuramoti

import _root_.android.content.Context
import _root_.android.widget.EditText
import _root_.android.util.AttributeSet
import _root_.java.util.Locale

import com.wanakanajava.WanaKanaJava

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
  lazy val WANA_KANA = new WanaKanaJava(true)
  lazy val MAP_ZENKAKU_TO_HANKAKU = Map(
    "．"->".", "＊"->"*", "？"->"?", "［"->"[", "］"->"]",
    "０"->"0", "１"->"1", "２"->"2", "３"->"3", "４"->"4",
    "５"->"5", "６"->"6", "７"->"7", "８"->"8", "９"->"9")

  def jap_to_roma(str:String):String = {
    WANA_KANA.toRomaji(str)
  }
  def roma_to_jap(str:String):String = {
    WANA_KANA.toKana(str)
  }

  def zenkaku_to_hankaku(str:String) = {
    str.toCharArray.map(_.toString).map(
      x => MAP_ZENKAKU_TO_HANKAKU.getOrElse(x,x)
    ).mkString
  }

  def is_japanese(context:Context):Boolean = {
    val loc = context.getResources.getConfiguration.locale
    loc.equals(Locale.JAPAN) || loc.equals(Locale.JAPANESE)
  }
  def jap_to_local(context:Context,text:String):String = if (is_japanese(context)){ text } else {jap_to_roma(text)}
}
