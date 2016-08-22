#!/bin/bash

# make symbolic link for devices API < 13, which does not support values-swNNNdp, values-wNNNdp

read -d '' SYMS <<EOC
twocolumn w100dp small,x
twocolumn w420dp-port large,normal-land
textsize sw100dp small
textsize sw360dp x
textsize sw480dp large
titlebar w100dp small
titlebar w360dp x
titlebar w480dp large
vertical-weight h100dp x
vertical-weight h500dp large
EOC


ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"/src/main/res


case "$1" in
  make)
    while read res dir tgt; do
      src="values-$dir/dimen-$res.xml"
      while read -d, t; do
        if [ "$t" = x ];then
          tt=values
        else
          tt="values-$t"
        fi
        if [ ! -e "$tt" ];then
          mkdir "$tt"
        fi
        if [ ! -e "$src" ];then
          echo "$src does not exist!"
          exit 1
        fi
        dst="$tt/dimen-$res.xml"
        ln -vsrf "$src" "$dst"
      done <<< "$tgt,"
    done <<< "$SYMS"
    ;;
  clean)
    find values values-* -name 'dimen-*' -type l -printf 'delete: %p\n' -delete
    rmdir --ignore-fail-on-non-empty values-*
    ;;
  check)
    echo "-- broken symlinks --"
    find . -xtype l
    ;;
  *)
    echo "usage $0 [make|clean|check]"
    ;;
esac
