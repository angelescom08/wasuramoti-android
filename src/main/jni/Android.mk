# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libvorbis
#LOCAL_C_INCLUDES := d:/work/android/ogg/libogg/include/ogg

LIBOGG_PATH := $(LOCAL_PATH)/libogg-1.3.0
LIBVORBIS_PATH := $(LOCAL_PATH)/libvorbis-1.3.2/

LOCAL_C_INCLUDES := $(LIBOGG_PATH)/include \
$(LIBVORBIS_PATH)/include \
$(LIBVORBIS_PATH)/lib

#LOCAL_CFLAGS := -D__ANDROID__ 
#-I$(LOCAL_PATH)

$(warning Value of LOCAL_C_INCLUDES is '$(LOCAL_C_INCLUDES)') 
$(warning Value of LOCAL_CFLAGS is '$(LOCAL_CFLAGS)') 

LOCAL_SRC_FILES := \
./wav_ogg_file_codec_jni.c \
./decode_file.c \
../$(LIBVORBIS_PATH)/lib/analysis.c \
../$(LIBVORBIS_PATH)/lib/registry.c \
../$(LIBVORBIS_PATH)/lib/vorbisenc.c \
../$(LIBOGG_PATH)/src/bitwise.c \
../$(LIBOGG_PATH)/src/framing.c \
../$(LIBVORBIS_PATH)/lib/bitrate.c  \
../$(LIBVORBIS_PATH)/lib/block.c  \
../$(LIBVORBIS_PATH)/lib/codebook.c  \
../$(LIBVORBIS_PATH)/lib/envelope.c  \
../$(LIBVORBIS_PATH)/lib/floor0.c \
../$(LIBVORBIS_PATH)/lib/floor1.c  \
../$(LIBVORBIS_PATH)/lib/info.c  \
../$(LIBVORBIS_PATH)/lib/lookup.c \
../$(LIBVORBIS_PATH)/lib/lpc.c \
../$(LIBVORBIS_PATH)/lib/lsp.c \
../$(LIBVORBIS_PATH)/lib/mapping0.c \
../$(LIBVORBIS_PATH)/lib/mdct.c \
../$(LIBVORBIS_PATH)/lib/psy.c \
../$(LIBVORBIS_PATH)/lib/res0.c \
../$(LIBVORBIS_PATH)/lib/sharedbook.c \
../$(LIBVORBIS_PATH)/lib/smallft.c \
../$(LIBVORBIS_PATH)/lib/synthesis.c \
../$(LIBVORBIS_PATH)/lib/vorbisfile.c \
../$(LIBVORBIS_PATH)/lib/window.c 

#../lib/barkmel.c  \
#../lib/tone.c \
#../lib/psytune.c \

include $(BUILD_SHARED_LIBRARY)
