
#!/bin/sh
alias am_start_main="adb -e shell am start -a android.intent.action.MAIN"
am_start_main -n com.android.browser/.BrowserActivity
adb -e shell ps | grep -F 'karuta.hpnpwd.wasuramoti' | awk '{print $2}' | xargs adb -e shell kill
