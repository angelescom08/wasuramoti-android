#!/usr/bin/env bash
cd $(dirname $(readlink -f "$0"))
for fn in ../src/main/scala/*;do
  echo "checking $(basename "$fn")"
  imps=$(grep "^import" < "$fn" | grep -o "[A-Z][a-zA-Z0-9]*")
  for i in $imps;do
    if ! (grep -v "^ *//" "$fn" | grep -v "^import" | grep -o "[A-Z][a-zA-Z0-9]*" | sort -u | grep -qF "$i") ;then
      echo -e "  $i"
    fi
  done
done
