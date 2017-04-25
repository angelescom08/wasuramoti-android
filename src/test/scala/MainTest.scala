package karuta.hpnpwd.wasuramoti

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.{Robolectric, RobolectricTestRunner, RuntimeEnvironment}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite
import scala.collection.mutable

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

  @Test
  def testSearchPoem(){
      val context = RuntimeEnvironment.application.getApplicationContext
      val index_poem = PoemSearchUtils.genIndex(context,R.array.poem_index)
      val index_author = PoemSearchUtils.genIndex(context,R.array.author_index)
      val found = PoemSearchUtils.doSearch(context,Array(index_poem,index_author),"ぬ")
      found shouldBe Set(1,10,11,13,20,24,28,32,36,37,45,46,52,53,55,57,61,65,72,74,75,78,82,87,90,92,97)
  }

  @Test
  def testAllFuda(){
    val context = RuntimeEnvironment.application.getApplicationContext
    val author = AllFuda.get(context,R.array.author)(76)
    val shrinked = AllFuda.shrinkAuthorParens(author)
    shrinked shouldBe "法性寺入道前関白太政大臣(ほっしょうじにゅうどうさきのかんぱくだいじょうだいじん)"
  }
  //@Test
  def testRandomMode(){
    val context = RuntimeEnvironment.application.getApplicationContext
    Utils.initGlobals(context)
    Utils.writeFudaSetToDB(context,"Test","め せ ちぎりお ひとも よのなかよ かぜそ みかき おおこ なげけ なにわが あわれ", 11)
    val edit = Globals.prefs.get.edit
    edit.putFloat("karafuda_urafuda_prob",0.5f)
    edit.putInt("karafuda_append_num",3)
    edit.putBoolean("karafuda_enable",true)
		edit.putString("fudaset", "Test")
    edit.commit()
    FudaListHelper.updateSkipList(context)
    val stats = mutable.Map[Int,Int]().withDefaultValue(0)
    for(i <- 1 to 10000){
      stats(FudaListHelper.queryRandom.get) += 1
    }
    for((k,v) <- stats.toSeq.sortBy(_._2)){
			println(s"${AllFuda.list(k-1)}: ${v}")
    }
  }
}
