import sbt._
import sbt.Keys._
import android.Keys._
object Build extends android.AutoBuild {
  lazy val mySettings = super.settings ++ android.Plugin.androidBuild ++ Seq (
    name := "wasuramoti",
    version := "0.9.12-beta1",
    versionCode := Some(73),
    scalaVersion := "2.11.7",
    platformTarget in Android := "android-24",
    buildToolsVersion in Android := Some("24.0.1"),
    // See https://github.com/pfn/android-sdk-plugin/issues/88
    sourceGenerators in Compile <<= (sourceGenerators in Compile) (g => Seq(g.last)),
    libraryDependencies ++= Seq(
      "com.android.support" % "support-v4" % "24.1.1",
      android.Dependencies.aar("com.android.support" % "appcompat-v7" % "24.1.1")
    ),
    scalacOptions in Compile ++= Seq(
        "-unchecked",
        "-deprecation",
        "-feature",
        "-Xlint",
        // See `scalac -Y` for more options
        "-Ywarn-dead-code",
        //"-Ywarn-numeric-widen",
        //"-Ywarn-value-discard",
        "-Ywarn-unused",
        "-Ywarn-unused-import"
        ),
    proguardOptions in Android ++= Seq(
    "-keepattributes Signature",
    "-verbose"
    ),
    shrinkResources in Android := true,
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
