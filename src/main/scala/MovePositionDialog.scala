package karuta.hpnpwd.wasuramoti

import android.app.{AlertDialog,Dialog,Activity}
import android.os.{Bundle,Handler}
import android.view.{View,LayoutInflater,MotionEvent,ViewGroup}
import android.widget.{TextView,Button,EditText,BaseAdapter,Filter,ListView,Filterable,AdapterView}
import android.support.v4.app.DialogFragment
import android.content.Context
import android.text.{Editable,TextWatcher,TextUtils}

import scala.util.Sorting
import scala.collection.mutable

class MovePositionDialog extends DialogFragment{
  var current_index = 0 // displayed number in TextView differs from real index
  var numbers_to_read = 0
  def setCurrentIndex(text:TextView){
    val (index_s,_) = Utils.makeDisplayedNum(current_index,numbers_to_read)
    text.setText(index_s.toString)
  }
  def incCurrentIndex(text:TextView,dx:Int):Boolean = {
    val offset = if(Utils.readFirstFuda){ 0 } else { 1 }
    val (n,total_s) = Utils.makeDisplayedNum(current_index+dx,numbers_to_read)
    val prev_index = current_index
    current_index = Math.min(Math.max(n,1),total_s) + offset
    if(prev_index != current_index){
      setCurrentIndex(text)
      true
    }else{
      false
    }
  }
  def setOnClick(view:View,id:Int,func:()=>Unit){
    view.findViewById(id).asInstanceOf[Button].setOnClickListener(
      new View.OnClickListener(){
        override def onClick(v:View){
          func()
        }
      })
  }

  abstract class RunnableWithCounter(var counter:Int) extends Runnable{
  }

  def startIncrement(handler:Handler,text:TextView,is_inc:Boolean){
    lazy val runnable:RunnableWithCounter = new RunnableWithCounter(0){
      override def run(){
        runnable.counter += 1
        val (dx,delay) = if(runnable.counter > 15){
          (3,150)
        }else if(runnable.counter > 7){
          (2,200)
        }else if(runnable.counter > 2){
          (1,300)
        }else{
          (1,500)
        }

        if(incCurrentIndex(text,if(is_inc){dx}else{-dx})){
          handler.postDelayed(runnable,delay)
        }
      }
    }
    runnable.run()
  }

  def endIncrement(handler:Handler){
    handler.removeCallbacksAndMessages(null)
  }

  def setIncrementWhilePressed(handler:Handler,view:View,text:TextView,id:Int,is_inc:Boolean){
    view.findViewById(id).asInstanceOf[Button].setOnTouchListener(
      new View.OnTouchListener(){
        override def onTouch(v:View, event:MotionEvent):Boolean = {
          event.getAction match {
            case MotionEvent.ACTION_DOWN => startIncrement(handler,text,is_inc)
            case MotionEvent.ACTION_UP => endIncrement(handler)
            case _ =>
          }
          false
        }
      }
    )
  }

  override def onSaveInstanceState(state:Bundle){
    super.onSaveInstanceState(state)
    state.putInt("current_index",current_index)
    state.putInt("numbers_to_read",numbers_to_read)
  }
  override def onCreateDialog(savedState:Bundle):Dialog = {
    if(savedState != null){
      numbers_to_read = savedState.getInt("numbers_to_read",0)
      current_index = savedState.getInt("current_index",0)
    }else{
      numbers_to_read = FudaListHelper.getOrQueryNumbersToReadAlt()
      current_index = FudaListHelper.getOrQueryCurrentIndexWithSkip(getActivity)
    }
    val builder = new AlertDialog.Builder(getActivity)
    val view = LayoutInflater.from(getActivity).inflate(R.layout.move_position,null)
    val text = view.findViewById(R.id.move_position_index).asInstanceOf[TextView]
    builder.setView(view)
    .setTitle(R.string.move_position_title)
    .setNegativeButton(android.R.string.cancel,null)
    val handler = new Handler()
    setIncrementWhilePressed(handler,view,text,R.id.move_button_next,true)
    setIncrementWhilePressed(handler,view,text,R.id.move_button_prev,false)
    setOnClick(view,R.id.move_button_goto_num, {() => onOk() })
   
    val (index_s,total_s) = Utils.makeDisplayedNum(current_index, numbers_to_read)
    view.findViewById(R.id.move_position_total).asInstanceOf[TextView].setText(total_s.toString)
    setCurrentIndex(text)

    val all_list = AllFuda.get(getActivity,R.array.list_full)
    val author_list = AllFuda.get(getActivity,R.array.author)
    val fudalist = FudaListHelper.getHaveToReadFromDBAsInt("= 0").map{fudanum =>
      val poem = AllFuda.removeInsideParens(all_list(fudanum))
      val author = AllFuda.removeInsideParens(author_list(fudanum))
      new SearchFudaListItem(poem,author,fudanum)
    }.toArray
    Sorting.quickSort(fudalist)
    val list_view = view.findViewById(R.id.move_search_list).asInstanceOf[ListView]
    val notfound_view = view.findViewById(R.id.move_search_notfound).asInstanceOf[TextView] 
    val adapter = new CustomFilteredArrayAdapter(getActivity,fudalist,list_view,notfound_view)
    list_view.setAdapter(adapter)
    list_view.setOnItemClickListener(new AdapterView.OnItemClickListener(){
      override def onItemClick(parent:AdapterView[_],view:View,position:Int,id:Long){
        val wa = getActivity.asInstanceOf[WasuramotiActivity]
        if(Utils.isRandom){
          wa.refreshAndSetButton(nextRandom = Some(id.toInt))
          wa.invalidateYomiInfo()
        }else{
          FudaListHelper.queryIndexFromFudaNum(id.toInt).foreach{index =>{
            FudaListHelper.putCurrentIndex(wa,index)
            wa.refreshAndSetButton()
            wa.invalidateYomiInfo()
          }}
        }
        getDialog.dismiss()
        Utils.playAfterMove(wa)
      }
    })
    view.findViewById(R.id.move_search_text).asInstanceOf[EditText].addTextChangedListener(new TextWatcher(){
      override def afterTextChanged(s:Editable){
        adapter.getFilter.filter(s)
      }
      override def beforeTextChanged(s:CharSequence,start:Int,count:Int,after:Int){
      }
      override def onTextChanged(s:CharSequence,start:Int,before:Int,count:Int){
      }
    })

    return builder.create
  }
  def onOk(){
    val wa = getActivity.asInstanceOf[WasuramotiActivity]
    FudaListHelper.updateCurrentIndexWithSkip(getActivity,Some(current_index))
    wa.refreshAndInvalidate()
    getDialog.dismiss()
    Utils.playAfterMove(wa)
  }
  def onPrev(text:TextView){incCurrentIndex(text,-1)}
  def onNext(text:TextView){incCurrentIndex(text,1)}

}

class SearchFudaListItem(val poem_text:String, val author:String, val num:Int) extends Ordered[SearchFudaListItem]{
  override def compare(that:SearchFudaListItem):Int = {
      num.compare(that.num)
  }
}

class CustomFilteredArrayAdapter(context:Context,orig:Array[SearchFudaListItem],
  list_view:ListView,notfound_view:TextView) extends BaseAdapter with Filterable {
  lazy val index_poem = PoemSearchUtils.genIndex(context,R.array.poem_index)
  lazy val index_author = PoemSearchUtils.genIndex(context,R.array.author_index)
  var objects = orig
  override def getCount():Int = {
    Option(objects).map{_.length}.getOrElse(0)
  }
  override def getItem(position:Int):Object = {
    Option(objects).map{_(position)}.getOrElse(null)
  }
  override def getItemId(position:Int):Long = {
    Option(objects).map{_(position).num.toLong}.getOrElse(0)
  }
  override def getView(position:Int,convertView:View, parent:ViewGroup):View = {
    if(objects == null){
      null
    }else{
      val view = Option(convertView).getOrElse{LayoutInflater.from(context).inflate(R.layout.my_simple_list_item_search,null)}
      val item = objects(position)
      view.findViewById(android.R.id.text1).asInstanceOf[TextView].setText(item.poem_text)
      view.findViewById(android.R.id.text2).asInstanceOf[TextView].setText(item.author)
      view
    }
  }

  // in Android's Java, regex \p{Alpha} matches to japanese letter
  //   http://www.ecoop.net/memo/archives/regular-expression-problem-o-android-java.html
  lazy val TOO_SHORT_PATTERN = """^[a-zA-Z\uFF21-\uFF3A\uFF41-\uFF5A]$""".r.pattern
  lazy val filter = new Filter(){
      override def performFiltering(constraint:CharSequence):Filter.FilterResults = {
        val results = new Filter.FilterResults
        val values = if(TextUtils.isEmpty(constraint) || TOO_SHORT_PATTERN.matcher(constraint).matches){
          orig
        }else if(android.os.Build.VERSION.SDK_INT <= 8){
          // Android <= 2.2 has a bug that cannot load large string array from resource file.
          // It was fixed in https://android.googlesource.com/platform/frameworks/base.git/+/4d4040b7b3c46ea29a42878c14b325f09f0029ad
          // The error message was:
          //   ReferenceTable overflow (max=512)
          //     at android.content.res.AssetManager.getArrayStringResource(Native Method)
          //     at android.content.res.AssetManager.getResourceStringArray(AssetManager.java:186)
          //     at android.content.res.Resources.getStringArray(Resources.java:381)

          // One workaround for the bug above is splitting the <string-array>, however, we just use simple search instead
          val found = PoemSearchUtils.doSearchSimple(context,constraint)
          orig.filter{p => found(p.num)}
        }else{
          val found = PoemSearchUtils.doSearch(context,Array(index_poem,index_author),constraint)
          orig.filter{p => found(p.num)}
        }
        context.asInstanceOf[Activity].runOnUiThread(new Runnable(){
          override def run(){
            if(values.isEmpty){
              list_view.setVisibility(View.GONE)
              notfound_view.setVisibility(View.VISIBLE)
              val crs = context.getResources
              val bounded_by_fudaset = FudaListHelper.isBoundedByFudaset
              val has_karafuda = FudaListHelper.getOrQueryNumbersOfKarafuda > 0
              val has_memorized = FudaListHelper.getOrQueryNumbersOfMemorized > 0
              val extras = Array(
                if(bounded_by_fudaset){Some(crs.getString(R.string.move_search_notfound_poemset))}else{None},
                if(has_karafuda){Some(crs.getString(R.string.move_search_notfound_karafuda))}else{None},
                if(has_memorized){Some(crs.getString(R.string.move_search_notfound_memorized))}else{None}
                ).flatten

              val text = crs.getString(R.string.move_search_notfound) +
              (if(extras.nonEmpty){
                " " + crs.getString(
                  R.string.move_search_notfound_extra,extras.mkString(
                    " " + crs.getString(R.string.move_search_notfound_extra_delimiter) + " "
                  ))
              }else{
                ""
              })
              notfound_view.setText(text)
            }else{
              list_view.setVisibility(View.VISIBLE)
              notfound_view.setVisibility(View.GONE)
              notfound_view.setText("")
            }
          }
        })
        results.values = values
        results.count = values.size
        results
      }
      override def publishResults(constraint:CharSequence,results:Filter.FilterResults){
        objects = results.values.asInstanceOf[Array[SearchFudaListItem]]
        // TODO: sort by score: https://www.elastic.co/guide/en/elasticsearch/guide/master/scoring-theory.html
        if (results.count > 0) {
          notifyDataSetChanged();
        } else {
          notifyDataSetInvalidated();
        }
      }
    }

  override def getFilter():Filter = {
    filter
  }
}

object PoemSearchUtils{
  type Index = Map[String,Array[Int]]
  def genIndex(context:Context,res_id:Int):Index = {
    // TODO: generate index only from poems in fudaset
    val res = mutable.Map[String,Array[Int]]()
    for(str <- AllFuda.get(context,res_id)){
      val Array(numlist,ngram) = str.split(" :: ")
      val nums = numlist.split(",").map{_.toInt}
      for(s <- ngram.split("/")){
        res.+=((s,nums))
      }
    }
    res.toMap
  }

  // DalvikVM does not support isHan
  val REMOVE_PATTEN_JP = """[^\p{Blank}\p{InHiragana}\p{InCJKUnifiedIdeographs}\p{InCJKSymbolsAndPunctuation}]+""".r
  def preprocessConstraint(context:Context,chars:CharSequence):String = {
    val str = if(Romanization.is_japanese(context)){
      chars.toString
    }else{
      Romanization.roma_to_jap(chars.toString)
    }
    REMOVE_PATTEN_JP.replaceAllIn(str,"")
  }

  def splitPhrase(phrase:String):Array[String] = {
    """\p{Blank}+""".r.split(phrase).filter{!TextUtils.isEmpty(_)}
  }

  def findInIndex(index:Index,keyword:String):Set[Int] = {
    //TODO: following method searches all bigram even after `index.get()` returns None
    val (found,notfound) = keyword.sliding(2).map{ s => {
        index.get(s).map{_.toSet}
      }}.span{_.nonEmpty}

    if(notfound.nonEmpty){
      Set()
    }else{
      found.flatten.reduce{(x,y)=>x.intersect(y)}
    }
  }

  def doSearch(context:Context,indices:Array[Index],constraint:CharSequence):Set[Int] = {
    val phrases = splitPhrase(preprocessConstraint(context,constraint))
    if(phrases.isEmpty){
      return Set()
    }
    phrases.map{ p =>
      indices.map{ index =>
        findInIndex(index,p)
      }.reduce{(x,y) => x ++ y}
    }.reduce{(x,y)=>x.intersect(y)}
  }

  def doSearchSimple(context:Context,constraint:CharSequence):Set[Int] = {
    val NOT_HIRA = """[^\p{InHiragana}]+""".r
    val phrases = splitPhrase(preprocessConstraint(context,constraint))
    if(phrases.isEmpty){
      return Set()
    }
    phrases.map{ p =>
      Array(R.array.list_full,R.array.author).map{ id =>
        AllFuda.get(context,id).drop(1).zipWithIndex.collect{
          case (poem,i) if AllFuda.removeInsideParens(poem).contains(p) || NOT_HIRA.replaceAllIn(poem,"").contains(p) => i+1
        }.toSet
      }.reduce{(x,y) => x ++ y}
    }.reduce{(x,y)=>x.intersect(y)}
  }
}
