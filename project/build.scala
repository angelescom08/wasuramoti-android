import sbt._
import sbt.Keys._
import android.Keys._
object Build extends android.AutoBuild {
  lazy val mySettings = super.settings ++ android.Plugin.androidBuild ++ Seq (
    name := "wasuramoti",
    version := "0.8.20",
    versionCode := Some(58),
    scalaVersion := "2.11.5",
    platformTarget in Android := "android-19",
    buildToolsVersion in Android := Some("19.1.0"),
    // See https://github.com/pfn/android-sdk-plugin/issues/88
    sourceGenerators in Compile <<= (sourceGenerators in Compile) (g => Seq(g.last)),
    // Support library 21.0.3 has a bug, https://code.google.com/p/android/issues/detail?id=91424
    // So we use older version 19.1.0 instead.
    libraryDependencies ++= Seq(
      "com.android.support" % "support-v4" % "19.1.0",
      android.Dependencies.aar("com.android.support" % "appcompat-v7" % "19.1.0")
    ),
    scalacOptions in Compile ++= Seq(
        "-unchecked",
        "-deprecation",
        "-feature",
        "-Xlint",
        // "-Xfatal-warnings", // treat warning as error
        "-Ywarn-dead-code",
        //"-Ywarn-numeric-widen",
        //"-Ywarn-value-discard",
        "-Ywarn-unused",
        "-Ywarn-unused-import"
        ),
    proguardOptions in Android ++= Seq(
    "-dontwarn scala.collection.**", // see http://blog.scaloid.org/2014_10_01_archive.html
    "-keepattributes Signature",
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
