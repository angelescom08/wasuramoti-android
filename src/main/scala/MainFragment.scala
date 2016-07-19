package karuta.hpnpwd.wasuramoti

import android.app.{Activity,AlertDialog}
import android.content.{Intent,Context,DialogInterface}
import android.os.{Bundle,Handler,Build}
import android.support.v7.app.{ActionBarActivity,ActionBar}
import android.support.v4.app.Fragment
import android.util.{Base64,TypedValue}
import android.view.animation.{AnimationUtils,Interpolator}
import android.view.{View,Menu,MenuItem,WindowManager,ViewStub,LayoutInflater,ViewGroup}
import android.widget.{ImageView,Button,RelativeLayout,TextView,LinearLayout,RadioGroup,Toast}

import java.lang.Runnable

import org.json.{JSONTokener,JSONObject,JSONArray}

import scala.collection.mutable


object WasuramotiFragment{
  def newInstance(have_to_resume_task:Boolean):WasuramotiFragment={
    val fragment = new WasuramotiFragment
    val args = new Bundle
    args.putBoolean("have_to_resume_task",have_to_resume_task)
    fragment.setArguments(args)
    return fragment
  }
}

class WasuramotiFragment extends Fragment{
  override def onCreateView(inflater:LayoutInflater,parent:ViewGroup,state:Bundle):View = {
    return inflater.inflate(R.layout.main_fragment, parent, false)
  }

  override def onViewCreated(root:View, state:Bundle){
    val wa = getActivity.asInstanceOf[WasuramotiActivity]
    switchViewAndReloadHandler(root)
    wa.setCustomActionBar()
    setLongClickYomiInfo(root)
    setLongClickButton(root)
    if(getArguments.getBoolean("have_to_resume_task")){
      wa.doWhenResume()
    }
  }

  def switchViewAndReloadHandler(root:View){
    val read_button = root.findViewById(R.id.read_button).asInstanceOf[Button]
    val stub = root.findViewById(R.id.yomi_info_stub).asInstanceOf[ViewStub]
    if(YomiInfoUtils.showPoemText){
      stub.inflate()
      read_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.read_button_text_normal))
      read_button.setBackgroundResource(R.drawable.main_button)
    }

    val frag_stub = root.findViewById(R.id.yomi_info_search_stub).asInstanceOf[ViewStub]
    if(frag_stub != null &&
      YomiInfoUtils.showPoemText &&
      Globals.prefs.get.getBoolean("yomi_info_show_info_button",true)
    ){
      frag_stub.inflate()
      val fragment = YomiInfoSearchDialog.newInstance(false,Some(0))
      getChildFragmentManager.beginTransaction.replace(R.id.yomi_info_search_fragment,fragment).commit
    }

    val replay_stub = root.findViewById(R.id.replay_last_button_stub).asInstanceOf[ViewStub]
    if(!YomiInfoUtils.showPoemText && Globals.prefs.get.getBoolean("show_replay_last_button",false)){
      replay_stub.inflate()
      val btn = root.findViewById(R.id.replay_last_button).asInstanceOf[Button]
      btn.setText(Utils.replayButtonText(getResources))
      btn.setOnClickListener(new View.OnClickListener(){
        override def onClick(v:View){
           KarutaPlayUtils.startReplay(getActivity.asInstanceOf[WasuramotiActivity])
        }
      })
    }

    Globals.setButtonText = Some( txt =>
       getActivity.runOnUiThread(new Runnable(){
        override def run(){
          val lines = txt.split("\n")
          val max_chars = lines.map{_.length}.max // TODO: treat japanese characters as two characters.
          if((lines.length >= 4 || max_chars >= 16) && YomiInfoUtils.showPoemText){
            read_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.read_button_text_small))
          }
          read_button.setMinLines(lines.length)
          read_button.setMaxLines(lines.length+1) // We accept exceeding one row
          read_button.setText(txt)
        }
      }))
  }
  def setLongClickYomiInfo(root:View){
    for(id <- Array(R.id.yomi_info_view_prev,R.id.yomi_info_view_cur,R.id.yomi_info_view_next)){
      val view = root.findViewById(id).asInstanceOf[YomiInfoView]
      if(view != null){
        view.setOnLongClickListener(
          new View.OnLongClickListener(){
            override def onLongClick(v:View):Boolean = {
              if(view.cur_num.nonEmpty){
                val dlg = YomiInfoSearchDialog.newInstance(true,view.cur_num)
                dlg.show(getChildFragmentManager,"yomi_info_search")
              }
              return true
            }
          }
        )
      }
    }
  }
  def setLongClickButton(root:View){
    val btn = root.findViewById(R.id.read_button).asInstanceOf[Button]
    if(btn != null){
      btn.setOnLongClickListener(
        if(Globals.prefs.get.getBoolean("skip_on_longclick",false)){
          new View.OnLongClickListener(){
            override def onLongClick(v:View):Boolean = {
              Globals.global_lock.synchronized{
                if(Globals.is_playing){
                  Globals.player.foreach{p=>
                    p.stop()
                    val wa = getActivity.asInstanceOf[WasuramotiActivity]
                    wa.moveToNextFuda()
                    wa.doPlay()
                  }
                }
              }
              return true
            }
          }
        }else{
          null
        }
      )
    }
  }

}
