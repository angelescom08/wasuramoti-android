#!/bin/bash
DATADIR=$HOME/wasuramoti_screenshot
JARFILE=wasuramoti_uitest.jar
APPID=karuta.hpnpwd.wasuramoti
PREF=${APPID}_preferences.xml
SHARED_PREF_DIR="/data/data/$APPID/shared_prefs"
FONTS_MAIN="Default asset:kouzan-gyosho.ttf asset:kouzan-sousho.ttf asset:tfont-kaisho.ttf"
FONTS_FURIGANA="None Default asset:tfont-kaisho.ttf"
FURIGANA_WIDTH="4 6 8"

cd "$(dirname $(readlink -f "$0"))"/..
adb push bin/$JARFILE /data/local/tmp/ || exit 1
mkdir $DATADIR || exit 1
counter=0
REMOTE_USER=$(adb shell su -c "stat -c '%u' $SHARED_PREF_DIR")
if [ $? -ne 0 ];then
  exit 1
fi
for font in $FONTS_MAIN;do
for font_furigana in $FONTS_FURIGANA;do
for furigana_width in $FURIGANA_WIDTH;do
for author in true false;do
for kami in true false;do
for simo in true false;do
  if [ $font_furigana = "None" ] && [ $furigana_width -ne 4 ];then
    continue
  fi
  if [ $author = "false" ] && [ $kami = "false" ] && [ $simo = "false" ];then
    continue
  fi
  ((counter++))
  counter0=$(printf '%04d' $counter)
  adb shell am force-stop "$APPID" || exit 1
  cat >$DATADIR/pref_$counter0.xml <<HEREDOC
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="show_yomi_info">$font</string>
<string name="yomi_info_furigana">$font_furigana</string>
<int name="yomi_info_furigana_width" value="$furigana_width" />
<boolean name="yomi_info_author" value="$author" />
<boolean name="yomi_info_kami" value="$kami" />
<boolean name="yomi_info_simo" value="$simo" />
<string name="dimlock_minutes">5</string>
<string name="wav_fadein_kami">0.1</string>
<string name="read_order_each">CUR2_NEXT1</string>
<boolean name="hardware_accelerate" value="true" />
<string name="wav_span_simokami">1.0</string>
<string name="reader_path">INT:inaba</string>
<string name="wav_threashold">0.01</string>
<int name="preference_version" value="2" />
<string name="read_order">SHUFFLE</string>
<string name="wav_fadeout_simo">0.2</string>
<string name="wav_begin_read">0.5</string>
</map>
HEREDOC
  adb push $DATADIR/pref_$counter0.xml /data/local/tmp/$PREF
  adb shell su -c "rm $SHARED_PREF_DIR/wasuramoti.pref.xml;"\
  "rm $SHARED_PREF_DIR/$PREF;" \
  "mv /data/local/tmp/$PREF $SHARED_PREF_DIR/;" \
  "chmod 660 $SHARED_PREF_DIR/$PREF;" \
  "chown $REMOTE_USER:$REMOTE_USER $SHARED_PREF_DIR/$PREF;" || exit 1
  adb shell am start -n $APPID/$APPID.WasuramotiActivity || exit 1
  sleep 3
  adb logcat -c
  adb shell uiautomator runtest $JARFILE -c karuta.hpnpwd.wasuramoti.uitest.TakeScreenshotAll || exit 1
  adb logcat -d > $DATADIR/logcat_$counter0.log
  SCDIR=$DATADIR/screenshot_$counter0
  mkdir $SCDIR 
  adb pull /data/local/tmp/wasuramoti_screenshot/ $SCDIR/
  adb shell rm -rf /data/local/tmp/wasuramoti_screenshot/
done
done
done
done
done
done
