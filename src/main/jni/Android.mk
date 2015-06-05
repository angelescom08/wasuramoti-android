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

LOCAL_MODULE    := libstbvorbis
LOCAL_LDLIBS := -llog -lOpenSLES

LOCAL_C_INCLUDES := $(LOCAL_PATH)

$(warning Value of LOCAL_C_INCLUDES is '$(LOCAL_C_INCLUDES)') 
$(warning Value of LOCAL_CFLAGS is '$(LOCAL_CFLAGS)') 

LOCAL_SRC_FILES := \
	./wav_ogg_file_codec_jni.c \
	./decode_file.c \
	./native-audio-jni.c \
	./stb_vorbis.c

include $(BUILD_SHARED_LIBRARY)
