import karuta.hpnpwd.wasuramoti.{Romanization,AllFuda,TrieUtils}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.Spec
import scala.collection.mutable.HashMap
// これはテスト
class Specs extends Spec with ShouldMatchers {
  describe("Romanization test") {
    it("jap to roma"){
      assert(Romanization.jap_to_roma("わすらもち 0123") == "wasuramoti 0123")
    }
    it("roma to jap"){
      assert(Romanization.roma_to_jap("wasuramoti 4567") == "わすらもち 4567")
    }
    it("zenkaku to hankaku"){
      assert(Romanization.zenkaku_to_hankaku("＊？［］１２３４５６７８９０") == "*?[]1234567890")
    }
    it("replace fuda num pattern"){
      var f = (ar:Seq[Int]) => " " + ar.sortWith(_<_).distinct.map(x => AllFuda.list(x-1)).mkString(" ")
      assert(AllFuda.replaceFudaNumPattern("あきの はるす ?") == "あきの はるす " + f(1 to 9))
      assert(AllFuda.replaceFudaNumPattern("？[13579]") == f(for(x <- 11 to 99 if x % 2 == 1)yield x))
      assert(AllFuda.replaceFudaNumPattern("1*") == f((Array(1,100)++(0 to 9).map(x=>x+10))))
      assert(AllFuda.replaceFudaNumPattern("[12]? ?[34]") == " " + f((0 to 9).flatMap(x=>Array(10+x,20+x)) ++ (1 to 9).flatMap(x=>Array(x*10+3,x*10+4))))
    }
  }
  describe("Karafuda test") {
    it("make karafuda"){
      val t = new HashMap[String,Int]()
      for(i <- 1 to 10000){
        val y = (TrieUtils.makeKarafuda(Set("あし","あきの","こころに","あさぼらけあ"),Set("ありま","あきか","こころあ","あさぼらけう","せ"),1))
        val x = y.head
        if(t.contains(x)){
          t(x) += 1
        }else{
          t(x) = 1
        }
      }
      println(t)

    }
  }
}
