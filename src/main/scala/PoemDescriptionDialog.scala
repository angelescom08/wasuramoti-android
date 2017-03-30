package karuta.hpnpwd.wasuramoti

import android.support.v4.app.DialogFragment
import android.app.{AlertDialog,Dialog,SearchManager}
import android.os.Bundle
import android.widget.TextView
import android.view.LayoutInflater
import android.content.Intent
import android.net.Uri
import android.text.Html

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
    R.id.poem_desc_search_author -> {()=>doWebSearch(true)},
    R.id.poem_desc_reference -> gotoReference _
    )
  override def onCreateDialog(saved:Bundle):Dialog = {
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.poem_description,null)
    getFudanum.foreach{ num =>
      val source_id = if(num == 0){
        R.string.poem_desc_reference_wikipedia
      }else{
        R.string.poem_desc_reference_shigureden
      }
      view.findViewById(R.id.poem_desc_reference).asInstanceOf[TextView].setText(source_id)

      val poem = AllFuda.get(getActivity,R.array.list_full)(num)
      val desc = AllFuda.get(getActivity,R.array.poem_description)(num)
      val author_desc = AllFuda.get(getActivity,R.array.author_description)(num)
      val body = if(Romanization.is_japanese(getActivity)){
        val author = AllFuda.get(getActivity,R.array.author)(num)
        val theme = AllFuda.get(getActivity,R.array.poem_theme)(num)
        getString(R.string.poem_desc_body,new java.lang.Integer(num),poem,theme,desc,author,author_desc)
      }else{
        val author = AllFuda.get(getActivity,R.array.author_en)(num)
        val romaji = AllFuda.get(getActivity,R.array.list_full_romaji)(num).replaceAllLiterally("|"," ")
        val eng =  AllFuda.get(getActivity,R.array.list_full_en)(num).replaceAll("(//|##)"," ")
        getString(R.string.poem_desc_body,new java.lang.Integer(num),poem,romaji,eng,desc,author,author_desc)
      }
      view.findViewById(R.id.poem_desc_body).asInstanceOf[TextView].setText(Html.fromHtml(body))
    }
    setButtonMapping(view)
    builder
      .setView(view)
      .setPositiveButton(android.R.string.ok,null)
      .create
  }
  def gotoReference(){
    getFudanum.foreach{ num =>
      val intent = new Intent(Intent.ACTION_VIEW)
      val url = if(num == 0){
        getString(R.string.poem_desc_reference_wikipedia_url)
      }else{
        "https://www.shigureden.or.jp/about/database_03.html?id="+num
      }
      intent.setData(Uri.parse(url))
      startActivity(intent)
    }
  }
  def doWebSearch(search_author:Boolean){
    val fudanum = getFudanum
    val query = if(!search_author){
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.list_full)(num))
      }.getOrElse{
        getString(R.string.search_text_default)
      }
    }else{
      fudanum.map{ num =>
        AllFuda.removeInsideParens(AllFuda.get(getActivity,R.array.author)(num)).replace(" ","")
      }.getOrElse{
        getString(R.string.search_text_default)
      } + " " + getString(R.string.search_text_author)
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
