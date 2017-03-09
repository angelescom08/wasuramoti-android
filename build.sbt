androidBuild

scalaVersion := "2.11.8"
platformTarget in Android := "android-25"
buildToolsVersion in Android := Some("25.0.2")

libraryDependencies ++= Seq(
  "com.android.support" % "support-v4" % "24.1.1",
  android.Dependencies.aar("com.android.support" % "appcompat-v7" % "24.1.1")
  )

javacOptions in Compile ++= Seq("-source","1.7","-target","1.7")
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
  )

useProguard := true
proguardOptions in Android ++= Seq(
  "-keepattributes Signature",
  "-verbose"
  )

shrinkResources in Android := true

// Reference: https://medium.com/@chrisbanes/appcompat-v23-2-age-of-the-vectors-91cbafa87c88
useSupportVectors
