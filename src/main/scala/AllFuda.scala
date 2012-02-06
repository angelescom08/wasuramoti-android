package karuta.hpnpwd.wasuramoti

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
    var st = Set[String]()
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
}
