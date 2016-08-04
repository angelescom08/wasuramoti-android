#!/bin/sh
# このファイルには日本語が含まれていてutf-8です
ROOT="$(git rev-parse --show-toplevel)"
tput smul; echo ${ROOT}/src/main/AndroidManifest.xml; tput rmul
grep -oP 'android:version[A-Za-z]*=".*?"' ${ROOT}/src/main/AndroidManifest.xml | sed -e 's/^/  /'
tput smul; echo ${ROOT}/README; tput rmul
grep 'App Version' ${ROOT}/README | sed -e 's/^ */  /'
tput smul; echo ${ROOT}/README.ja; tput rmul
grep 'バージョン:' ${ROOT}/README.ja | sed -e 's/^ */  /'
