package karuta.hpnpwd.wasuramoti

import android.content.Context
import android.widget.EditText
import android.util.AttributeSet

import com.wanakanajava.WanaKanaJava

class LocalizationEditText(context:Context,attrs:AttributeSet) extends EditText(context,attrs){
  def getLocalizationText():String = {
    var text = super.getText().toString()
    if(!Romanization.isJapanese(context)){
      text = Romanization.romaToJap(text)
    }
    return text
  }
  def setLocalizationText(text:String){
    setText(Romanization.japToLocal(context,text))
  }
}

object Romanization{
  lazy val WANA_KANA = new WanaKanaJava(true)
  lazy val MAP_ZENKAKU_TO_HANKAKU = Map(
    "．"->".", "＊"->"*", "？"->"?", "［"->"[", "］"->"]",
    "０"->"0", "１"->"1", "２"->"2", "３"->"3", "４"->"4",
    "５"->"5", "６"->"6", "７"->"7", "８"->"8", "９"->"9")

  def japToRoma(str:String):String = {
    WANA_KANA.toRomaji(str)
  }
  def romaToJap(str:String):String = {
    WANA_KANA.toKana(str)
  }

  def zenkakuToHankaku(str:String) = {
    str.toCharArray.map(_.toString).map(
      x => MAP_ZENKAKU_TO_HANKAKU.getOrElse(x,x)
    ).mkString
  }

  def isJapanese(context:Context):Boolean = {
    context.getResources.getString(R.string.locale) == "ja"
  }
  def japToLocal(context:Context,text:String):String = if (isJapanese(context)){ text } else {japToRoma(text)}
}
