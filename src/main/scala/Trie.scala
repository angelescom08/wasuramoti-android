package tami.pen.wasuramoti

class TrieVertex{
  var char:Char = '\0'
  var isLeaf:Boolean = false
  var flag:Boolean = false
  var childs:Seq[TrieVertex] = Seq()
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
    var r = Seq[String]()
    this.traverseAll( (v,s) => {
        val b = str.startsWith(s) || s.startsWith(str)
        if(b && v.isLeaf){
          r ++= Seq(s);
        }
        b
      }
    )
    return r
  }

  def traverseWithout(seq:Seq[String]):Seq[String] = {
    var r = Seq[String]()
    for(s <- seq){
      traverseSingle(s, _.flag = true)
    }
    this.traverseAll( (v,s) => {
      if(!v.flag){
        r ++= Seq(s)
      }
      v.flag}
    )
    for(s <- seq){
      traverseSingle(s, _.flag = false)
    }
    return r
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
          case None => val v = new TrieVertex{ char = c }; cur.childs ++= Seq(v); cur = v
        }
      }
      cur.isLeaf = true
    }
    return root
  }
}
