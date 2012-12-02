package karuta.hpnpwd.wasuramoti
import _root_.android.util.AttributeSet
import _root_.android.content.Context
import _root_.android.preference.DialogPreference
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{LinearLayout,TextView,SeekBar,Button}
import _root_.android.os.Handler
import _root_.android.media.audiofx.Equalizer

class EqualizerPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var root_view = None:Option[View]
  var number_of_bands = None:Option[Short]

  override def onDialogClosed(positiveResult:Boolean){
    super.onDialogClosed(positiveResult)
    if(positiveResult && !number_of_bands.isEmpty){
      val editor = getEditor()
      val seq = makeSeq()
      editor.putString(getKey(),Utils.serializeToString(seq))
      editor.commit()
    }
    Globals.player.foreach{ p => {p.stop(); p.equalizer_seq = None} }
  }
  def makeSeq():Utils.EqualizerSeq={
    val view = root_view.get
    val seq = number_of_bands.flatMap{ nb =>
      Some(for( i <- 0 until nb ) yield {
        val seek = view.findViewWithTag("equalizer_"+i.toString).asInstanceOf[SeekBar]
        if(seek.getProgress == seek.getMax / 2){
          None
        }else{
          Some(seek.getProgress.toDouble/seek.getMax.toDouble)
        }
      })
    }.getOrElse(Seq())
    if(seq.forall{_.isEmpty}){
      Seq()
    }else{
      seq
    }
  }

  def set_all_seekbar(x:Int){ 
    number_of_bands.foreach{ n => 
      for(i <- 0 until n){
        val seek = root_view.get.findViewWithTag("equalizer_"+i.toString).asInstanceOf[SeekBar]
        seek.setProgress(x)
      }
    }
  }

  def set_button_listeners(view:View){
    // Play Button
    val btn = view.findViewById(R.id.equalizer_play).asInstanceOf[Button]
    val handler = new Handler()
    btn.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        Globals.player.foreach{ pl => {
          if(Globals.is_playing){
            pl.stop()
            btn.setText(context.getResources().getString(R.string.equalizer_play))
          }else{
            pl.equalizer_seq = Some(makeSeq())
            pl.play( _ => {
              handler.post(new Runnable(){
                override def run(){
                  btn.setText(context.getResources().getString(R.string.equalizer_play))
                }
              })
            })
            btn.setText(context.getResources().getString(R.string.equalizer_stop))
          }
        }}
      }
    })
    // Reset Button
    val rst = view.findViewById(R.id.equalizer_reset).asInstanceOf[Button]
    rst.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val s = view.findViewWithTag("equalizer_all").asInstanceOf[SeekBar]
        set_all_seekbar(s.getMax/2)
      }
    })
  }

  def add_seekbars(view:View,equalizer:Equalizer,inflater:LayoutInflater){
    // SeekBar ALL
    val vw = inflater.inflate(R.layout.equalizer_item, null)
    vw.setPadding(0,0,0,20)
    vw.findViewById(R.id.equalizer_seek).asInstanceOf[SeekBar].setTag("equalizer_all")
    vw.findViewById(R.id.equalizer_seek_text).asInstanceOf[TextView].setText("ALL")
    val seek = vw.findViewById(R.id.equalizer_seek).asInstanceOf[SeekBar]
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      override def onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
        set_all_seekbar(progress)
      }
      override def onStartTrackingTouch(bar:SeekBar){
      }
      override def onStopTrackingTouch(bar:SeekBar){
      }
    })
    view.findViewById(R.id.equalizer_linear).asInstanceOf[LinearLayout].addView(vw)

    // Each SeekBars
    val prev_seq = Utils.getPrefsEqualizer()
    for(i <- 0 until number_of_bands.get){
      val vw = inflater.inflate(R.layout.equalizer_item, null)
        val freq = equalizer.getCenterFreq(i.toShort)/1000
        val txt = if (freq < 10000 ){
          freq.toString+"Hz"
        }else{
          (freq/1000).toString+"kHz"
        }
        vw.findViewById(R.id.equalizer_seek_text).asInstanceOf[TextView].setText(txt)
        val seek = vw.findViewById(R.id.equalizer_seek).asInstanceOf[SeekBar]
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
          override def onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
            if(Globals.is_playing){
              Globals.player.foreach{_.equalizer.foreach{ e =>
                val Array(min_eq,max_eq) = e.getBandLevelRange
                val r = progress.toDouble/seek.getMax.toDouble
                e.setEnabled(true)
                e.setBandLevel(i.toShort,(min_eq+(max_eq-min_eq)*r).toShort)
              }}
            }
          }
          override def onStartTrackingTouch(bar:SeekBar){
          }
          override def onStopTrackingTouch(bar:SeekBar){
          }
        })
        seek.setTag("equalizer_"+i.toString)
        if(i < prev_seq.length){
          prev_seq(i).foreach{ x => seek.setProgress((x*seek.getMax).toInt) }
        }
        view.findViewById(R.id.equalizer_linear).asInstanceOf[LinearLayout].addView(vw)
    }
  }

  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    val inflater = LayoutInflater.from(context)
    val view = inflater.inflate(R.layout.equalizer, null)

    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)

    Globals.player match{ 
      case Some(pl) => {
          val (audio_track,equalizer) = AudioHelper.makeAudioTrack(pl.getFirstDecoder(),true) 
          equalizer match{
            case Some(eqlz) => {
              number_of_bands = Some(eqlz.getNumberOfBands)
              set_button_listeners(view)
              add_seekbars(view,eqlz,inflater)
              // Don't forget to release equalizer or you get "java.lang.UnsupportedOperationException: Effect library not loaded"
              eqlz.release()
              audio_track.release()
            }
            case None => {
              view.findViewById(R.id.equalizer_message).asInstanceOf[TextView].setText(context.getResources().getString(R.string.equalizer_error_notsupported))
            }
          }
      }
      case None => {
        view.findViewById(R.id.equalizer_message).asInstanceOf[TextView].setText(context.getResources().getString(R.string.equalizer_error_noplay))
      }
    }
    return view
  }
}
