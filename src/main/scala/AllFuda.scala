package karuta.hpnpwd.wasuramoti

import _root_.java.util.regex.PatternSyntaxException
import _root_.android.content.Context
import _root_.android.text.TextUtils
import scala.collection.mutable

object AllFuda{
  var musumefusahoseAll = null:String
  var list = null:Array[String]
  def init(context:Context){
    musumefusahoseAll = context.getResources.getString(R.string.musumefusahose)
    list = context.getResources.getStringArray(R.array.poem_list)
  }

  var cache = new mutable.HashMap[Int,Array[String]]
  // TODO: do we really have to cache resource ? does'nt Android do the job ?
  def get(context:Context,id:Int):Array[String] ={
    cache.getOrElseUpdate(id,context.getResources.getStringArray(id))
  }

  val INSIDE_PARENS = """\((.*?)\)""".r
  def removeInsideParens(s:String):String = {
    INSIDE_PARENS.replaceAllIn(s,"")
  }
  def onlyInsideParens(s:String):String = {
    INSIDE_PARENS.findAllMatchIn(s).map{_.group(1)}.mkString("")
  }
  // We cannot use \p{IsHan} in Android DalvikVM
  val FURIGANA_PATTERN = """((\p{InCJKUnifiedIdeographs}|\p{InCJKSymbolsAndPunctuation})+)\((.*?)\)""".r
  def parseFurigana(str:String):Array[(String,String)] = {
    val r = new mutable.ArrayBuffer[(String,String)]()
    var prev = 0
    for(m <- FURIGANA_PATTERN.findAllMatchIn(str)){
      if(prev != m.start){
        r+=((str.substring(prev,m.start),""))
      }
      r+=((m.group(1),m.group(3)))
      prev = m.end
    }
    if(prev != str.length){
      r+=((str.substring(prev),""))
    }
    r.toArray
  }

  // romaji -> "Taki No Oto Wa Taete"
  // hiragana -> "たきのおとはたえて"
  // result -> Array((Taki,たき),(No,の),(Oto,おと),(Wa,は),(Taete,たえて))
  def splitToCorrespondingRomaji(romaji:String,hiragana:String):Array[(String,String)] = {
    var buf = hiragana
    val res = romaji.split("\\s+").map{ s =>
      val c = countRomaji(s)
      val h = takeIgnoringSmallLetters(buf,c)
      buf = buf.drop(h.length)
      (s,h)
    }
    if(!TextUtils.isEmpty(buf)){
      val newlast = (res.last._1, res.last._2+buf)
      res.update(res.length-1,newlast)
    }
    res
  }

  // Akinota -> 4, Chihayaburu -> 5, Tenchi -> 3
  def countRomaji(str:String):Int = {
    var buf = new mutable.Queue[Char]
    var count = 0
    str.toLowerCase.foreach{ c =>
      buf += c
      if("aiueo".contains(buf.last)){
        count += 1
        buf.clear
      }else if("\u016b\u014d".contains(buf.last)){
        // latin small letter {u,o} with macron
        count += 2
        buf.clear
      }else if(buf.head == 'n' && buf.length >= 2 && buf(1) != 'y'){
        count += 1
        buf = buf.tail
      }
    }
    if(!buf.isEmpty){
      count += 1
    }
    count
  }

  // str -> しょうちゅうこう
  // count -> 4
  // result -> しょうちゅう
  def takeIgnoringSmallLetters(str:String, count:Int):String = {
    var i = 0
    str.takeWhile{ c =>
      // hiragana letter small {tu,ya,yu,yo}
      if(!"\u3063\u3083\u3085\u3087".contains(c)){
        i += 1
      }
      i <= count
    }
  }

  def getOnlyHiragana(str:String):String = {
    parseFurigana(str).map{ case (s,t) =>
      if(TextUtils.isEmpty(t)){
        s
      }else{
        t
      }
    }.mkString("")
  }

  val goshoku =Seq((R.string.fudaset_five_color_blue,Seq(3,5,6,12,14,24,30,31,50,57,61,62,69,70,74,75,76,82,91,100)),
                    (R.string.fudaset_five_color_pink,Seq(1,4,13,16,22,28,34,40,48,51,58,65,66,72,73,80,83,84,86,97)),
                    (R.string.fudaset_five_color_yellow,Seq(2,7,10,18,32,33,37,39,46,47,55,60,78,79,81,85,87,89,94,96)),
                    (R.string.fudaset_five_color_green,Seq(8,9,11,15,17,20,23,26,29,35,36,38,41,42,54,59,68,71,92,93)),
                    (R.string.fudaset_five_color_orange,Seq(19,21,25,27,43,44,45,49,52,53,56,63,64,67,77,88,90,95,98,99)))

  def compareMusumefusahose(x:String,y:String):Boolean = {
    val x1 = musumefusahoseAll.indexOf(x(0))
    val y1 = musumefusahoseAll.indexOf(y(0))
    if( x1 == y1 ){
      return x.compare(y) < 0
    }else{
      return x1.compare(y1) < 0
    }
  }
  def getFudaNum(s:String):Int = {
    val r = list.indexOf(s)
    if( r < 0){
      return -1
    }else{
      return r+1
    }
  }

  def replaceFudaNumPattern(str:String):String = {
    val PATTERN_FUDANUM = """[0-9?*\[\]]+""".r
    val buf = Romanization.zenkaku_to_hankaku(str)
    val patterns = PATTERN_FUDANUM.findAllIn(buf).flatMap({
        s => try{
          Some(s.replaceAllLiterally("?","\\d").replaceAllLiterally("*","\\d*").r)
        }catch{
          case _:PatternSyntaxException => None
        }
    }).toList
    val r = new StringBuilder(PATTERN_FUDANUM.replaceAllIn(buf,""))
    for( i <- 0 until list.length){
      val s = (i+1).toString
      if(patterns.exists(_.pattern.matcher(s).matches)){
        r.append(" " + list(i))
      }
    }
    r.toString
  }
}
