package karuta.hpnpwd.wasuramoti
import android.text.TextUtils
import scala.collection.mutable
import scala.collection.mutable.Buffer

class TrieVertex{
  var char:Char = '\u0000'
  var isLeaf:Boolean = false
  var flag:Boolean = false
  var childs:Buffer[TrieVertex] = Buffer()
  def traverseSingle(str:String, func: (TrieVertex) => Unit ){
      var cur = this
      func(cur)
      for( c <- str ){
        cur.childs.find(_.char == c) match{
          case Some(x) => cur = x
          case None => return
        }
        func(cur)
      }
  }

  def traverseAll(func: (TrieVertex,String) => Boolean ){
      def tra(cur:TrieVertex,str:String){
          if(! func(cur,str) ){
            return
          }
          for( v <- cur.childs ){
            tra(v,str+v.char)
          }
      }
      tra(this,"")
  }

  def traversePrefix(str:String):Seq[String] = {
    val r = Buffer[String]()
    if(TextUtils.isEmpty(str)){
      return r
    }
    this.traverseAll( (v,s) => {
        val b = str.startsWith(s) || s.startsWith(str)
        if(b && v.isLeaf){
          r += s;
        }
        b
      }
    )
    return r.toSeq
  }

  def traverseWithout(seq:Seq[String]):Seq[String] = {
    val r = Buffer[String]()
    // preprocess
    this.flag = true
    for(s <- seq){
      traverseSingle(s, _.flag = true)
    }
    // traverse
    this.traverseAll( (v,s) => {
      if(!v.flag){
        r += s
      }
      v.flag}
    )
    // postprocess
    for(s <- seq){
      traverseSingle(s, _.flag = false)
    }
    this.flag = false
    return r.toSeq
  }

}

object CreateTrie{
  def makeTrie(seq:Seq[String]):TrieVertex = {
    val root = new TrieVertex
    for( s <- seq ){
      var cur = root
      for( c <- s ){
        cur.childs.find(_.char == c) match{
          case Some(x) => cur = x
          case None => val v = new TrieVertex{ char = c }; cur.childs += v; cur = v
        }
      }
      cur.isLeaf = true
    }
    return root
  }
}

object TrieUtils{
  def makeKimarijiSetFromNumList(num_list:Seq[Int]):Option[(String,Int)] = {
    makeKimarijiSet(num_list.map(i => AllFuda.list(i-1)))
  }
  def makeKimarijiSet(str_list:Seq[String]):Option[(String,Int)] = {
    val trie = CreateTrie.makeTrie(AllFuda.list)
    val st = mutable.Set[String]()
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
  def makeNumListFromKimariji(str:String):Set[Int] = {
    val trie = CreateTrie.makeTrie(AllFuda.list.filter{x=>x.head == str.head})
    trie.traversePrefix(str).map{AllFuda.getFudaNum(_)}.toSet
  }
  def makeHaveToRead(str:String):Set[String] = {
    val ret = mutable.Set[String]()
    if(TextUtils.isEmpty(str)){
      return ret.toSet
    }
    val trie = CreateTrie.makeTrie(AllFuda.list)
    for( s <- str.split(" ") ){
      ret ++= trie.traversePrefix(s).toSet
    }
    return ret.toSet
  }
  def makeWeightedKarafuda(fudaset:Set[String],candidate:Set[String]):Seq[(Float,String)] = {
    val bias = 1.618034f
    val trie = CreateTrie.makeTrie(fudaset.toSeq)
    val ura_prob = Globals.prefs.get.getFloat("karafuda_urafuda_prob",0.5f)
    candidate.map{ s =>
      var count = -1
      trie.traverseSingle(s, {_ => count += 1 })
      val w = math.max(0.0f, bias + (count.toFloat-bias) * ura_prob )
      (w,s)
    }.toSeq.sortBy(_._1).reverse // sorting in a descending order makes it faster when choosing random with weight.
  }
  def makeKarafuda(fudaset:Set[String],candidate:Set[String], num:Int):Set[String] = {
    val EPSILON = 0.00000001
    val wlist = makeWeightedKarafuda(fudaset,candidate).toBuffer
    var weight_sum = wlist.map(_._1).sum
    ( 0 until math.min(num,candidate.size) ).map{ i =>
      if(weight_sum > EPSILON){
        val r = Globals.rand.nextDouble * weight_sum
        var ws = 0.0f
        val index = math.max(0,wlist.toStream.indexWhere{case(w,s)=>{ws+=w;ws>=r}})
        val (w,s) = wlist(index)
        weight_sum -= w
        wlist.remove(index)
        s
      }else{
        //choose randomly
        val index = Globals.rand.nextInt(wlist.length)
        val (_,s) = wlist(index)
        wlist.remove(index)
        s
      }
    }.toSet
  }
  def calcKimariji(notyetread:Set[String],target:String):String = {
    val trie = CreateTrie.makeTrie(notyetread.toSeq.filter{x=> x != target && x.head == target.head})
    var count = 0
    trie.traverseSingle(target, {_ => count += 1})
    target.substring(0,count)
  }
}
