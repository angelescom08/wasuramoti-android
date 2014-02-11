#!/usr/bin/ruby20
# Extract only Kanji and Hiragana from file.
# Intended for generating fontshrink.py
# Example: ./extract_chars.rb ../src/main/scala/AllFuda.scala >> ./fontshrink.py
require 'set'
KEYS= {HIRA:"Hiragana",KANJI:"Han"}
res = Hash.new{Set.new}
$<.each{|line|
  line.each_char{|x|
    KEYS.values.each{|w|
      x=~/\p{#{w}}/ and res[w] <<= x
    }
  }
}
KEYS.each{|k,v|
  puts "#{k} = u'''#{res[v].to_a.sort.each_slice(40).map{|x|x.join("")}.join("\n")}'''"
}
