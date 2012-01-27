import sbt._

import Keys._
import AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Wasuramoti",
    version := "0.1",
    scalaVersion := "2.9.1",
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
      -keep class scala.Function1
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
