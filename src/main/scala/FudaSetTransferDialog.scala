package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.content.Context
import android.view.{View,LayoutInflater}
import android.widget.{Button,Toast,TextView,CheckBox}
import android.util.Base64
import android.text.Html

import java.util.BitSet

import scala.util.{Try,Failure}

@KeepConstructor
class FudaSetTransferDialog(context:Context)
  extends CustomAlertDialog(context) with CommonDialog.WrappableDialog{
  override def doWhenClose():Boolean = {
    return true
  }
  override def onCreate(state:Bundle){
    val root = LayoutInflater.from(context).inflate(R.layout.fudaset_transfer, null)
    val overwrite = root.findViewById[CheckBox](R.id.fudaset_transfer_overwrite)
    root.findViewById[Button](R.id.button_fudaset_transfer_import)
        .setOnClickListener(new View.OnClickListener(){
          override def onClick(view:View){
            fudasetTransferImport(overwrite.isChecked)
          }
        })
    root.findViewById[Button](R.id.button_fudaset_transfer_export)
        .setOnClickListener(new View.OnClickListener(){
          override def onClick(view:View){
            fudasetTransferExport()
          }
        })
    root.findViewById[TextView](R.id.fudaset_transfer_description).setText(Html.fromHtml(Utils.htmlAttrFormatter(context,context.getString(R.string.fudaset_transfer_description_html))))

    setTitle(R.string.fudaset_transfer_title)
    setView(root)
    super.onCreate(state)
  }

  def fudasetTransferExport(){
    for {
      fudaset <- Try(FudaSetTransferHelper.encodeAllFudaSet())
        .recoverWith{case e:Exception =>
          Toast.makeText(context,R.string.fudaset_transfer_failed_encode,Toast.LENGTH_SHORT).show()
          Failure(e)
        }
      _ <- Try(Utils.copyToClipBoard(context,"wasuramoti fudaset",fudaset))
        .recoverWith{case e:Exception =>
          Toast.makeText(context,R.string.fudaset_transfer_failed_clipboard,Toast.LENGTH_SHORT).show()
          Failure(e)
        }
  } {
      Toast.makeText(context,R.string.fudaset_transfer_export_success,Toast.LENGTH_SHORT).show()
    }
  }
  def fudasetTransferImport(insertOrUpdate:Boolean){
    for {
      txt <- Try(Utils.copyFromClipBoard(context))
        .recoverWith{case e:Exception =>
          Toast.makeText(context,R.string.fudaset_transfer_failed_clipboard,Toast.LENGTH_SHORT).show()
          Failure(e)
        }
      num <- Try(FudaSetTransferHelper.decodeAndSaveFudaSets(context,txt,insertOrUpdate))
        .recoverWith{case e:Exception =>
          Toast.makeText(context,R.string.fudaset_transfer_failed_decode,Toast.LENGTH_SHORT).show()
          Failure(e)
        }
    } {
      val message = context.getString(R.string.fudaset_transfer_import_success,num.toString)
      Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
      val bundle = new Bundle
      bundle.putString("tag","fudaset_list_changed")
      callbackListener.onCommonDialogCallback(bundle)
    }
  }
}

object FudaSetTransferHelper {
  def encodeAllFudaSet():String = {
    FudaListHelper.selectFudasetAll.map{fudaset => 
      val bs = new BitSet
      for(poem <- TrieUtils.makeHaveToRead(fudaset.body)){
        bs.set(AllFuda.list.indexOf(poem)+1)
      }
      new String(Base64.encode(bitSetToByteArray(bs),Base64.URL_SAFE),"utf-8").trim() + " " + fudaset.title
    }.mkString("\n")
  }

  def decodeAndSaveFudaSets(context:Context,str:String,insertOrUpdate:Boolean):Int = {
    var success = 0
    for(s <- str.split("\n")){
      try {
        val Array(b64,title) = s.split(" ",2)
        val nl = byteArrayToBitSet(Base64.decode(b64,Base64.URL_SAFE))
        TrieUtils.makeKimarijiSetFromNumList(bitSetToIntArray(nl)).foreach{
          case (kimari,st_size) =>
            if(Utils.writeFudaSetToDB(context,title,kimari,st_size,insertOrUpdate=insertOrUpdate)){
              success += 1
            }
        }
      } catch {
        case e:Exception =>
          // do nothing
      }
    }
    return success
  }

  def byteArrayToBitSet(ar:Array[Byte]):BitSet = {
    if(android.os.Build.VERSION.SDK_INT >= 19){
      return BitSet.valueOf(ar)
    } else {
      return byteArrayToBitSetForAPI18(ar)
    }
  }
  def bitSetToByteArray(bs:BitSet):Array[Byte] = {
    if(android.os.Build.VERSION.SDK_INT >= 19){
      return bs.toByteArray()
    } else {
      return bitSetToByteArrayForAPI18(bs)
    }
  }

  def bitSetToIntArray(bs:BitSet):Array[Int] = {
    if(android.os.Build.VERSION.SDK_INT >= 24){
      return bs.stream.toArray
    }else{
      return bitSetToIntArrayForAPI23(bs)
    }
  }

  def byteArrayToBitSetForAPI18(ar:Array[Byte]):BitSet = {
    val bs = new BitSet
    for (i <- 0 until ar.length*8) {
      if ((ar(i/8)&(1<<(i%8))) > 0) {
        bs.set(i)
      }
    }
    return bs
  }
  def bitSetToByteArrayForAPI18(bs:BitSet):Array[Byte] = {
    val len = if (bs.length%8==0) {bs.length/8} else {bs.length/8+1}
    val ar = new Array[Byte](len)
    for (i <- 0 until bs.length) {
      if(bs.get(i)) {
        val index = i/8
        ar(index) = (ar(index)|(1<<(i%8))).byteValue
      }
    }
    return ar
  }

  def bitSetToIntArrayForAPI23(bs:BitSet):Array[Int] = {
    (0 until bs.length).filter(bs.get(_)).toArray
  }

}
