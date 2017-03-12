package karuta.hpnpwd.wasuramoti

import android.app.AlertDialog
import android.content.Context
import android.widget.{RadioGroup,RadioButton,TextView}
import android.view.{LayoutInflater,View}

object GeneralRadioHelper{
  case class Item(id:Int, text:Either[Int,String], description:Either[Int,String])

  def eachRadioText(group:RadioGroup, handler:(View,Option[RadioButton])=>Unit){
    var last_radio_button = None:Option[RadioButton]
    for(i <- 0 until group.getChildCount){
      group.getChildAt(i) match {
        case radio:RadioButton => last_radio_button = Some(radio)
        case view => {
          if(Option(view.getTag).exists(_.toString == "radio_text")){
            handler(view, last_radio_button)
          }
        }
      }
    }
  }

  def setRadioTextClickListener(group:RadioGroup, clickHandler:Option[(Int)=>Unit]=None){
    eachRadioText(group,(view,radio)=>{
      radio.foreach{ btn => 
        view.setOnClickListener(new View.OnClickListener(){
          override def onClick(v:View){
            group.check(btn.getId)
            clickHandler.foreach(_(btn.getId))
          }
        })
        clickHandler.foreach{handler =>
          btn.setOnClickListener(new View.OnClickListener(){
            override def onClick(v:View){
              handler(btn.getId)
            }
          })
        }
    }})
  }
}

class GeneralRadioHelper(context:Context, var builder:AlertDialog.Builder = null){
  if(builder == null){
    builder = new AlertDialog.Builder(context)
  }
  val inflater = LayoutInflater.from(context)
  val view = inflater.inflate(R.layout.general_radio_dialog,null)
  val radio_group = view.findViewById(R.id.general_radio_dialog_group).asInstanceOf[RadioGroup]
  builder.setView(view)

  def setDescription(resourceId: Int){
    val text = context.getResources.getString(resourceId)
    val v = view.findViewById(R.id.general_radio_dialog_description).asInstanceOf[TextView]
    v.setText(text)
  }

  def addItems(items:Seq[GeneralRadioHelper.Item], clickHandler:Option[(Int)=>Unit]=None){
    for(item <- items){
      inflater.inflate(R.layout.general_radio_dialog_item,radio_group,true)
    }
    val iter = items.iterator
    GeneralRadioHelper.eachRadioText(radio_group, (view,radio)=>{
      radio.foreach{ r =>
        val item = iter.next
        r.setId(item.id)
        item.text match {
          case Left(id) => r.setText(id)
          case Right(txt) => r.setText(txt)
        }
        item.description match {
          case Left(id) =>  view.asInstanceOf[TextView].setText(id)
          case Right(txt) =>  view.asInstanceOf[TextView].setText(txt)
        }
      }
    })
    GeneralRadioHelper.setRadioTextClickListener(radio_group, clickHandler)
  }
}
