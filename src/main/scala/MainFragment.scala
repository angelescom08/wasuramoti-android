package karuta.hpnpwd.wasuramoti

import android.app.Activity
import android.os.{Bundle,Handler}
import android.support.v4.app.Fragment
import android.util.TypedValue
import android.view.{View,ViewStub,LayoutInflater,ViewGroup}
import android.widget.{Button,LinearLayout}

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
    val was = getActivity.asInstanceOf[WasuramotiActivity]
    switchViewAndReloadHandler(was,root)
    was.setCustomActionBar()
    setLongClickYomiInfo(root)
    if(getArguments.getBoolean("have_to_resume_task")){
      was.doWhenResume()
    }
  }

  def switchViewAndReloadHandler(was:WasuramotiActivity, root:View){
    val read_button = root.findViewById(R.id.read_button).asInstanceOf[Button]
    val stub = root.findViewById(R.id.yomi_info_stub).asInstanceOf[ViewStub]
    if(YomiInfoUtils.showPoemText){
      stub.inflate()
      read_button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources.getDimension(R.dimen.read_button_text_normal))
      read_button.setBackgroundResource(R.drawable.main_button)
    }

    val frag_stub = root.findViewById(R.id.yomi_info_search_stub).asInstanceOf[ViewStub]
    if(frag_stub != null &&
      YomiInfoUtils.showPoemText){
      frag_stub.inflate()
      val fragment = YomiInfoSearchDialog.newInstance(false,Some(0))
      getChildFragmentManager.beginTransaction.replace(R.id.yomi_info_search_fragment,fragment).commit
    }

    val show_replay_last_button = Globals.prefs.get.getBoolean("show_replay_last_button",false) 
    
    val show_skip_button = Globals.prefs.get.getBoolean("show_skip_button",false)
    if(!YomiInfoUtils.showPoemText && (show_replay_last_button || show_skip_button)){
      val sub_buttons = root.findViewById(R.id.sub_buttons_stub).asInstanceOf[ViewStub].inflate()
      if(show_replay_last_button){
        val inflated = sub_buttons.findViewById(R.id.replay_last_button_stub).asInstanceOf[ViewStub].inflate()
        val btn = inflated.findViewById(R.id.replay_last_button).asInstanceOf[Button]
        btn.setText(Utils.replayButtonText(getResources))
        btn.setOnClickListener(new View.OnClickListener(){
          override def onClick(v:View){
             KarutaPlayUtils.startReplay(was)
          }
        })
      }
      if(show_skip_button){
        val inflated = sub_buttons.findViewById(R.id.skip_button_stub).asInstanceOf[ViewStub].inflate()
        val btn = inflated.findViewById(R.id.skip_button).asInstanceOf[Button]
        btn.setOnClickListener(new View.OnClickListener(){
          override def onClick(v:View){
             KarutaPlayUtils.skipToNext(was)
          }
        })
        // add margin
        // TODO: for API >= 11 you can use android:showDividers and android:divider instead
        // http://stackoverflow.com/questions/4259467/in-android-how-to-make-space-between-linearlayout-children
        if(show_replay_last_button){
          val container = sub_buttons.asInstanceOf[LinearLayout]
          val params = inflated.getLayoutParams.asInstanceOf[LinearLayout.LayoutParams]
          val margin = Utils.dpToPx(4).toInt // 4dp
          container.getOrientation match {
            case LinearLayout.HORIZONTAL =>
              params.setMargins(margin,0,0,0) //left margin
            case LinearLayout.VERTICAL =>
              params.setMargins(0,margin,0,0) // top margin
          }
          inflated.setLayoutParams(params)
        }
      }
    }

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
}
