scalaVersion := "2.11.7"

resolvers ++= Seq(
Resolver.sonatypeRepo("releases"),
Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
"com.lihaoyi" %% "fastparse" % "0.3.4"
)
