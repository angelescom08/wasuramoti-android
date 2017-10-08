androidBuild

scalaVersion := "2.11.8"
platformTarget in Android := "android-25"
buildToolsVersion in Android := Some("25.0.2")

// Relinker is in distributed using jcenter
resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.robolectric" % "robolectric" % "3.2.2" % Test,
  "junit" % "junit" % "4.12" % Test,
  "com.getkeepsafe.relinker" % "relinker" % "1.2.2",
  "com.android.support" % "support-v4" % "24.1.1",
  aar("com.android.support" % "appcompat-v7" % "24.1.1")
  )

// Required for testing
unmanagedClasspath in Test ++= (bootClasspath in Android).value

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

