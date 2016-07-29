#!/bin/sh
# before running this script, you have to place some files as follows: 
# ${READER_DIR}/<name>/<name>_%03d_{1,2}.ogg - audio file
# ${READER_DIR}/<name>-licence - copyright and licence of audio file

ROOT="$(git rev-parse --show-toplevel)"
READER_DIR="${ROOT}/players/"
READER_ASSETS="${ROOT}/src/main/assets/reader"
TARGET_BASE="${ROOT}"

function die(){
  echo "$@"
  exit
}

function copy_license(){
  ROOT="$1"
  TARGET_DIR="$2"
  AUDIO_LICENCE="$3"
  mkdir -p "${TARGET_DIR}"
  for fn in README README.ja; do
    suffix=${fn#README}
    cat "${ROOT}/${fn}" | sed -e "/__WRITE_AUDIO_LICENSE_HERE__/{
    r ${AUDIO_LICENCE}${suffix}
    d
    }" > "${TARGET_DIR}/../$fn"
  done
  cp "${ROOT}/license/"* "${TARGET_DIR}"
}

if [ $# -eq 0 ];then
  echo "USAGE: $0 [reader]"
  exit
fi
if [ ! -d "${READER_DIR}/$1" ];then
  echo directory "${READER_DIR}/$1" not found
  exit
fi

for f in "${READER_ASSETS}"/*; do
  if [ -h "${f}" ];then
    unlink "${f}"
  fi
done
mkdir -p "${READER_ASSETS}"
ln -s "${READER_DIR}"/$1 "${READER_ASSETS}"
cd ${ROOT}

AUDIO_LICENCE=${READER_DIR}/${1}-license
copy_license "$ROOT" "$ROOT/src/main/assets/license/" "$AUDIO_LICENCE"
# sbt android:package-release generates jarsigned and zipaligned apk
sbt android:package-release || die compile FAILED

APK_FILE=$(ls ${ROOT}/target/android-bin/wasuramoti-release.apk)
if [ $? -ne 0 ];then
  exit
fi
READER=$(unzip -t ${APK_FILE} | grep -o -m 1 'testing: assets/reader/[^/]*/' | sed -re 's!.*/([^/]*)/$!\1!')
if [ "${READER}" != "$1" ];then
  echo "assets in apk and argument differ: '$1' != '${READER}'"
  exit
fi
if [ ! "${READER}" ];then
  echo no assets/reader found in ${APK_FILE}
  exit
fi

VERSION=$(grep -oP 'android:versionName=".*?"' < ${ROOT}/target/android-bin/AndroidManifest.xml | cut -d = -f 2 | tr -d '"')
TARGET_DIR="${TARGET_BASE}/wasuramoti-android-${READER}-${VERSION}"
if [ -e "${TARGET_DIR}" ];then
  rm -rf "${TARGET_DIR}"
fi

TARGET_FILE="${TARGET_DIR}/wasuramoti-${READER}-${VERSION}.apk"

copy_license "$ROOT" "$TARGET_DIR/license/" "$AUDIO_LICENCE"

cp -f "${APK_FILE}" "${TARGET_FILE}"
echo "copied to ${TARGET_FILE}"
echo "all done."

