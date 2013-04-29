import sbt._

import Keys._
import AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Wasuramoti",
    version := "0.7.5",
    scalaVersion := "2.9.2",
    platformName in Android := "android-10"
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    AndroidNdk.settings ++
    TypedResources.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "change-me",
      libraryDependencies ++= Seq( "org.scalatest" %% "scalatest" % "1.6.1" % "test"),
      proguardOption in Android := """
      -keep class karuta.hpnpwd.audio.OggVorbisDecoder
      -keep class scala.Either
      -keep class scala.Function0
      -keep class scala.Function1
      -keep class scala.Function2
      -keep class scala.Function3
      -keep class scala.Option
      -keep class scala.Tuple2
      -keep class scala.Tuple3
      -keep class scala.Tuple4
      -keep class scala.collection.Seq
      -keep class scala.collection.immutable.List
      -keep class scala.collection.immutable.Set
      -keep class scala.collection.mutable.Buffer
      -keep class scala.collection.mutable.HashMap
      -keep class scala.collection.mutable.Queue
      -keep class scala.collection.mutable.StringBuilder
      -keep class scala.Enumeration$Value
      -keep class scala.PartialFunction
      -keep class scala.runtime.BooleanRef
      -keep class scala.runtime.BoxedUnit
      -keep class scala.runtime.IntRef
      -keep class scala.runtime.FloatRef
      -keep class scala.runtime.LongRef
      -keep class scala.runtime.ObjectRef
      -keep class scala.runtime.VolatileIntRef
      -keep class scala.util.matching.Regex
      -keep class scala.util.Random
      -verbose
      """,
      useProguard in Android := true,
      scalacOptions ++= Seq("-deprecation")
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "Wasuramoti",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++ AndroidTest.androidSettings ++ Seq (
      name := "WasuramotiTests"
    )
  ) dependsOn main
}
