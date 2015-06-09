package karuta.hpnpwd.wasuramoti
import _root_.android.util.AttributeSet
import _root_.android.content.Context
import _root_.android.preference.DialogPreference
import _root_.android.view.{View,LayoutInflater}
import _root_.android.widget.{LinearLayout,TextView,SeekBar,Button}
import _root_.android.media.audiofx.Equalizer

class EqualizerPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs){
  var root_view = None:Option[View]
  var number_of_bands = None:Option[Short]

  override def onDialogClosed(positiveResult:Boolean){
    Globals.current_config_dialog = None
    if(positiveResult && !number_of_bands.isEmpty){
      persistString(Utils.equalizerToString(makeSeq()))
    }
    Globals.player.foreach{ p => {
      Globals.global_lock.synchronized{
        if(Globals.is_playing){
          p.stop();
        }
        p.equalizer_seq = None
      }
      }}
    super.onDialogClosed(positiveResult)
  }
  def makeSeq():Utils.EqualizerSeq={
    val view = root_view.get
    val seq = number_of_bands.flatMap{ nb =>
      Some(for( i <- 0 until nb ) yield {
        val seek = view.findViewWithTag("equalizer_"+i.toString).asInstanceOf[SeekBar]
        val half = seek.getMax / 2
        val prog = seek.getProgress
        if(prog >= half - 2 && prog <= half + 2){
          None
        }else{
          Some(seek.getProgress.toFloat/seek.getMax.toFloat)
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
    KarutaPlayUtils.setAudioPlayButton(view,context,Some({
        pl => pl.equalizer_seq = Some(makeSeq())
      }))
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
    vw.setPadding(0,0,0,16)
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
            Globals.global_lock.synchronized{
              if(Globals.is_playing){
                Globals.player.foreach{pl=>{
                  // TODO: If `Play` button is pressed at the same time as SeekBar has changed,
                  //       the KarutaPlayer.music_track would be None at this row.
                  //       To avoid it, we have to ensure that music_track is non empty here.
                  pl.makeEqualizer(true)
                  pl.equalizer.foreach{ e =>
                    val Array(min_eq,max_eq) = e.getBandLevelRange
                    val r = progress.toDouble/seek.getMax.toDouble
                    e.setEnabled(true)
                    e.setBandLevel(i.toShort,(min_eq+(max_eq-min_eq)*r).toShort)
                  }
                }}
              }
            }
          }
          override def onStartTrackingTouch(bar:SeekBar){
          }
          override def onStopTrackingTouch(bar:SeekBar){
          }
        })
        seek.setTag("equalizer_"+i.toString)
        if(i < prev_seq.seq.length){
          prev_seq.seq(i).foreach{ x => seek.setProgress((x*seek.getMax).toInt) }
        }
        view.findViewById(R.id.equalizer_linear).asInstanceOf[LinearLayout].addView(vw)
    }
  }

  override def onCreateDialogView():View = {
    super.onCreateDialogView()
    // we have to access to the current dialog inside KarutaPlayUtils.doAfterConfiguration()
    Globals.current_config_dialog = Some(this)

    val inflater = LayoutInflater.from(context)
    val view = inflater.inflate(R.layout.equalizer, null)

    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)

    Globals.player match{
      case Some(pl) => {
          try{
            pl.makeMusicTrack()
          }catch{
            case e:OggDecodeFailException =>
            view.findViewById(R.id.equalizer_message).asInstanceOf[TextView].setText(e.getMessage())
            return view
          }
          pl.makeEqualizer(true)
          pl.equalizer match{
            case Some(eqlz) => {
              number_of_bands = Some(eqlz.getNumberOfBands)
              set_button_listeners(view)
              add_seekbars(view,eqlz,inflater)
              eqlz.release()
              pl.equalizer = None
            }
            case None => {
              view.findViewById(R.id.equalizer_message).asInstanceOf[TextView].setText(
                if(Globals.prefs.get.getBoolean("use_opensles",false)){
                  R.string.equalizer_not_available_in_opensles
                }else{
                  R.string.equalizer_error_notsupported
                })
            }
          }

          pl.releaseTrackSetNone() 
      }
      case None => {
        view.findViewById(R.id.equalizer_message).asInstanceOf[TextView].setText(context.getResources().getString(R.string.player_error_noplay))
      }
    }
    return view
  }
}
