package karuta.hpnpwd.wasuramoti

import android.os.Bundle
import android.support.v7.preference.{DialogPreference,PreferenceDialogFragmentCompat}
import android.support.v7.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import scala.reflect.ClassTag

import scala.collection.mutable

object PrefWidgets {
  // shuld be same as PreferenceDialogFragmentCompat.ARG_KEY
  val ARG_KEY = "key"
  def newInstance[C <: PreferenceDialogFragmentCompat](key:String)(implicit tag:ClassTag[C]):C = {
    val fragment = tag.runtimeClass.getConstructor().newInstance().asInstanceOf[C]
    val bundle = new Bundle(1)
    bundle.putString(ARG_KEY, key)
    fragment.setArguments(bundle)
    return fragment
  }
}

class ReadOrderPreferenceFragment extends PreferenceDialogFragmentCompat {
  override def onPrepareDialogBuilder(builder:AlertDialog.Builder){
    val context = getContext
    val pref = getPreference.asInstanceOf[ReadOrderPreference]
    val helper = new GeneralRadioHelper(context,builder)
    val ar = context.getResources.getStringArray(R.array.conf_read_order_entries)
    // TODO: use View.generateViewId() for API >= 17
    val ids = context.getResources.obtainTypedArray(R.array.general_radio_helper)
    val id2key = mutable.Map[Int,String]()
    val persisted = pref.getPersistedString("SHUFFLE")
    var currentId = None:Option[Int]
    val items = ar.zipWithIndex.map{ case (x,i) =>
      val id = ids.getResourceId(i,-1)
      if(id == -1){
        throw new RuntimeException(s"index out of range for general_radio_helper[${i}]")
      }
      val Array(key,title,desc) = x.split("\\|")
      id2key += ((id,key))
      if(key == persisted){
        currentId = Some(id)
      }
      GeneralRadioHelper.Item(id,Right(title),Right(desc))
    }
    val handler = (id:Int) => {
      id2key.get(id).foreach(pref.persistString(_))
      getDialog.dismiss()
    }
    helper.setDescription(R.string.conf_read_order_desc)
    helper.addItems(items, Some(handler))
    currentId.foreach(helper.radio_group.check(_))
    builder.setPositiveButton(null,null)
    ids.recycle()
    super.onPrepareDialogBuilder(builder)
  }
  override def onDialogClosed(b:Boolean){
  }
}

class ReadOrderPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) {
  def this(context:Context,attrs:AttributeSet,def_style:Int) = this(context,attrs)
  override def getPersistedString(default:String):String = {
    super.getPersistedString(default)
  }
  override def persistString(value:String):Boolean = {
    super.persistString(value)
  }
}
