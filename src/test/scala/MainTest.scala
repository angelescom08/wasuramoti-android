package karuta.hpnpwd.wasuramoti

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.{Robolectric, RobolectricTestRunner, RuntimeEnvironment}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite

@RunWith(classOf[RobolectricTestRunner])
@Config(
  manifest = "src/main/AndroidManifest.xml",
  resourceDir = "../../target/android/intermediates/res",
  sdk = Array(24)
)
class MainTest extends JUnitSuite with Matchers {
  //@Test
  //def testExample(){
  //  val mainActivity = Robolectric.setupActivity(classOf[WasuramotiActivity])
  //}

  @Test
  def testStringConvert(){
    Romanization.jap_to_roma("わすらもち 0123") shouldBe "wasuramochi 0123"
    Romanization.roma_to_jap("wasuramoti 4567") shouldBe "わすらもち 4567"
    Romanization.zenkaku_to_hankaku("＊？［］１２３４５６７８９０") shouldBe "*?[]1234567890"
  }

  @Test
  def testReplaceFudaNum(){
      val context = RuntimeEnvironment.application.getApplicationContext
      AllFuda.init(context)
      var f = (ar:Seq[Int]) => " " + ar.sortWith(_<_).distinct.map(x => AllFuda.list(x-1)).mkString(" ")
      AllFuda.replaceFudaNumPattern("あきの はるす ?") shouldBe "あきの はるす " + f(1 to 9)
      AllFuda.replaceFudaNumPattern("？[13579]") shouldBe f(for(x <- 11 to 99 if x % 2 == 1)yield x)
      AllFuda.replaceFudaNumPattern("1*") shouldBe f((Array(1,100)++(0 to 9).map(x=>x+10)))
      AllFuda.replaceFudaNumPattern("[12]? ?[34]") shouldBe " " + f((0 to 9).flatMap(x=>Array(10+x,20+x)) ++ (1 to 9).flatMap(x=>Array(x*10+3,x*10+4)))
  }
}
