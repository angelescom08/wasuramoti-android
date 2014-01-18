#!/bin/sh
ROOT="$(git rev-parse --show-toplevel)"
tput smul; echo ${ROOT}/src/main/AndroidManifest.xml; tput rmul
grep -o 'android:version[A-Za-z]*="[0-9.]*"' ${ROOT}/src/main/AndroidManifest.xml | sed -e 's/^/  /'
tput smul; echo ${ROOT}/README; tput rmul
grep 'App Version' ${ROOT}/README | sed -e 's/^ */  /'
tput smul; echo ${ROOT}/project/build.scala; tput rmul
grep '^ *version\(Code\)\? *:= *' ${ROOT}/project/build.scala | sed -e 's/^ */  /'
echo
tput bold;echo 'dont forget to execute "jarsigner" and "zipalign" to *.apk package';tput sgr0
