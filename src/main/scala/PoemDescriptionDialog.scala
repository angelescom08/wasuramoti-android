package karuta.hpnpwd.wasuramoti

import android.support.v4.app.DialogFragment
import android.app.{AlertDialog,Dialog,SearchManager}
import android.os.Bundle
import android.view.LayoutInflater
import android.content.Intent
import android.net.Uri

object PoemDescriptionDialog{
  def newInstance(fudanum:Option[Int]):PoemDescriptionDialog = {
    val fragment = new PoemDescriptionDialog
    val args = new Bundle
    args.putSerializable("fudanum",fudanum)
    fragment.setArguments(args)
    return fragment
  }
}

class PoemDescriptionDialog extends DialogFragment with GetFudanum with ButtonListener{

  override def buttonMapping = Map(
    R.id.poem_desc_search_poem -> {()=>doWebSearch(false)},
    R.id.poem_desc_search_author -> {()=>doWebSearch(false)},
    R.id.poem_desc_reference -> gotoReference _
    )
  override def onCreateDialog(saved:Bundle):Dialog = {
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.poem_description,null)
    setButtonMapping(view)
    builder
      .setView(view).create
  }
  def gotoReference(){
    getFudanum.foreach{ num =>
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.setData(Uri.parse("https://www.shigureden.or.jp/about/database_03.html?id="+num))
      startActivity(intent)
    }
  }
  def doWebSearch(search_author:Boolean){
    val fudanum = getFudanum
    val query = if(!search_author){
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.list_full)(num))
      }.getOrElse{
        getActivity.getString(R.string.search_text_default)
      }
    }else{
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.author)(num)).replace(" ","")
      }.getOrElse{
        getActivity.getString(R.string.search_text_default)
      } + " " + getActivity.getString(R.string.search_text_author)
    }
    val f1 = {() =>
      val intent = new Intent(Intent.ACTION_WEB_SEARCH)
      intent.putExtra(SearchManager.QUERY,query)
      Left(intent)
    }
    val f2 = {() =>
      val intent = new Intent(Intent.ACTION_VIEW)
      intent.setData(Uri.parse("http://www.google.com/search?q="+Uri.encode(query)))
      Left(intent)
    }
    val f3 = {() =>
      Right({() =>
        Utils.messageDialog(getActivity,Right(R.string.browser_not_found))
      })
    }
    // scala.util.control.Breaks.break does not work (why?)
    // Therefore we use `exists` in Traversable trait instead
    Seq(f1,f2,f3) exists {f=>
        f() match {
          case Left(intent) =>
            try{
              startActivity(intent)
              true
            }catch{
              case _:android.content.ActivityNotFoundException => false
            }
          case Right(g) => {g();true}
        }
      }
  }
}
