#!/usr/bin/python3
# decode all the ogg files in PLAYER_DIR to wav file
from ctypes import *
import os, re, glob, wave, tempfile

PLAYER_DIR = "../../players/"
OUT_DIR = "./out"

class StbVorbisInfo(Structure):
    _fields_ = [
            ("sample_rate", c_uint),
            ("channels", c_int),
            ("setup_memory_required", c_uint),
            ("setup_temp_memory_required", c_uint),
            ("temp_memory_required", c_uint),
            ("max_frame_size", c_int)
    ]

lib = cdll.LoadLibrary("./libs/libstbvorbis.so")

for path in glob.glob(PLAYER_DIR+"/**/*.ogg"):
    info = StbVorbisInfo()
    tmp = tempfile.NamedTemporaryFile()
    # string of python3 is unicode. you have to encode it to ascii before passing it
    lib.decode_file(None,path.encode('ascii'),tmp.name.encode('ascii'),pointer(info))
    out = os.path.join(OUT_DIR, os.path.basename(os.path.dirname(path)) , re.sub(r"\.ogg$",".wav",os.path.basename(path)))
    if not os.path.exists(os.path.dirname(out)):
        os.makedirs(os.path.dirname(out))
    raw = open(tmp.name,"rb")
    wv = wave.open(out,"wb")
    wv.setframerate(info.sample_rate)
    wv.setnchannels(info.channels)
    wv.setsampwidth(2)
    wv.writeframes(raw.read())
    wv.close()
    raw.close()
    print("decoded" + path + " to " + out)
