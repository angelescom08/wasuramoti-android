#!/bin/bash
DATADIR=$HOME/wasuramoti_screenshot
JARFILE=wasuramoti_uitest.jar
APPID=karuta.hpnpwd.wasuramoti
PREF=${APPID}_preferences.xml
SHARED_PREF_DIR="/data/data/$APPID/shared_prefs"
FONTS_MAIN="asset:tfont-kaisho.ttf"
FONTS_FURIGANA="asset:tfont-kaisho.ttf"
#LANGS="Japanese Romaji English"
LANGS="Japanese Romaji"
FURIGANA_WIDTH="5"

cd "$(dirname $(readlink -f "$0"))"/..
adb push bin/$JARFILE /data/local/tmp/ || exit 1
mkdir $DATADIR || exit 1
REMOTE_USER=$(adb shell su -c "stat -c '%u' $SHARED_PREF_DIR")
if [ $? -ne 0 ];then
  exit 1
fi
for default_lang in $LANGS;do
  counter0="$default_lang"
  adb shell am force-stop "$APPID" || exit 1
  cat >$DATADIR/pref_$counter0.xml <<HEREDOC
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<string name="show_yomi_info">asset:tfont-kaisho.ttf</string>
<boolean name="yomi_info_author" value="true" />
<boolean name="yomi_info_kami" value="true" />
<boolean name="yomi_info_simo" value="true" />
<boolean name="yomi_info_furigana_show" value="true" />
<string name="read_order">POEM_NUM</string>
<string name="yomi_info_default_lang">$default_lang</string>
<string name="intended_use">study</string>
</map>
HEREDOC
  adb push $DATADIR/pref_$counter0.xml /data/local/tmp/$PREF
  # I do'nt know why, but we cannot do each command in one `su`.
  adb shell su -c "rm -f $SHARED_PREF_DIR/wasuramoti.pref.xml;" || exit 1
  adb shell su -c "rm $SHARED_PREF_DIR/$PREF;" || exit 1
  adb shell su -c "mv /data/local/tmp/$PREF $SHARED_PREF_DIR/;" || exit 1
  adb shell su -c "chmod 660 $SHARED_PREF_DIR/$PREF;" || exit 1
  adb shell su -c "chown $REMOTE_USER:$REMOTE_USER $SHARED_PREF_DIR/$PREF;" || exit 1
  adb shell am start -n $APPID/$APPID.WasuramotiActivity || exit 1
  # wait for closing "super user request" toast
  sleep 10
  adb logcat -c
  adb shell uiautomator runtest $JARFILE -c karuta.hpnpwd.wasuramoti.uitest.TakeScreenshotAll || exit 1
  adb logcat -d > $DATADIR/logcat_$counter0.log
  SCDIR=$DATADIR/screenshot_$counter0
  mkdir $SCDIR 
  adb pull /data/local/tmp/wasuramoti_screenshot/ $SCDIR/
  adb shell rm -rf /data/local/tmp/wasuramoti_screenshot/
done
