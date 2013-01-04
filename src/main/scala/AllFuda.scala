package karuta.hpnpwd.wasuramoti

import _root_.android.text.TextUtils
import _root_.java.util.regex.PatternSyntaxException
import scala.collection.mutable

object AllFuda{
  val list:Array[String] = Array(
    "あきの","はるす","あし","たご","おく",
    "かさ","あまの","わがい","はなの","これ",
    "わたのはらや","あまつ","つく","みち","きみがためは",
    "たち","ちは","す","なにわが","わび",
    "いまこ","ふ","つき","この","なにし",
    "おぐ","みかの","やまざ","こころあ","ありあ",
    "あさぼらけあ","やまが","ひさ","たれ","ひとは",
    "なつ","しら","わすら","あさじ","しの",
    "こい","ちぎりき","あい","おおこ","あわれ",
    "ゆら","やえ","かぜを","みかき","きみがためお",
    "かく","あけ","なげき","わすれ","たき",
    "あらざ","め","ありま","やす","おおえ",
    "いに","よを","いまは","あさぼらけう","うら",
    "もろ","はるの","こころに","あらし","さ",
    "ゆう","おと","たか","うか","ちぎりお",
    "わたのはらこ","せ","あわじ","あきか","ながか",
    "ほ","おも","よのなかよ","ながら","よも",
    "なげけ","む","なにわえ","たま","みせ",
    "きり","わがそ","よのなかは","みよ","おおけ",
    "はなさ","こぬ","かぜそ","ひとも","もも")

  val author = Array(
    "天智天皇","持統天皇","柿本人麻呂","山辺赤人","猿丸大夫",
    "中納言家持","安倍仲麿","喜撰法師","小野小町","蝉丸",
    "参議篁","僧正遍昭","陽成院","河原左大臣","光孝天皇",
    "中納言行平","在原業平朝臣","藤原敏行朝臣","伊勢","元良親王",
    "素性法師","文屋康秀","大江千里","菅家","三条右大臣",
    "貞信公","中納言兼輔","源宗于朝臣","凡河内躬恒","壬生忠岑",
    "坂上是則","春道列樹","紀友則","藤原興風","紀貫之",
    "清原深養父","文屋朝康","右近","参議等","平兼盛",
    "壬生忠見","清原元輔","権中納言敦忠","中納言朝忠","謙徳公",
    "曾禰好忠","恵慶法師","源重之","大中臣能宣朝臣","藤原義孝",
    "藤原実方朝臣","藤原道信朝臣","右大将道綱母","儀同三司母","大納言公任",
    "和泉式部","紫式部","大弐三位","赤染衛門","小式部内侍",
    "伊勢大輔","清少納言","左京大夫道雅","権中納言定頼","相模",
    "前大僧正行尊","周防内侍","三条院","能因法師","良暹法師",
    "大納言経信","祐子内親王家紀伊","前権中納言匡房","源俊頼朝臣","藤原基俊",
    "法性寺入道前関白太政大臣","崇徳院","源兼昌","左京大夫顕輔","待賢門院堀河",
    "後徳大寺左大臣","道因法師","皇太后宮大夫俊成","藤原清輔朝臣","俊恵法師",
    "西行法師","寂蓮法師","皇嘉門院別当","式子内親王","殷富門院大輔",
    "後京極摂政前太政大臣","二条院讃岐","鎌倉右大臣","参議雅経","前大僧正慈円",
    "入道前太政大臣","権中納言定家","従二位家隆","後鳥羽院","順徳院")

  val goshoku =List((R.string.fudaset_five_color_blue,List(3,5,6,12,14,24,30,31,50,57,61,62,69,70,74,75,76,82,91,100)),
                    (R.string.fudaset_five_color_pink,List(1,4,13,16,22,28,34,40,48,51,58,65,66,72,73,80,83,84,86,97)),
                    (R.string.fudaset_five_color_yellow,List(2,7,10,18,32,33,37,39,46,47,55,60,78,79,81,85,87,89,94,96)),
                    (R.string.fudaset_five_color_green,List(8,9,11,15,17,20,23,26,29,35,36,38,41,42,54,59,68,71,92,93)),
                    (R.string.fudaset_five_color_orange,List(19,21,25,27,43,44,45,49,52,53,56,63,64,67,77,88,90,95,98,99)))

  val musumefusahoseAll : String = "むすめふさほせうつしもゆいちひきはやよかみたこおわなあ"

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
  def makeKimarijiSetFromNumList(num_list:List[Int]):Option[(String,Int)] = {
    makeKimarijiSet(num_list.map(i => AllFuda.list(i-1))) 
  }
  def makeKimarijiSet(str_list:List[String]):Option[(String,Int)] = {
    val trie = CreateTrie.makeTrie(AllFuda.list)
    var st = mutable.Set[String]()
    for( m <- str_list ){
      st ++= trie.traversePrefix(m)
    }
    if(st.isEmpty){
      None
    }else{
      val excl = AllFuda.list.toSet -- st
      val kimari = trie.traverseWithout(excl.toSeq).toList
        .sortWith(AllFuda.compareMusumefusahose).mkString(" ")
      Some((kimari,st.size))
    }
  }
  def makeHaveToRead(str:String):Set[String] = {
    var ret = mutable.Set[String]()
    if(TextUtils.isEmpty(str)){
      return ret.toSet
    }
    val trie = CreateTrie.makeTrie(AllFuda.list)
    for( s <- str.split(" ") ){
      ret ++= trie.traversePrefix(s).toSet
    }
    return ret.toSet
  }

  def replaceFudaNumPattern(str:String):String = {
    val PATTERN_FUDANUM = """[0-9?*\-\[\]]+""".r
    val patterns = PATTERN_FUDANUM.findAllIn(str).flatMap({
        s => try{
          Some(s.replaceAllLiterally("?","\\d").replaceAllLiterally("*","\\d*").r)
        }catch{
          case e:PatternSyntaxException => None
        }
    }).toList
    val r = new StringBuilder(PATTERN_FUDANUM.replaceAllIn(str,""))
    for( i <- 0 until list.length){
      val s = (i+1).toString
      if(patterns.exists(_.pattern.matcher(s).matches)){
        r.append(" " + list(i))
      }
    }
    r.toString
  }
}
