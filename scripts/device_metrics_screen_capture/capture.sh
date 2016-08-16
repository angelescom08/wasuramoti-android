#!/bin/bash

# run the app in variety of devices and take screenshot of it

BASE_PATH=$(dirname $(readlink -f $0))
CAPTURE_PATH="$BASE_PATH"/capture

function adb_shell(){
  adb -e shell -n "$@"
}

function normalize_title(){
  sed -e 's/[^[:alnum:]]\+/_/g' -e 's/_$//' <<< "$1"
}

function density_to_dpi(){
  bc <<< "(160*$1)/1"
}

if ! [ -e "$CAPTURE_PATH" ];then
  mkdir "$CAPTURE_PATH" || exit 1
fi

jq -r < device_metrics.json '[.title,(.width|tostring),(.height|tostring),.density]|join("\t")' | \
while IFS=$'\t' read title width height density; do
  id=$(normalize_title "$title")
  dpi=$(density_to_dpi "$density")
  if ((width > height));then
    ww=$height
    hh=$width
  else
    ww=$width
    hh=$height
  fi
  adb_shell wm size "$ww"x"$hh" || exit 1
  adb_shell wm density "$dpi"
  adb_shell am force-stop karuta.hpnpwd.wasuramoti
  adb_shell am start -a android.intent.action.MAIN -n karuta.hpnpwd.wasuramoti/.WasuramotiActivity
  adb_shell dumpsys window | grep 'mCurConfiguration' > $BASE_PATH/capture/"$id".info
  image=/sdcard/"$id".png
  sleep 2
  adb_shell screencap -p "$image"
  adb -e pull "$image" "$CAPTURE_PATH"/"$id".png 
  adb_shell rm "$image"
done
