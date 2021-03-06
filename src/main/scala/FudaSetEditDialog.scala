package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.content.Context
import android.text.{TextUtils,Html}
import android.view.{LayoutInflater,ViewGroup}
import android.widget.{EditText,TextView,ArrayAdapter,Filter,ToggleButton,ListView}
import android.app.Dialog

import android.support.v4.app.DialogFragment

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.immutable.ListSet


object FudaSetEditDialogFragment {
  def newInstance(isAdd:Boolean,origTitle:String,origFs:FudaSetWithSize):FudaSetEditDialogFragment = {
    val fragment = new FudaSetEditDialogFragment
    val bundle = new Bundle
    bundle.putBoolean("is_add",isAdd)
    bundle.putString("orig_title",origTitle)
    bundle.putSerializable("orig_fs", origFs)
    fragment.setArguments(bundle)
    return fragment
  }
}

class FudaSetEditDialogFragment extends DialogFragment with CommonDialog.CallbackListener{
  val self = this
  override def onCommonDialogCallback(bundle:Bundle){
    bundle.getString("tag") match {
      case "fudasetedit_dialog_closed" =>
        val kimari = bundle.getString("kimari")
        val title = bundle.getString("title")
        val stSize =  bundle.getInt("st_size")
        val origTitle = bundle.getSerializable("orig_title").asInstanceOf[Option[String]]
        Utils.writeFudaSetToDB(getContext,title,kimari,stSize,origTitle)

        val args = super.getArguments
        val isAdd = args.getBoolean("is_add")
        val origFs = args.getSerializable("orig_fs").asInstanceOf[FudaSetWithSize]
        val newFs = new FudaSetWithSize(title,stSize)
        getTargetFragment.asInstanceOf[FudaSetEditListener].onFudaSetEditListenerResult(isAdd,origFs,newFs)

        Globals.forceRefreshPlayer = true
        dismiss
      case "fudaset_edit_num_done" | "fudaset_edit_initial_done" =>
        val fudaset = bundle.getSerializable("set").asInstanceOf[ListSet[Int]]
        getDialog.asInstanceOf[FudaSetEditDialog].appendNums(fudaset)
      case "fudaset_edit_list_done" =>
        val body_view = getDialog.findViewById[LocalizationEditText](R.id.fudasetedit_text)
        val body = bundle.getString("body")
        body_view.setLocalizationText(body)
    }
  }
  override def onCreateDialog(state:Bundle):Dialog = {
    val args = super.getArguments
    val isAdd = args.getBoolean("is_add")
    val origTitle = args.getString("orig_title")
    new FudaSetEditDialog(getContext,isAdd,origTitle)
  }
  class FudaSetEditDialog(
    context:Context,
    is_add:Boolean,
    orig_title:String) extends CustomAlertDialog(context) with ButtonListener{

    override def buttonMapping = Map(
        R.id.button_fudasetedit_list -> buttonFudasetEditList _,
        R.id.button_fudasetedit_initial -> buttonFudasetEditInitial _,
        R.id.button_fudasetedit_num -> buttonFudasetEditNum _,
        R.id.fudasetedit_help_html -> helpHtmlClicked _
      )

    var data_id = None:Option[Long]

    def makeKimarijiSetFromBodyView(body_view:LocalizationEditText):Option[(String,Int)] = {
      val PATTERN_HIRAGANA = "[あ-ん]+".r
      var text = body_view.getLocalizationText()
      text = AllFuda.replaceFudaNumPattern(text)
      TrieUtils.makeKimarijiSet(PATTERN_HIRAGANA.findAllIn(text).toList)
    }

    override def onCreate(bundle:Bundle){
      setTitle(R.string.fudasetedit_title)
      val view = LayoutInflater.from(context).inflate(R.layout.fudaset_edit,null)
      val title_view = view.findViewById[EditText](R.id.fudasetedit_name)
      val body_view = view.findViewById[LocalizationEditText](R.id.fudasetedit_text)
      if(!is_add){
        title_view.setText(orig_title)
        val fs = FudaListHelper.selectFudasetByTitle(orig_title)
        fs.foreach{ f => body_view.setLocalizationText(f.body) }
        data_id = fs.map{_.id}
      }
      val help_view = view.findViewById[TextView](R.id.fudasetedit_help_html)
      help_view.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,context.getString(R.string.fudasetedit_help_html))))
      setButtonMapping(view)
      setView(view)
      super.onCreate(bundle)

    }

    def helpHtmlClicked(){
      CommonDialog.generalHtmlDialog(context,Right(R.string.fudasetedit_fudanum_html))
    }

    override def doWhenClose():Boolean = {
     Globals.db_lock.synchronized{
        val title_view = findViewById[EditText](R.id.fudasetedit_name)
        val body_view = findViewById[LocalizationEditText](R.id.fudasetedit_text)
        val title = title_view.getText.toString
        if(TextUtils.isEmpty(title)){
          CommonDialog.messageDialog(context,Right(R.string.fudasetedit_titleempty))
          return false
        }
        if(FudaListHelper.isDuplicatedFudasetTitle(title,is_add,data_id)){
          CommonDialog.messageDialog(context,Right(R.string.fudasetedit_titleduplicated))
          return false
        }
        makeKimarijiSetFromBodyView(body_view) match {
        case None =>
          CommonDialog.messageDialog(context,Right(R.string.fudasetedit_setempty))
          return false
        case Some((kimari,st_size)) =>
          val message = context.getString(R.string.fudasetedit_confirm,new java.lang.Integer(st_size))
          val result = new Bundle()
          result.putString("tag","fudasetedit_dialog_closed")
          result.putString("kimari",kimari)
          result.putString("title",title)
          result.putInt("st_size",st_size)
          result.putSerializable("orig_title",if(is_add){None}else{Some(orig_title)})
          CommonDialog.confirmDialogWithCallback(self,Left(message),result)
        }
      }
      return false
    }
    def getCurrentKimarijis():String={
      val body_view = this.findViewById[LocalizationEditText](R.id.fudasetedit_text)
      return makeKimarijiSetFromBodyView(body_view).map{_._1}.getOrElse("")
    }
    def buttonFudasetEditList(){
      val kms = getCurrentKimarijis
      FudaSetEditListDialog.show(self,kms)
    }

    def appendNums(st:Set[Int]){
      val body_view = this.findViewById[LocalizationEditText](R.id.fudasetedit_text)
      val kms = getCurrentKimarijis
      val curs = TrieUtils.makeHaveToRead(kms)
      val sts = st.map{(x:Int)=>AllFuda.list(x-1)}
      val str = TrieUtils.makeKimarijiSet((curs++sts).toSeq).map{_._1}.getOrElse("")
      body_view.setLocalizationText(str)
    }

    def buttonFudasetEditInitial(){
      CommonDialog.showWrappedDialogWithCallback[FudaSetEditInitialDialog](self)
    }
    def buttonFudasetEditNum(){
      CommonDialog.showWrappedDialogWithCallback[FudaSetEditNumDialog](self)
    }
  }
}


object FudaSetEditUtils{

  def aggregateToggleButton(vg:ViewGroup,tag_id:Int,ignore:Char,build:mutable.StringBuilder){
    for(i <- 0 until vg.getChildCount){
      vg.getChildAt(i) match {
        case v:ViewGroup => aggregateToggleButton(v,tag_id,ignore,build)
        case v:ToggleButton => 
          val cc = v.getTag(tag_id).asInstanceOf[Char]
          if(v.isChecked && cc != ignore){build.append(cc)}
        case _ => ()
      }
    }
  }

  def filterableAdapter[T](
    context:Context,
    list_view:ListView,
    filter_func:(CharSequence)=>Array[T],
    ordering:Ordering[T]):ArrayAdapter[T]={
    val fudalist = mutable.ArrayBuffer[T]()
    val checked = mutable.Map[T,Boolean]()
    return new ArrayAdapter[T](context,R.layout.my_simple_list_item_multiple_choice,fudalist.asJava){
      val adapter = this
      val filter = new Filter(){
        override def performFiltering(constraint:CharSequence):Filter.FilterResults = {
          val results = new Filter.FilterResults
          if(TextUtils.isEmpty(constraint)){
            results.values = Array()
            results.count = 0
          }else{
            val arr = filter_func(constraint) 
            results.values = arr
            results.count = arr.size
          }
          return results
        }
        override def publishResults(constraint:CharSequence,results:Filter.FilterResults){
          // preserve checked state from ListView
          checked.clear()
          val poss = list_view.getCheckedItemPositions
          for(i <- 0 until adapter.getCount){
            checked += ((adapter.getItem(i),poss.get(i,true)))
          }

          fudalist.clear()
          fudalist ++= results.values.asInstanceOf[Array[T]]
          adapter.sort(ordering)
          if(results.count > 0){
            adapter.notifyDataSetChanged()
          }else{
            adapter.notifyDataSetInvalidated()
          }

          // restore checked state
          for(i <- 0 until adapter.getCount){
            val state = checked.get(adapter.getItem(i)) match {
              case None | Some(true) => true
              case Some(false) => false
            }
            list_view.setItemChecked(i,state)
          }

        }
      }
      override def getFilter():Filter = {
        return filter
      }
    }
  }
}
