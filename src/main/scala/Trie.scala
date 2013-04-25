package karuta.hpnpwd.wasuramoti
import _root_.android.text.TextUtils
import scala.collection.mutable
import scala.collection.mutable.Buffer

class TrieVertex{
  var char:Char = '\0'
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
}
