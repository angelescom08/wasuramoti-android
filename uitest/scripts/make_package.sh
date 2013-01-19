#!/bin/sh
if [ "$ANDROID_SDK_HOME" ] && [ ! "$ANDROID_HOME" ];then
  export ANDROID_HOME="$ANDROID_SDK_HOME"
fi
cd "$(dirname $(readlink -f "$0"))"/..
ant build
