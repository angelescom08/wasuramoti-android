package karuta.hpnpwd.wasuramoti

import _root_.karuta.hpnpwd.audio.OggVorbisDecoder
import _root_.android.media.AudioTrack
import _root_.java.io.{File,RandomAccessFile}
import _root_.java.nio.{ByteOrder,ShortBuffer}
import _root_.java.nio.channels.FileChannel

import scala.collection.mutable

object AudioHelper{
  type AudioQueue = mutable.Queue[Either[WavBuffer,Int]]
  def calcTotalMillisec(audio_queue:AudioQueue):Long = {
    audio_queue.map{ arg =>{
      arg match {
        case Left(w) => w.audioLength()
        case Right(millisec) => millisec
        }
      }
    }.sum
  }

  def millisecToBufferSizeInBytes(decoder:OggVorbisDecoder,millisec:Int):Int = {
    ((java.lang.Short.SIZE/java.lang.Byte.SIZE) *millisec * decoder.rate.toInt / 1000)*decoder.channels
  }
  def refreshKarutaPlayer(activity:WasuramotiActivity,old_player:Option[KarutaPlayer],force:Boolean):Option[KarutaPlayer] = {
    val app_context = activity.getApplicationContext()
    val maybe_reader = ReaderList.makeCurrentReader(app_context)
    if(maybe_reader.isEmpty){
      return None
    }
    val current_index = FudaListHelper.getCurrentIndex(app_context)
    val num = if("RANDOM" == Globals.prefs.get.getString("read_order",null)){
      val cur_num = Globals.player match {
        case Some(player) => player.next_num
        case None => 0
      }
      val next_num = FudaListHelper.queryRandom(app_context)
      Some(cur_num,next_num)
    }else{
      FudaListHelper.queryNext(app_context,current_index).map{
        case (cur_num,next_num,_,_) => (cur_num,next_num)
      }
    }
    num.foreach{ case (cur_num,next_num) =>
      if(Globals.play_log.isEmpty){
        Globals.play_log.++=:(Array(next_num,cur_num))
      }else{
        Globals.play_log.+=:(next_num)
      }
      if(Globals.play_log.length > 10){
        Globals.play_log.trimEnd(Globals.play_log.length-10)
      }
    }
    num.flatMap{case(cur_num,next_num) =>{
        if(!maybe_reader.get.bothExists(cur_num,next_num)){
          None
        }else if(force || Globals.forceRefresh || old_player.isEmpty ||
        old_player.get.cur_num != cur_num || old_player.get.next_num != next_num
        ){
          Globals.forceRefresh = false
          Some(new KarutaPlayer(activity,maybe_reader.get,cur_num,next_num))
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
  // note: modifying the follwing ShortBuffer is reflected to tho original file because it is casted from MappedByteBuffer
  def withMappedShortsFromFile(f:File,func:ShortBuffer=>Unit){
    val raf = new RandomAccessFile(f,"rw")
    func(raf.getChannel().map(FileChannel.MapMode.READ_WRITE,0,f.length()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer())
    raf.close()
  }
}

class WavBuffer(val buffer:ShortBuffer, val orig_file:File, val decoder:OggVorbisDecoder) extends WavBufferDebugTrait{
  val SHORT_BYTE = java.lang.Short.SIZE/java.lang.Byte.SIZE
  val MAX_AMP = (1 << (decoder.bit_depth-1)).toDouble
  var index_begin = 0
  var index_end = orig_file.length().toInt / SHORT_BYTE

  // in milliseconds
  def audioLength():Long = {
    ((1000.0 * ((index_end - index_begin).toDouble / decoder.rate.toDouble)).toLong)/decoder.channels
  }
  def bufferSizeInBytes():Int = {
    SHORT_BYTE * (index_end - index_begin)
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
  def threasholdIndex(threashold:Double, fromEnd:Boolean):Int = {
    var (bg,ed,step) = if(fromEnd){
      (index_end-1,index_begin,-1)
    }else{
      (index_begin,index_end-1,1)
    }
    bg = indexInBuffer(bg)
    ed = indexInBuffer(ed)
    try{
      for( i <- bg to (ed,step) ){
        if( scala.math.abs(buffer.get(i)) / MAX_AMP > threashold ){
          return i
        }
      }
    }catch{
      // These exceptions shold not happen since indexInBuffer() sets proper begin, end.
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

  // if begin < end then fade-in else fade-out
  def fade(i_begin:Int, i_end:Int){
    val begin = indexInBuffer(i_begin)
    val end = indexInBuffer(i_end)
    if(begin == end){
      return
    }
    val width = scala.math.abs(begin - end).toDouble
    val step = if( begin < end ){ 1 } else { -1 }
    try{
      for( i <- begin to (end,step) ){
        val len = scala.math.abs(i - begin).toDouble
        val amp = len / width
        buffer.put(i,(buffer.get(i)*amp).toShort)
      }
    }catch{
      // These exceptions shold not happen since indexInBuffer() sets proper begin, end.
      // Therefore these catches are just for sure.
      case _:IndexOutOfBoundsException => None
    }
  }
  // fadein
  def trimFadeIn(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold", 0.01, 1.0)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadein_kami", 0.1, 9999.0) * decoder.rate * decoder.channels ).toInt
    val beg = threasholdIndex(threashold,false)
    val fadebegin = if ( beg - fadelen < 0 ) { 0 } else { beg - fadelen }
    fade(fadebegin,beg)
    index_begin = ( fadebegin / decoder.channels ) * decoder.channels
    //TODO: more strict way to ensure 0 <= index_begin < index_end <= buffer_size
    if(index_begin >= index_end){
      index_begin = index_end - decoder.channels
    }
  }
  // fadeout
  def trimFadeOut(){
    val threashold = Utils.getPrefAs[Double]("wav_threashold", 0.01, 1.0)
    val fadelen = (Utils.getPrefAs[Double]("wav_fadeout_simo", 0.2, 9999.0) * decoder.rate * decoder.channels).toInt
    val end = threasholdIndex(threashold,true)
    val fadeend = if ( end - fadelen < 0) { 0 } else { end - fadelen }
    fade(end,fadeend)
    index_end = ( end / decoder.channels ) * decoder.channels
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
