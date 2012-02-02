#!/bin/sh
ROOT=$(hg root)
tput smul; echo ${ROOT}/README; tput rmul
grep 'App Version' ${ROOT}/README | sed -e 's/^ */  /'
tput smul; echo ${ROOT}/AndroidManifest.xml; tput rmul
grep -o 'android:version[A-Za-z]*="[0-9.]*"' ${ROOT}/src/main/AndroidManifest.xml | sed -e 's/^/  /'
tput smul; echo ${ROOT}/project/build.scala; tput rmul
grep '^ *version *:= *' ${ROOT}/project/build.scala | sed -e 's/^ */  /'
