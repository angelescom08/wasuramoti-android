package karuta.hpnpwd.wasuramoti
import android.util.AttributeSet
import android.content.Context
import android.support.v7.preference.{DialogPreference,PreferenceDialogFragmentCompat}
import android.view.{View,LayoutInflater}
import android.widget.{LinearLayout,TextView,SeekBar,Button}
import android.media.audiofx.Equalizer
import android.text.TextUtils

class EqualizerPreferenceFragment extends PreferenceDialogFragmentCompat {
  var root_view = None:Option[View]
  var number_of_bands = None:Option[Short]

  override def onDialogClosed(positiveResult:Boolean){
    val pref = getPreference.asInstanceOf[EqualizerPreference]
    PrefUtils.current_config_dialog = None
    if(positiveResult && number_of_bands.nonEmpty){
      pref.persistString(Utils.equalizerToString(makeSeq()))
    }
    stopAndClear()
  }

  def stopAndClear(){
    Globals.player.foreach{ p => {
      Globals.global_lock.synchronized{
        if(Globals.is_playing){
          p.stop();
        }
        p.equalizer_seq = None
      }
      }}
  }

  def makeSeq():Utils.EqualizerSeq={
    val view = root_view.get
    val seq = number_of_bands.flatMap{ nb =>
      Some(for( i <- 0 until nb ) yield {
        // since the timing of number_of_bands is set and setAllSeekbar differs,
        // there might be a case that we cannot find seekbar which corresponds to band number
        Option(view.findViewWithTag[SeekBar]("equalizer_"+i.toString)).flatMap{ seek =>
          val half = seek.getMax / 2
          val prog = seek.getProgress
          if(prog >= half - 2 && prog <= half + 2){
            None
          }else{
            Some(seek.getProgress.toFloat/seek.getMax.toFloat)
          }
        }
      })
    }.getOrElse(Seq())
    if(seq.forall{_.isEmpty}){
      Seq()
    }else{
      seq
    }
  }

  def setAllSeekbar(x:Int){
    number_of_bands.foreach{ n =>
      for(i <- 0 until n){
        val seek = root_view.get.findViewWithTag[SeekBar]("equalizer_"+i.toString)
        seek.setProgress(x)
      }
    }
  }

  def setButtonListeners(view:View){
    // Play Button
    KarutaPlayUtils.setAudioPlayButton(view,getContext,Some({
        pl => pl.equalizer_seq = Some(makeSeq())
      }))
    // Reset Button
    val rst = view.findViewById[Button](R.id.equalizer_reset)
    rst.setOnClickListener(new View.OnClickListener(){
      override def onClick(v:View){
        val s = view.findViewWithTag[SeekBar]("equalizer_all")
        setAllSeekbar(s.getMax/2)
      }
    })
  }

  def addSeekbars(view:View,equalizer:Equalizer,inflater:LayoutInflater){
    // SeekBar ALL
    val vw = inflater.inflate(R.layout.equalizer_item, null)
    vw.setPadding(0,0,0,16)
    vw.findViewById[SeekBar](R.id.equalizer_seek).setTag("equalizer_all")
    vw.findViewById[TextView](R.id.equalizer_seek_text).setText("ALL")
    val seek = vw.findViewById[SeekBar](R.id.equalizer_seek)
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      override def onProgressChanged(bar:SeekBar,progress:Int,fromUser:Boolean){
        setAllSeekbar(progress)
      }
      override def onStartTrackingTouch(bar:SeekBar){
      }
      override def onStopTrackingTouch(bar:SeekBar){
      }
    })
    view.findViewById[LinearLayout](R.id.equalizer_linear).addView(vw)

    // Each SeekBars
    val prev_seq = Utils.getPrefsEqualizer(getContext)
    for(i <- 0 until number_of_bands.get){
      val vw = inflater.inflate(R.layout.equalizer_item, null)
        val freq = equalizer.getCenterFreq(i.toShort)/1000
        val txt = if (freq < 10000 ){
          freq.toString+"Hz"
        }else{
          (freq/1000).toString+"kHz"
        }
        vw.findViewById[TextView](R.id.equalizer_seek_text).setText(txt)
        val seek = vw.findViewById[SeekBar](R.id.equalizer_seek)
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
        view.findViewById[LinearLayout](R.id.equalizer_linear).addView(vw)
    }
  }

  override def onCreateDialogView(context:Context):View = {
    // we have to access to the current dialog inside KarutaPlayUtils.doAfterConfiguration()
    PrefUtils.current_config_dialog = Some(this)
    val inflater = LayoutInflater.from(context)
    val view = inflater.inflate(R.layout.equalizer, null)

    // getDialog() returns null on onDialogClosed(), so we save view
    root_view = Some(view)

    // onCreateDialogView seems to be called before onDialogClosed when screen rotate, so we also have to stop playing here
    stopAndClear()

    Globals.player match{
      case Some(pl) => {
          try{
            pl.waitDecodeAndUpdateAudioQueue
            pl.makeMusicTrack(pl.audio_queue)
          }catch{
            case e:Exception =>
            view.findViewById[TextView](R.id.equalizer_message).setText(e.getMessage())
            return view
          }
          pl.makeEqualizer(true)
          pl.equalizer match{
            case Some(eqlz) => {
              number_of_bands = Some(eqlz.getNumberOfBands)
              setButtonListeners(view)
              addSeekbars(view,eqlz,inflater)
              eqlz.release()
              pl.equalizer = None
            }
            case None => {
              view.findViewById[TextView](R.id.equalizer_message).setText(
                if(PrefManager.getPrefBool(context,PrefKeyBool.UseOpenSles)){
                  R.string.equalizer_not_available_in_opensles
                }else{
                  R.string.equalizer_error_notsupported
                })
            }
          }

          pl.releaseTrackSetNone() 
      }
      case None => {
        view.findViewById[TextView](R.id.equalizer_message).setText(context.getResources().getString(R.string.player_error_noplay))
      }
    }
    return view
  }

}

class EqualizerPreference(context:Context,attrs:AttributeSet) extends DialogPreference(context,attrs) with CustomPref {
  override def getAbbrValue():String = {
    val equalizer = PrefManager.getPrefStr(context,PrefKeyStr.EffectEqualizerSeq)
    val resid = if(TextUtils.isEmpty(equalizer)){
      R.string.message_disabled
    } else {
      R.string.message_enabled
    }
    context.getResources.getString(resid)
  }
}
