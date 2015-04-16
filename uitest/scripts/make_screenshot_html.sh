#!/bin/bash
DATADIR=$HOME/wasuramoti_screenshot
for dir in $DATADIR/screenshot_*; do
  cd $dir
  mkdir large
  mkdir small
  left=$(grep left screen_rect.txt | cut -d ':' -f 2)
  right=$(grep right screen_rect.txt | cut -d ':' -f 2)
  top=$(grep top screen_rect.txt | cut -d ':' -f 2)
  bottom=$(grep bottom screen_rect.txt | cut -d ':' -f 2)
  exec 3> index.html
  echo >&3 "<!DOCTYPE html>"
  echo >&3 "<html><head><title>$(basename $dir)</title></head><body>"
  for fn in wsrmt_*.png; do
    base="${fn#wsrmt_}"
    crop_repage="-crop $((bottom-top))x$((right-left))+${left}+${top} +repage"
    convert $crop_repage "$fn" PNG8:large/"$base"
    convert $crop_repage -resize 40% "$fn" PNG8:small/"$base" 
    echo >&3 "<a href='large/$base'><img src='small/$base' /></a>"
  done
  echo >&3 "</body></html>"
  exec 3>&-
done
echo "now you can exec "'`'"find $DATADIR -name 'wsrmt_*.png' -delete"'`'""
