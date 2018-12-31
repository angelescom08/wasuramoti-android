package karuta.hpnpwd.wasuramoti

import android.content.Context


object PrefKeyNumeric extends Enumeration {
  type PrefKeyNumeric = Value
  // EditTextPreferenceCustom persists value as String, however, we have to treat as Numeric when getting it from preference.
  //   https://github.com/aosp-mirror/platform_frameworks_base/blob/99f6957f2e42caeea209d2069174cab24c347a95/core/java/android/preference/EditTextPreference.java#L96
  // TODO: do not use EditTextPreferenceCustom and store as appropriate type. (e.g. SharedProference.Editor#putFloat)
  val DimlockMinutes = Value("dimlock_minutes")
  val WavBeginRead = Value("wav_begin_read")
  val WavEndRead = Value("wav_end_read")
  val WavFadeinKami = Value("wav_fadein_kami")
  val WavFadeoutSimo = Value("wav_fadeout_simo")
  val WavSpanSimokami = Value("wav_span_simokami")
  val WavThreshold = Value("wav_threshold")
}

object PrefKeyBool extends Enumeration {
  type PrefKeyBool = PrefKeyBoolVal
  val VolumeAlert = PrefKeyBoolVal("volume_alert", R.bool.volume_alert_default)
  val RingerModeAlert = PrefKeyBoolVal("ringer_mode_alert", R.bool.ringer_mode_alert_default)
  val UseAudioFocus = PrefKeyBoolVal("use_audio_focus", R.bool.use_audio_focus_default)
  val ShowMessageWhenMoved = PrefKeyBoolVal("show_message_when_moved", R.bool.show_message_when_moved_default)
  val ShowCurrentIndex = PrefKeyBoolVal("show_current_index", R.bool.show_current_index_default)
  val PlayAfterSwipe = PrefKeyBoolVal("play_after_swipe", R.bool.play_after_swipe_default)
  val UseOpenSles = PrefKeyBoolVal("use_opensles", R.bool.use_opensles_default)
  protected case class PrefKeyBoolVal(key:String, defaultResId:Int) extends super.Val()
  implicit def convert(value: Value) = value.asInstanceOf[PrefKeyBoolVal]
}

object PrefKeyStr extends Enumeration {
  type PrefKeyStr = PrefKeyStrVal
  val AudioStreamType = PrefKeyStrVal("audio_stream_type", R.string.audio_stream_type_default)
  protected case class PrefKeyStrVal(key:String, defaultResId:Int) extends super.Val()
  implicit def convert(value: Value) = value.asInstanceOf[PrefKeyStrVal]
}

object PrefManager {
  import PrefKeyNumeric._
  import PrefKeyBool._
  import PrefKeyStr._

  implicit val LongPrefAccept = new PrefAccept[Long] {
    def from(s: String) = s.toLong
    def defaultValue(context:Context, resId:Int):Long = {
      from(context.getResources.getString(resId))
    }
    def maxValue(conf:Conf[Long]):Long = {
      conf.maxValue
    }
  }
  implicit val DoublePrefAccept = new PrefAccept[Double] {
    def from(s: String) = s.toDouble
    def defaultValue(context:Context, resId:Int):Double = {
      from(context.getResources.getString(resId))
    }
    def maxValue(conf:Conf[Double]):Double = {
      conf.maxValue
    }
  }

  case class Conf[T:PrefAccept](defResId:Int,maxValue:T)

  def getConf[T:PrefAccept](key:PrefKeyNumeric):Conf[T] = {
    val conv = implicitly[PrefAccept[T]].from _
    key match {
      case DimlockMinutes => Conf[T](R.string.dimlock_minutes_default, conv("9999"))
      case WavBeginRead => Conf[T](R.string.wav_begin_read_default, conv("5.0"))
      case WavEndRead => Conf[T](R.string.wav_end_read_default, conv("5.0"))
      case WavFadeinKami => Conf[T](R.string.wav_fadein_kami_default, conv("2.0"))
      case WavFadeoutSimo => Conf[T](R.string.wav_fadeout_simo_default, conv("2.0"))
      case WavSpanSimokami => Conf[T](R.string.wav_span_simokami_default, conv("999.0"))
      case WavThreshold => Conf[T](R.string.wav_threshold_default, conv("0.3"))
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
  def getPrefDefault[T:PrefAccept](context:Context,key:PrefKeyNumeric):T = {
    val conf = getConf[T](key)
    implicitly[PrefAccept[T]].defaultValue(context, conf.defResId)
  }

  def getPrefBool(context:Context,key:PrefKeyBool):Boolean = {
    val defValue = context.getResources.getBoolean(key.defaultResId)
    Globals.prefs.get.getBoolean(key.key, defValue)
  }
  def getPrefStr(context:Context,key:PrefKeyStr):String = {
    val defValue = context.getResources.getString(key.defaultResId)
    Globals.prefs.get.getString(key.key, defValue)
  }
}
