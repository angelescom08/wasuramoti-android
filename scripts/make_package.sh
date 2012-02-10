#!/bin/sh
ROOT="$(hg root)"
KEYSTORE="${ROOT}/keystore/hpnpwd.keystore"
READER_DIR="${ROOT}/players/"
READER_ASSETS="${ROOT}/src/main/assets/reader"
TARGET_BASE="${ROOT}"

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
ln -s "${READER_DIR}"/$1 "${READER_ASSETS}"
cd ${ROOT}

sbt android:package-release

APK_FILE=$(ls ${ROOT}/target/wasuramoti-*.apk)
if [ $? -ne 0 ];then
  exit
fi
READER=$(unzip -t ${APK_FILE} | grep -o -m 1 'testing: assets/reader/[^/]*/' | sed -re 's!.*/([^/]*)/$!\1!')

if [ ! "${READER}" ];then
  echo no assets/reader found in ${APK_FILE}
  exit
fi

VERSION=$(echo "${APK_FILE}" | sed -re 's!.*/wasuramoti-(.*).apk!\1!')
TARGET_DIR="${TARGET_BASE}/wasuramoti-android-${READER}-${VERSION}"
mkdir "${TARGET_DIR}"
jarsigner -verbose -keystore "${KEYSTORE}" "${APK_FILE}" techkey
TARGET_FILE="${TARGET_DIR}/wasuramoti-${READER}-${VERSION}.apk"
zipalign -v 4 "${APK_FILE}" "${TARGET_FILE}"
echo "${TARGET_FILE} created"
cp "${ROOT}/README" "${TARGET_DIR}"
cp "${ROOT}/src/main/jni/libvorbis-"*"/COPYING" "${TARGET_DIR}"
