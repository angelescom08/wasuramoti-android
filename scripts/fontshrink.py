#!/usr/bin/env python
# -*- coding: utf-8 -*-
# create ttf font which contains only subset of characters

import os, sys, argparse, fontforge
import xml.etree.ElementTree as ET

POEM_LIST = os.path.join(os.path.dirname(__file__),"..","src","main","res","values","strings-poem-list.xml")
tree = ET.parse(POEM_LIST)
root = tree.getroot()

def get_charset(root, names):
  res = set()
  for name in names:
    ar = root.find("./string-array[@name='%s']" % name)
    if ar is None:
      raise Exception('string-array "' + name + '" not found in "' + POEM_LIST + '"')
    for item in ar.findall('./item'):
      res |= set(item.text)
  return res
  
def get_jpn_chars(root):
  return get_charset(root, ["list_torifuda","list_full","author"]) - set(' ()')
    
def get_eng_chars(root):
  return get_charset(root, ["list_full_en","author_en","list_full_romaji","author_romaji"]) - set('|')

parser = argparse.ArgumentParser(prog='PROG', usage='%(prog)s [options] [input.ttf] [output.ttf]',description='remove characters not in Hyakunin-Isshu from TTF file.')
parser.add_argument('--jpn',action="store_true",help='process japanese font')
parser.add_argument('--eng',action="store_true",help='process english font')
parser.add_argument("rest",nargs=argparse.REMAINDER)
args = parser.parse_args()
chars_ascii = None
if len(args.rest) != 2 or (not args.jpn and not args.eng):
  parser.print_help()
  print("[English Characters]")
  print("".join(sorted(get_eng_chars(root))))
  print("[Japanese Characters]")
  print("".join(sorted(get_jpn_chars(root))))
  sys.exit(0)
elif args.jpn:
  chars_ascii = get_jpn_chars(root)
elif args.eng:
  chars_ascii = get_eng_chars(root)

chars = list(map(ord,chars_ascii))
(in_file,out_file) = args.rest
font = fontforge.open(in_file)
lst = [-1] + sorted(chars) + [0x10000]
for (x,y) in zip(lst,lst[1:]):
  if y - x <= 1:
    continue
  a = "u%05x"%(x+1)
  b = "u%05x"%(y-1)
  try:
    font.selection.select(("ranges",),a,b)
    font.clear()
  except ValueError as e:
    print(e)
    print("start =",a,"end =",b)

for g in font.glyphs():
  if g.encoding <= 0xFFFF:
    # check the existance of glyph
    (xmin,ymin,xmax,ymax) = g.boundingBox()
    if xmax - xmin > 0.0 and ymax - ymin > 0.0:
      chars.remove(g.encoding)
  else:
    # delete fonts outside unicode
    font.selection.select(g)
    font.clear()

if chars:
  print("Warning, %d glypths missing: '%s'"%(len(chars), "','".join(unichr(i).encode('utf-8') for i in chars)))

font.generate(out_file,flags=("TeX-table",))

