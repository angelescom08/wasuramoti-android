package com.hpnpwd.wasuramoti

import fastparse.all._

import fastparse.core.{ParseCtx}
import scala.util.matching.Regex
import scala.io.Source
import java.io.PrintWriter

object BigramGenerator extends App{
  case class Chooser(c:Seq[_])
  case class RegexParser(regex:Regex) extends Parser[String] {
    def parseRec(cfg: ParseCtx, index: Int) = {
      val input = cfg.input
      regex.findPrefixMatchOf(input.substring(index)).map{ m => 
        success(cfg.success, m.group(0), index + m.end, Nil, false)
      }.getOrElse{fail(cfg.failure,index)}
    }
  }

  // Dalvik VM does not support isHan
  val kanji = """(\p{InCJKUnifiedIdeographs}|\p{InCJKSymbolsAndPunctuation})+""".r
  val jap = """(\p{InHiragana}|\p{InCJKUnifiedIdeographs}|\p{InCJKSymbolsAndPunctuation})+""".r
  val hira = """\p{InHiragana}+""".r

  val kword = P(RegexParser(kanji))
  val jword = P(RegexParser(jap))
  val hword = P(RegexParser(hira))
  val jchoice = P("[" ~ jword.rep(1,sep=",") ~ "]").map{Chooser(_)}
  val kchoice = P("[" ~ kword.rep(1,sep=",") ~ "]").map{Chooser(_)}
  val kanjifurigana = P( (kword|kchoice).rep(1) ~ "(" ~ (jword|jchoice).rep(1) ~ ")" ).map{case (x,y) => Chooser(Seq(x,y)) }
  val phrase = P((kanjifurigana|hword|jchoice).rep(1))
  val poem = P(phrase.rep(1,sep=" "))

  def bigramWithPrev(x:String,prev:String):Seq[String] = {
    val last = Option(prev).map{_.last}.getOrElse("")
    x.toSeq.map{_.toString} ++ ((last + x).sliding(2).toSeq)
  }

  def bigramWithPrevAny(x:String,prev:Any):Seq[String] = {
    prev match {
      case p:String => bigramWithPrev(x,p)
      case a:Seq[String] => a.flatMap(bigramWithPrev(x,_))
      case null => bigramWithPrev(x,null)
    }
  }

  def prevMatcher(x:Any):Any = {
    x match {
      case Chooser(c) => c.flatMap{ z=>
        prevMatcher(z) match {
          case a:Seq[_] => a
          case x => Seq(x)
        }
      }
      case a:Seq[_] => prevMatcher(a.last)
      case x:String => x
      case null => null
    }
  }

  def matcher(x:Any,prev:Any):Seq[String] = {
    x match {
      case Chooser(c) => c.flatMap{matcher(_,prev)}
      case a:Seq[_] => traverse(a,prev)
      case x:String => bigramWithPrevAny(x,prevMatcher(prev))
    }
  }
  def traverse(ar:Seq[_],prev:Any):Seq[String] = {
    var p = prev
    ar.flatMap{x => {
      val r = matcher(x,p)
      p = x
      r
    }}
  }

  def printIndex(source:Source,writer:PrintWriter){
    val res = for((poem,index) <- source.getLines.zipWithIndex;
      p <- poem.split(" ");
      val parsed = phrase.parse(p).asInstanceOf[Parsed.Success[_]].value;
      c <- traverse(parsed.asInstanceOf[Seq[_]],null))
      yield (index+1,c)
    
    val rev = res.toSeq.groupBy(_._2).map{case(k,v)=>(k,v.map{_._1}.toSet)}.groupBy(_._2).map{case(k,v)=>(k,v.map{_._1})}

    for((k,v)<-rev.toList.sortBy{case(ar,_)=>(ar.size :: ar.toList.sorted).toIterable}){
      writer.println("    <item>" + k.toList.sorted.mkString(",") + " :: " + v.toList.sorted.mkString("/") + "</item>")
    }
  }

  def main(){
    val writer = new PrintWriter("strings-poem-index.xml")
    writer.println("""<?xml version="1.0" encoding="utf-8"?>""")
    writer.println("""<resources>""")
    writer.println("""  <string-array name="poem_index">""")
    printIndex(Source.fromFile("src/main/res/poem.txt"),writer)
    writer.println("""  </string-array>""")
    writer.println("""  <string-array name="author_index">""")
    printIndex(Source.fromFile("src/main/res/author.txt"),writer)
    writer.println("""  </string-array>""")
    writer.println("""</resources>""")
    writer.close()
  }
  main()

}
