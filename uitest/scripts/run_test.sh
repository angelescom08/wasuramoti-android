#!/bin/sh
JARFILE=wasuramoti_uitest.jar

cd "$(dirname $(readlink -f "$0"))"/..
adb push bin/$JARFILE /data/local/tmp/
adb shell uiautomator runtest $JARFILE -c karuta.hpnpwd.wasuramoti.uitest.LaunchSettings
