package karuta.hpnpwd.wasuramoti

import karuta.hpnpwd.audio.OggVorbisDecoder
import android.media.AudioTrack
import android.content.Context

import java.nio.{ByteOrder,ShortBuffer,ByteBuffer}

import scala.collection.mutable

object AudioHelper{
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]
  val SHORT_SIZE = java.lang.Short.SIZE/java.lang.Byte.SIZE

  def genReadNumKamiSimoPairs(context:Context,cur_num:Int,next_num:Int):Seq[(Int,Int,Boolean)] = {
    val read_order_each = Globals.prefs.get.getString("read_order_each","CUR2_NEXT1")
    var ss = read_order_each.split("_")
    if(cur_num == 0){
      val (up,lo) = if(Globals.prefs.get.getBoolean("joka_enable",true)){
        Utils.parseReadOrderJoka
      }else{
        (0,0)
      }
      ss = Array.fill(up){"CUR1"} ++ Array.fill(lo){"CUR2"} ++ ss.filter(_.startsWith("NEXT"))
    }
    val is_last_fuda = FudaListHelper.isLastFuda(context)
    if(is_last_fuda && read_order_each.endsWith("NEXT1")){
      ss ++= Array("NEXT2")
    }
    ss.map{ s =>
      val read_num = if(s.startsWith("CUR")){
        cur_num
      }else{
        next_num
      }
      val kami_simo = if(s.endsWith("1")){
        1
      }else{
        2
      }
      val is_cur = s.startsWith("CUR")
      (read_num,kami_simo,is_cur)
    }
  }

  def calcTotalMillisec(audio_queue:AudioQueue):Long = {
    audio_queue.map{ arg =>{
      arg match {
        case Left(w) => w.audioLength()
        case Right(millisec) => millisec
        }
      }
    }.sum
  }

  def calcBufferSize(decoder:OggVorbisDecoder,audio_queue:AudioQueue):Int = {
      audio_queue.map{ arg =>{
          arg match {
            case Left(w) => w.bufferSizeInBytes()
            case Right(millisec) => millisecToBufferSizeInBytes(decoder,millisec)
          }
        }
      }.sum
  }

  def makeBuffer(decoder:OggVorbisDecoder,audio_queue:AudioQueue):Array[Short] = {
      val buf = new Array[Short](calcBufferSize(decoder,audio_queue)/SHORT_SIZE)
      var offset = 0
      audio_queue.foreach{ arg => {
          arg match {
            case Left(w) => offset += w.writeToShortBuffer(buf,offset)
            case Right(millisec) => offset += millisecToBufferSizeInBytes(decoder,millisec) / SHORT_SIZE
            }
        }
      }
      buf
  }

  def millisecToBufferSizeInBytes(decoder:OggVorbisDecoder,millisec:Int):Int = {
    // must be multiple of channels * sizeof(Short)
    (millisec * decoder.rate.toInt / 1000) * decoder.channels * SHORT_SIZE
  }

  def pickLastPhrase(aq:AudioQueue):Option[AudioQueue] = {
    if(Globals.prefs.get.getBoolean("show_replay_last_button",false)){
      val l = aq.headOption.filter(_.isRight)
      val m = aq.reverse.find(_.isLeft)
      val r = aq.lastOption.filter(_.isRight)
      val ar = mutable.Queue(l,m,r).flatten
      if(ar.nonEmpty){
        Some(ar)
      }else{
        None
      }
    }else{
      None
    }
  }

  def refreshKarutaPlayer(activity:WasuramotiActivity,old_player:Option[KarutaPlayer],force:Boolean, fromAuto:Boolean = false, nextRandom:Option[Int] = None):Option[KarutaPlayer] = {
    Globals.player_none_reason = None
    val app_context = activity.getApplicationContext()
    val maybe_reader = ReaderList.makeCurrentReader(app_context)
    if(maybe_reader.isEmpty){
      Globals.player_none_reason = Some(app_context.getResources.getString(R.string.player_none_reason_no_reader))
      return None
    }
    val current_index = FudaListHelper.getCurrentIndex(app_context)
    val num = if(Utils.isRandom){
      val cur_num = 
        nextRandom
        .orElse(old_player.map{_.next_num})
        .orElse(activity.getCurNumInView)
        .orElse(FudaListHelper.queryRandom)
        .getOrElse(0)
      val next_num = FudaListHelper.queryRandom
      next_num.map{
       (cur_num,_)
      }
    }else{
      FudaListHelper.queryNext(current_index).map{
        x=>(x.cur.num,x.next.num)
      }
    }
    num.flatMap{case(cur_num,next_num) =>{
        val num_changed = old_player.forall{ x => (x.cur_num, x.next_num) != ((cur_num, next_num)) }
        val (readable,reason) = maybe_reader.get.bothReadable(cur_num,next_num)
        if(!readable){
          Globals.player_none_reason = Some(reason)
          None
        }else if(force || Globals.forceRefreshPlayer || num_changed){
          Globals.forceRefreshPlayer = false

          old_player.foreach{ p=>
            // mayInterruptIfRunning must be true to set Thread.currentThread.isInterrupted() == true for AsyncTask's thread
            p.decode_task.cancel(true)
            // TODO: do we have to wait for decode to finish since it is jni ?
            p.stop(fromAuto)
          }
          Some(new KarutaPlayer(activity,maybe_reader,cur_num,next_num))
        }else{
          old_player
        }
      }
    }
  }
  def writeSilence(track:AudioTrack,millisec:Int){
    val buf = new Array[Short](track.getSampleRate()*millisec/1000)
    track.write(buf,0,buf.length)
  }
}

class WavBuffer(val buffer:ShortBuffer, val decoder:OggVorbisDecoder, val num:Int, val kamisimo:Int) extends WavBufferDebugTrait with BugReportable{
  val MAX_AMP = (1 << (decoder.bit_depth-1))
  var index_begin = 0
  var index_end = decoder.data_length

  override def toBugReport():String = {
    val bld = new mutable.StringBuilder
    bld ++= s"num->$num;"
    bld ++= s"kamisimo->$kamisimo;"
    bld ++= s"index_begin->$index_begin;"
    bld ++= s"index_end->$index_end;"
    val crc = shortBufferCRC(buffer)
    bld ++= s"buffer->capacity[${buffer.capacity}].crc[${crc}];"
    bld ++= s"channels->${decoder.channels};"
    bld ++= s"rate->${decoder.rate};"
    bld ++= s"bit_depth->${decoder.bit_depth};"
    bld ++= s"data_length->${decoder.data_length};"
    bld.toString
  }

  def shortBufferCRC(buffer:ShortBuffer):String = {
    val crc = new java.util.zip.CRC32()
    while(buffer.hasRemaining){
      val ss = new Array[Short](Math.min(1024,buffer.remaining))
      val bb = new Array[Byte](ss.size*2)
      buffer.get(ss)
      ByteBuffer.wrap(bb).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer.put(ss)
      crc.update(bb)
    }
    f"${crc.getValue}%08X"
  }

  // in milliseconds
  def audioLength():Long = {
    ((1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong)/decoder.channels
  }
  def bufferSizeInBytes():Int = {
    AudioHelper.SHORT_SIZE * (index_end - index_begin)
  }

  def boundIndex(x:Int) = {
    math.min(math.max(0,x),buffer.limit)
  }

  def writeToAudioTrack(track:AudioTrack){
    // Since using ShortBuffer.array() throws UnsupportedOperationException (maybe because we are using FileChannel.map() ?),
    // we use ShortBuffer.get() instead.

    // Some device raises java.lang.IllegalArgumentException in java.nio.Buffer.position().
    // The Exception should not be raised if there's no bug in previous procedure such as fade().
    // However I could not find the bug. Therefore I prevent it by checking the index bound here.
    // TODO: fix the bug in fade() or some other function which causes IllegalArgumentException here.
    val ib = boundIndex(index_begin)
    val ie = boundIndex(index_end)

    val b_size = ie - ib
    val b = new Array[Short](b_size)

    buffer.position(ib)
    buffer.get(b,0,b_size)
    buffer.rewind()
    track.write(b,0,b_size)
  }
  def writeToShortBuffer(dst:Array[Short], offset:Int):Int = {
    // See writeToAudioTrack() why we apply boundIndex()
    val ib = boundIndex(index_begin)
    val ie = boundIndex(index_end)
    val b_base = ie - ib
    val b_size = math.min( b_base, dst.length-offset )
    buffer.position(ib)
    buffer.get(dst, offset, b_size)
    buffer.rewind()
    // TODO: which should we return: b_base or b_size ?
    return(b_size)
  }
  def thresholdIndex(threshold:Double, fromEnd:Boolean):Int = {
    val threshold_amp = (MAX_AMP *
      (if(threshold > 0.0001){ threshold }else{ 0 })
     ).toShort
    var (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    bg = indexInBuffer(bg)
    ed = indexInBuffer(ed)
    try{
      for( i <- bg to (ed,step) ){
        // TODO: check by block of signal instead of single signal
        if( math.abs(buffer.get(i)) > threshold_amp ){
          return i
        }
      }
    }catch{
      // These exceptions should not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case _:IndexOutOfBoundsException => return(bg)
    }
    return(ed)
  }

  def indexInBuffer(i:Int):Int = {
    if(i < 0){
      return 0
    }
    if(i >= buffer.limit()){
      return (buffer.limit() - 1)
    }
    return i
  }

  // index must be multiple of decoder.channels
  def fitIndex(i:Int):Int = {
    (i / decoder.channels) * decoder.channels
  }

  // if begin < end then fade-in else fade-out
  // center_amp must be between 0.0 and 1.0
  // TODO: strictly speaking, this fade does not fade stereo wav correctly since left and right amplitudes are amplified differently.
  def fade(i_begin:Int, i_end:Int, center_amp:Double = 0.5){
    val begin = indexInBuffer(i_begin)
    val end = indexInBuffer(i_end)
    val width = math.abs(begin - end)
    if(width < secToIndex(0.01) ){
      return
    }
    val step = if( begin < end ){ 1 } else { -1 }
    try{
      for( i <- begin to (end,step) ){
        val x = (math.abs(i - begin).toDouble)/width.toDouble
        val amp = if(x <= 0.5){
          center_amp * x * 2.0
        }else{
          (1.0 - center_amp) * 2.0 * x + (2.0 * center_amp - 1.0)
        }
        buffer.put(i,(buffer.get(i)*amp).toShort)
      }
    }catch{
      // These exceptions should not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case _:IndexOutOfBoundsException => None
    }
  }

  def secToIndex(sec:Double):Int = {
    (sec * decoder.rate).toInt * decoder.channels
  }

  // fadein
  def trimFadeIn(context:Context){
    val threshold = PrefManager.getPrefNumeric[Double](context, PrefKeyNumeric.WavThreshold)
    val fadelen = secToIndex(PrefManager.getPrefNumeric[Double](context,PrefKeyNumeric.WavFadeinKami))
    val beg = fitIndex(thresholdIndex(threshold,false))
    val fadebegin = Math.max( beg - fadelen, 0)
    // Log.v("wasuramoti_fadein", s"num=${num}, kamisimo=${kamisimo}, fadebegin=${fadebegin}, beg=${beg}, len=${beg-fadebegin}")
    fade(fadebegin, beg, 0.75)
    index_begin = fitIndex(fadebegin)
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_begin >= index_end){
      index_begin = index_end - decoder.channels
    }
  }
  // fadeout
  def trimFadeOut(context:Context){
    val threshold = PrefManager.getPrefNumeric[Double](context,PrefKeyNumeric.WavThreshold)
    val fadelen = secToIndex(PrefManager.getPrefNumeric[Double](context, PrefKeyNumeric.WavFadeoutSimo))
    val end = fitIndex(thresholdIndex(threshold,true))
    val fadeend = Math.max( end - fadelen, 0)
    // Log.v("wasuramoti_fadeout", s"num=${num}, kamisimo=${kamisimo}, fadeend=${decoder.data_length-fadeend}, end=${decoder.data_length-end}, len=${end-fadeend}")
    fade(end,fadeend)
    index_end = fitIndex(end)
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_end <= index_begin){
      index_end = index_begin + decoder.channels
    }
  }
}

trait WavBufferDebugTrait{
  self:WavBuffer =>
  def checkSum():String = {
    if(Globals.IS_DEBUG){
      var (bg,ed) = (index_begin,index_end-1)
      bg = indexInBuffer(bg)
      ed = indexInBuffer(ed)
      // sampling in every 128 elems
      val s = (for( i <- bg to (ed,128) )yield buffer.get(i)).sum
      audioLength().toString + ":" + ("%04X".format(s))
    }else{
      ""
    }
  }
}
