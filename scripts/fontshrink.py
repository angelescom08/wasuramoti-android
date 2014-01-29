#!/usr/bin/env python
# -*- coding: utf-8 -*-
# create ttf font which contains only subset of characters

KANJI = u"""々阿哀逢安庵伊位移衣稲因院右宇羽雨浦雲影永衛円猿遠塩奥王沖屋乙音嘉夏家河火花過霞我雅
外柿笠潟鎌茅寒干貫間関丸岸岩顔喜基寄岐帰祈紀儀義議菊吉久宮朽居京匡橋興暁業極玉近九具君傾契恵慶経
月兼権見謙賢軒顕元原源言古後光公向后好孝康恒江皇紅綱行降香高告黒今恨左砂菜在坂咲桜三参山散讃残司
士姿子師思紫侍寺慈持治鹿式室実篠若寂弱守手樹周宗秀秋舟住十従重宿出俊春順初渚緒女宵将小少昌昭松消
焼上条色信寝心深神臣親身人壬須吹水崇菅世瀬是勢性成政正清生盛声西誓惜昔石赤摂折節雪絶蝉千宣川撰泉
浅染前曾曽素僧倉早漕相草藻霜則袖尊村太待代大滝誰嘆淡短端知智置筑中仲忠昼朝潮町長鳥津貞定庭摘天田
渡都冬島当等統藤踏同道徳苫敦奈内難二弐日入任忍禰年燃之納能波馬倍白八髪半悲尾浜敏夫富父負部風淵物
分文聞平別辺遍輔暮母峰方法忘房防墨堀本凡磨麻枕末麿妙民夢霧名命明鳴模木目問門夜野友憂有由祐雄夕葉
陽養来頼落乱嵐里陸立流隆竜良涙嶺列恋蓮呂路露和于岑暹杣殷篁蘆躬閨鵲"""

HIRA = u"""あいうえおかがきぎくぐけげこごさざしじすずせぜそぞただちぢっつづてでとどなにぬねの
はばぱひびふぶぷへべほぼまみむめもゃやゅゆょよらりるれろわゐゑをん"""

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
