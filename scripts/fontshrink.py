#!/usr/bin/env python
# -*- coding: utf-8 -*-
# create ttf font which contains only subset of characters

HIRA = u'''あいうえおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっつづてでとどなにぬね
のはばぱひびふぶへべほぼまみむめもゃやゅゆょよらりるれろわゐゑをん'''
KANJI = u'''々三上世中丸久之九乱乾二于京人仁今代仲任伊位住侍俊信倉倍僧儀元光入八公具兼内円冬
冲凡出分列初別則前勢匂匡十千半原参友右司吉同名后向君吹呂告周命和咲問喜嘆嘉因在坂
基堀士壬声変夏夕外夜夢大天太夫奈契奥好妙姿子孝宇守安宗定実宣室宮宵家宿寂富寒寝寺
将尊小少尾屋山岐岑岩岸峰島崇嵐嶺川左師帰干平年幾庭庵康式弐弱当待後従徳心忍忘忠思
性恋恒恨恵悲惜慈慶憂成我房手折持摂撰政敏散敦文方日昌明昔春昭是昼時智暁暮暹更曾月
有朝木末本村条杣来松枕染柿桜業極模権樹橋正残殷母民水江沖河治泉法波津流浅浜浦消涙
淡深淵清渚渡源滝漕潟潮濡瀬火焼燃父物猿玉王生田由町白皇盛相知石砂磨祈祐神禰秀秋稲
立竜笠笹等篁篠紀紅納素紫経統絶綱網義羽聞能臣興舟良色花若苫茅草菅菊菜落葉葦蓮藤蝉
行衛衣袖西見親言誓誰謙議讃貞貫賢赤越路踏身躬輔近通逢遍過道遠部都里重野錦鎌長門間
関閨防降院陸陽隆雄雅難雨雪雲霜霧露音順須頼顔顕風養香高髪鳥鳴鹿麻麿黒'''

import sys, fontforge

if len(sys.argv) != 3:
  print "USAGE:%s [input.ttf] [output.ttf]" % sys.argv[0]
  sys.exit(0)

(in_file,out_file) = sys.argv[1:]
font = fontforge.open(in_file)
chars = set(ord(i) for i in HIRA+KANJI if i != "\n")
lst = [-1] + sorted(chars) + [0x10000]
for (x,y) in zip(lst,lst[1:]):
  if y - x <= 1:
    continue
  a = "u%05x"%(x+1)
  b = "u%05x"%(y-1)
  try:
    font.selection.select(("ranges",),a,b)
    font.clear()
  except ValueError,e:
    print e
    print "start =",a,"end =",b

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
  print "Error, %d glypths missing:"%(len(chars)), "".join(unichr(i).encode('utf-8') for i in chars)
else:
  font.generate(out_file,flags=("TeX-table",))

