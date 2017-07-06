#!/usr/bin/env node
// requires: https://github.com/gka/chroma.js
// $ npm install chroma-js
const chroma = require('chroma-js')
const input = process.argv[2]
if(!input){
  console.log("USAGE: ./this_script [COLOR]")
  process.exit()
}
const color = chroma(input)
const lumi = color.luminance()
console.log(color.luminance(1-lumi).hex())
