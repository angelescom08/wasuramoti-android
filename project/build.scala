import sbt._
import Keys._
import Defaults._
import sbtandroid.AndroidPlugin._

object AndroidBuild extends Build{
  lazy val globalSettings = Seq (
    name := "Wasuramoti",
    version := "0.8.2",
    versionCode := 40,
    scalaVersion := "2.10.2",
    platformName := "android-16",
    keyalias := "techkey",
    libraryDependencies ++= Seq(
      "com.android.support" % "support-v4" % "19.0.1",
      aarlib("com.android.support" % "appcompat-v7" % "19.0.1")
    ),
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

  lazy val main = AndroidProject(
    "Wasuramoti",
    file("."),
    settings = globalSettings 
  )
  lazy val tests = AndroidTestProject(
    "WasuramotiTests",
    file("tests"),
    settings = globalSettings
  )
}
