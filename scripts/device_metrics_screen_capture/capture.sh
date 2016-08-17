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

function resize_or_error(){
  local sz="$1"
  adb_shell wm size "$sz"
  local actual physical
  while read p _ s; do
    case "$p" in
      Physical)
        physical="$s";;
      Override)
        actual="$s";;
    esac
  done < <(adb_shell wm size | tr -d $'\r')
  if ! [ "$actual" ];then
    actual="$physical"
  fi
  if [ "$actual" != "$sz" ];then
    echo "Requested size does not match actual override size: $sz != $actual"
    echo "Check that emulator as enough physical size."
    echo "The maximum override size is twice of physical size."
    exit 1
  fi
}

function density_or_error(){
  local density="$1"
  local dpi=$(density_to_dpi "$density")
  adb_shell wm density "$dpi"
  local actual physical
  while read p _ s; do
    case "$p" in
      Physical)
        physical="$s";;
      Override)
        actual="$s";;
    esac
  done < <(adb_shell wm density | tr -d $'\r')
  if ! [ "$actual" ];then
    actual="$physical"
  fi
  if [ "$actual" != "$dpi" ];then
    echo "Requested density does not match actual override density: $dpi != $actual"
    echo "Cannot set density."
    exit 1
  fi
}

if ! [ -e "$CAPTURE_PATH" ];then
  mkdir "$CAPTURE_PATH" || exit 1
fi

# disable accelerometer controlling rotation
adb_shell content insert --uri content://settings/system --bind name:s:accelerometer_rotation --bind value:i:0 || exit 1


jq -r < device_metrics.json '[.title,(.width|tostring),(.height|tostring),.density]|join("\t")' | \
while IFS=$'\t' read title width height density; do
  id=$(normalize_title "$title")
  if ((width > height));then
    ww=$height
    hh=$width
  else
    ww=$width
    hh=$height
  fi
  resize_or_error "${ww}x${hh}"
  density_or_error "$density"

  adb_shell am force-stop karuta.hpnpwd.wasuramoti
  adb_shell am start -a android.intent.action.MAIN -n karuta.hpnpwd.wasuramoti/.WasuramotiActivity
  for rotation in port land; do
    prefix="$id"-$rotation
    image=/sdcard/"$prefix".png
    # rotate device
    if [ $rotation = port ];then
      adb_shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:0
    else
      adb_shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:1
    fi
    sleep 2
    adb_shell screencap -p "$image"
    adb_shell dumpsys window | grep 'mCurConfiguration' > $BASE_PATH/capture/"$prefix".info
    adb -e pull "$image" "$CAPTURE_PATH"/"$prefix".png 
    adb_shell rm "$image"
  done
done
