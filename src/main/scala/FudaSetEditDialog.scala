package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.content.Context
import android.text.{TextUtils,Html}
import android.view.{View,LayoutInflater,ViewGroup}
import android.widget.{EditText,TextView,ArrayAdapter,Filter,ToggleButton,ListView}
import android.app.Dialog

import android.support.v7.app.{AlertDialog,AppCompatDialog}
import android.support.v4.app.DialogFragment

import scala.collection.JavaConversions
import scala.collection.mutable


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

class FudaSetEditDialogFragment extends DialogFragment with CommonDialog.CallBackListener{
  val self = this
  override def onCommonDialogResult(dialogId:Int, bundle:Bundle){
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
      val title_view = view.findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
      val body_view = view.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      if(!is_add){
        title_view.setText(orig_title)
        val fs = FudaListHelper.selectFudasetByTitle(orig_title)
        fs.foreach{ f => body_view.setLocalizationText(f.body) }
        data_id = fs.map{_.id}
      }
      val help_view = view.findViewById(R.id.fudasetedit_help_html).asInstanceOf[TextView]
      help_view.setText(Html.fromHtml(Utils.htmlAttrFormatter(context,context.getString(R.string.fudasetedit_help_html))))
      setButtonMapping(view)
      setView(view)
      super.onCreate(bundle)

    }

    def helpHtmlClicked(){
      Utils.generalHtmlDialog(context,Right(R.string.fudasetedit_fudanum_html))
    }

    override def doWhenClose():Boolean = {
     Globals.db_lock.synchronized{
        val title_view = findViewById(R.id.fudasetedit_name).asInstanceOf[EditText]
        val body_view = findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
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
          result.getString("kimari",kimari)
          result.putString("title",title)
          result.putInt("st_size",st_size)
          result.putSerializable("orig_title",if(is_add){None}else{Some(orig_title)})
          CommonDialog.confirmDialog(self,Left(message),0,result)
        }
      }
      return false
    }
    def getCurrentKimarijis():String={
      val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      return makeKimarijiSetFromBodyView(body_view).map{_._1}.getOrElse("")
    }
    def buttonFudasetEditList(){
      val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      val kms = getCurrentKimarijis
      val d = new FudaSetEditListDialog(context,kms,{body_view.setLocalizationText(_)})
      d.show()
    }

    def appendNums(st:Set[Int]){
      val body_view = this.findViewById(R.id.fudasetedit_text).asInstanceOf[LocalizationEditText]
      val kms = getCurrentKimarijis
      val curs = TrieUtils.makeHaveToRead(kms)
      val sts = st.map{(x:Int)=>AllFuda.list(x-1)}
      val str = TrieUtils.makeKimarijiSet((curs++sts).toSeq).map{_._1}.getOrElse("")
      body_view.setLocalizationText(str)
    }

    def buttonFudasetEditInitial(){
      val dialog = new FudaSetEditInitialDialog(context,appendNums)
      dialog.show()
    }
    def buttonFudasetEditNum(){
      val dialog = new FudaSetEditNumDialog(context,appendNums)
      dialog.show()
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
    return new ArrayAdapter[T](context,R.layout.my_simple_list_item_multiple_choice,JavaConversions.bufferAsJavaList(fudalist)){
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
