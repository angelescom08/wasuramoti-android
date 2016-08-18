#!/bin/bash

# https://design.google.com/devices/
# run
# $ ./download_device_metrics.sh > device_metrics.json

curl 'https://design.google.com/devices/devices_data.min.js' \
| tail -n 1 | sed -e 's/;$//' -e 's/^window.devicesData =//' \
| jq '.feed.entry | .[] | 
  select(.platform == "Android"  and .formfactor != "Watch") |
  {title:.title."$t",
   width:.pxscreenw|tonumber,
   height:.pxscreenh|gsub("[^0-9]+";"")|tonumber,
   density:.density,
   screen_inch:.inscreend
  }'
