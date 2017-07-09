androidBuild

scalaVersion := "2.11.8"
platformTarget in Android := "android-25"
buildToolsVersion in Android := Some("25.0.2")
resolvers ++= Seq(
  Resolver.jcenterRepo,
  "Google Maven Repository" at "https://maven.google.com"
)

val supportLibVer = "25.4.0"

// Until the folling bug is fixed by upstream, we use patched support library instead
// https://stackoverflow.com/questions/32070670/preferencefragmentcompat-requires-preferencetheme-to-be-set
// https://github.com/Gericop/Android-Support-Preference-V7-Fix

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.robolectric" % "robolectric" % "3.2.2" % Test,
  "junit" % "junit" % "4.12" % Test,
  "com.android.support" % "support-v4" % supportLibVer,
  "com.takisoft.fix" % "preference-v7" % "25.4.0.3",
  android.Dependencies.aar("com.android.support" % "appcompat-v7" % supportLibVer)
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

