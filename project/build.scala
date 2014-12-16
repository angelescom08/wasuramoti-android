import sbt._
import sbt.Keys._
import android.Keys._
object Build extends android.AutoBuild {
  lazy val mySettings = super.settings ++ android.Plugin.androidBuild ++ Seq (
    name := "wasuramoti",
    version := "0.8.16",
    versionCode := Some(54),
    scalaVersion := "2.10.4",
    platformTarget in Android := "android-19",
    libraryDependencies ++= Seq(
      "com.android.support" % "support-v4" % "19.1.0",
      android.Dependencies.aar("com.android.support" % "appcompat-v7" % "19.1.0")
    ),
    scalacOptions ++= Seq("-unchecked","-deprecation"),
    proguardOptions := Seq(
    "-keep class karuta.hpnpwd.audio.OggVorbisDecoder",
    "-keep class scala.Either",
    "-keep class scala.Function0",
    "-keep class scala.Function1",
    "-keep class scala.Function2",
    "-keep class scala.Function3",
    "-keep class scala.Option",
    "-keep class scala.Tuple2",
    "-keep class scala.Tuple3",
    "-keep class scala.Tuple4",
    "-keep class scala.collection.Seq",
    "-keep class scala.collection.immutable.List",
    "-keep class scala.collection.immutable.Set",
    "-keep class scala.collection.mutable.Buffer",
    "-keep class scala.collection.mutable.HashMap",
    "-keep class scala.collection.mutable.Queue",
    "-keep class scala.collection.mutable.StringBuilder",
    "-keep class scala.Enumeration$Value",
    "-keep class scala.PartialFunction",
    "-keep class scala.runtime.BooleanRef",
    "-keep class scala.runtime.BoxedUnit",
    "-keep class scala.runtime.IntRef",
    "-keep class scala.runtime.FloatRef",
    "-keep class scala.runtime.LongRef",
    "-keep class scala.runtime.ObjectRef",
    "-keep class scala.runtime.VolatileIntRef",
    "-keep class scala.util.matching.Regex",
    "-keep class scala.util.Random",
    "-verbose"
    ),
    useProguard := true
  )
  lazy val root = Project(
      id = "wasuramoti",
      base = file(".")
  ).settings(
    mySettings:_*
  )
}

// vim: set ts=4 sw=4 et:
