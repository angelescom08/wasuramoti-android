package karuta.hpnpwd.wasuramoti

import android.content.Context


object PrefKeyNumeric extends Enumeration {
  type PrefKeyNumeric = Value
  val DimlockMinutes = Value("dimlock_minutes")
  val WavBeginRead = Value("wav_begin_read")
  val WavEndRead = Value("wav_end_read")
  val WavFadeinKami = Value("wav_fadein_kami")
  val WavFadeoutSimo = Value("wav_fadeout_simo")
  val WavSpanSimokami = Value("wav_span_simokami")
  val WavThreshold = Value("wav_threshold")
}

object PrefManager {
  import PrefKeyNumeric._

  implicit val LongPrefAccept = new PrefAccept[Long] {
    def from(s: String) = s.toLong
    def defaultValue(context:Context, resId:Int):Long = {
      context.getResources.getInteger(resId)
    }
    def maxValue(conf:Conf[Long]):Long = {
      conf.maxValue
    }
  }
  implicit val DoublePrefAccept = new PrefAccept[Double] {
    def from(s: String) = s.toDouble
    def defaultValue(context:Context, resId:Int):Double = {
      Utils.getDimenFloat(context,resId)
    }
    def maxValue(conf:Conf[Double]):Double = {
      conf.maxValue
    }
  }

  case class Conf[T:PrefAccept](defResId:Int,maxValue:T)

  def getConf[T:PrefAccept](key:PrefKeyNumeric):Conf[T] = {
    val conv = implicitly[PrefAccept[T]].from _
    key match {
      case DimlockMinutes => Conf[T](R.integer.dimlock_minutes_default, conv("9999"))
      case WavBeginRead => Conf[T](R.dimen.wav_begin_read_default, conv("5.0"))
      case WavEndRead => Conf[T](R.dimen.wav_end_read_default, conv("5.0"))
      case WavFadeinKami => Conf[T](R.dimen.wav_fadein_kami_default, conv("2.0"))
      case WavFadeoutSimo => Conf[T](R.dimen.wav_fadeout_simo_default, conv("2.0"))
      case WavSpanSimokami => Conf[T](R.dimen.wav_span_simokami_default, conv("999.0"))
      case WavThreshold => Conf[T](R.dimen.wav_threshold_default, conv("0.3"))
    }
  }

  abstract class PrefAccept[T <% Ordered[T] ] {
    def from(s:String):T
    def defaultValue(context:Context,resId:Int):T
    def maxValue(conf:Conf[T]):T
    def >(a:T,b:T):Boolean = a > b
  }

  def getPrefAs[T:PrefAccept](key:PrefKeyNumeric,defValue:T,maxValue:T):T = {
    if(Globals.prefs.isEmpty){
      return defValue
    }
    val r = try{
      val v = Globals.prefs.get.getString(key.toString,defValue.toString)
      implicitly[PrefAccept[T]].from(v)
    }catch{
      case _:NumberFormatException => defValue
    }
    if( implicitly[PrefAccept[T]].>(r,maxValue)  ){
      maxValue
    }else{
      r
    }
  }

  def getPrefNumeric[T:PrefAccept](context:Context,key:PrefKeyNumeric):T = {
    val conf = getConf[T](key)
    val defValue = implicitly[PrefAccept[T]].defaultValue(context, conf.defResId)
    val maxValue = implicitly[PrefAccept[T]].maxValue(conf)
    getPrefAs[T](key, defValue, maxValue)
  }
}
