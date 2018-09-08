package karuta.hpnpwd.wasuramoti

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.{Robolectric, RobolectricTestRunner, RuntimeEnvironment}
import org.scalatest.Matchers
import org.scalatest.junit.JUnitSuite
import scala.collection.mutable
import java.util.BitSet
import scala.util.Random

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
    Romanization.japToRoma("わすらもち 0123") shouldBe "wasuramochi 0123"
    Romanization.romaToJap("wasuramoti 4567") shouldBe "わすらもち 4567"
    Romanization.zenkakuToHankaku("＊？［］１２３４５６７８９０") shouldBe "*?[]1234567890"
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
  @Test
  def testMakeKimarijiSet(){
    val context = RuntimeEnvironment.application.getApplicationContext
    AllFuda.init(context)
    val result = TrieUtils.makeKimarijiSet(Seq("あきの","あさぼらけあ","いまこ","いに","つき","あきか","いまこ","つく","あさぼらけう","みかき","もも","みかの","わたのはらや","せ","みち","わび","ありあ"))
    result shouldBe Some(("せ つ もも いに いまこ みか みち わたのはらや わび あき あさぼ ありあ",16))
  }

  @Test
  def testSortByMusumefusahose(){
    val context = RuntimeEnvironment.application.getApplicationContext
    AllFuda.init(context)
    val result = AllFuda.sortByMusumefusahose(1 to AllFuda.list.length)
    result shouldBe(Seq(87, 18, 57, 22, 70, 81, 77, 74, 65, 23, 13, 40, 37, 100, 66, 71, 46, 61, 21, 63, 75, 42, 17, 33, 35, 99, 50, 15, 91, 96, 9, 2, 67, 47, 59, 32, 28, 93, 83, 85, 62, 51, 6, 98, 48, 49, 27, 90, 14, 94, 73, 55, 4, 16, 89, 34, 41, 29, 68, 97, 24, 10, 60, 95, 44, 5, 26, 72, 82, 8, 92, 38, 54, 76, 11, 20, 80, 84, 53, 86, 36, 25, 88, 19, 43, 79, 1, 52, 39, 31, 64, 3, 12, 7, 56, 69, 30, 58, 78, 45))

  }

  @Test
  def testHtmlAttrFormatter(){
    val context = RuntimeEnvironment.application.getApplicationContext
    context.setTheme(R.style.Wasuramoti_MainTheme)
    val html = """<font color='?attr/confCurrentValueColor'>apple</font>
<font color='?attr/poemTextFuriganaColor'>banana</font>
<font color='?attr/torifudaEdgeColor'>candy</font>
<font color='?attr/hogefugaColor'>dragon</font>"""
    Utils.htmlAttrFormatter(context,html) shouldBe(
      """<font color='#ffa500'>apple</font>
<font color='#c7effb'>banana</font>
<font color='#002a11'>candy</font>
<font >dragon</font>""")
    
  }

  @Test
  def testBitSet() {
    def doIt(orig:Array[Byte]) {
      val bs1 = BitSet.valueOf(orig)
      val bs2 = FudaSetTransferHelper.byteArrayToBitSetForAPI18(orig)
      bs1 shouldBe bs2 
      val ar1 = bs1.toByteArray()
      val ar2 = FudaSetTransferHelper.bitSetToByteArrayForAPI18(bs1)
      ar1 shouldBe ar2
    }
    doIt(Array[Byte](182.toByte,119.toByte,156.toByte,227.toByte))

    // test random bitset
    val rand = new Random
    for (i <- 0 until 1000) {
      val len = rand.nextInt(12)
      val ar = new Array[Byte](len)
      rand.nextBytes(ar)
      doIt(ar)
    }
  }

  @Test
  def testEncodeFudaSet(){
    val context = RuntimeEnvironment.application.getApplicationContext
    AllFuda.init(context)
    Globals.database = Some(new DictionaryOpenHelper(context))
    val str = FudaSetTransferHelper.encodeAllFudaSet()
    FudaSetTransferHelper.decodeAndSaveFudaSets(context,str)

  }

}
