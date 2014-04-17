#!/bin/bash
JARFILE=wasuramoti_uitest.jar
APPID=karuta.hpnpwd.wasuramoti

cd "$(dirname $(readlink -f "$0"))"/..
adb push bin/$JARFILE /data/local/tmp/ || exit 1
LOG_DIR="$HOME/wasuramoti_play_log"
mkdir $LOG_DIR
for i in {1..1000}; do
  adb shell am start -n $APPID/$APPID.WasuramotiActivity
  sleep 2
  adb logcat -c
  adb logcat > $LOG_DIR/log_$i.txt &
  LAST_JOB=$!
  adb shell uiautomator runtest $JARFILE -c karuta.hpnpwd.wasuramoti.uitest.PlayAll
  kill -INT $LAST_JOB
  sleep 1
  kill $LAST_JOB
done
