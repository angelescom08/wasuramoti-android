#!/bin/sh
ROOT=$(hg root)
alias am_start_main="adb -e shell am start -a android.intent.action.MAIN"
adb -e uninstall karuta.hpnpwd.wasuramoti
adb -e install ${ROOT}/wasuramoti-android-*/*.apk
am_start_main -n karuta.hpnpwd.wasuramoti/.WasuramotiActivity
#am_start_main -n karuta.hpnpwd.wasuramoti/.ConfActivity
