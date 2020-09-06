name := "scala-cats-playground"

version := "0.1"

scalaVersion := "2.13.3"

fork := true

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.0.0",
  "org.typelevel" %% "cats-effect" % "2.1.4",
  "co.fs2" %% "fs2-core" % "2.4.0", // For cats 2 and cats-effect 2
  "co.fs2" %% "fs2-io" % "2.4.0",
  "dev.zio" %% "zio" % "1.0.1",
  "dev.zio" %% "zio-interop-cats" % "2.1.4.0",
  "com.github.julien-truffaut" %% "monocle-core"  % "2.0.3",
  "com.github.julien-truffaut" %% "monocle-macro" % "2.0.3",
)

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",       // yes, this is 2 args
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
addCompilerPlugin("org.typelevel"   % "kind-projector_2.13.3"     % "0.11.0")
