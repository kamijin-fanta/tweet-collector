name := "twi"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= {
  val akkaVersion = "2.4.16"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.danielasfregola" %% "twitter4s" % "4.0",
//    "com.danielasfregola" %% "twitter4s" % "3.0",
    "org.http4s" %% "http4s-core" % "0.15.2",
    "org.http4s" %% "http4s-blaze-client" % "0.15.2",
    "org.scalaj" %% "scalaj-http" % "2.3.0",
    "com.github.nscala-time" %% "nscala-time" % "2.16.0",
    "de.knutwalker" %% "typed-actors" % "1.6.0",
    "de.knutwalker" %% "typed-actors-creator" % "1.6.0",
//    "com.ibm" %% "couchdb-scala" % "0.7.2",
    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
    "ch.qos.logback" % "logback-classic" % "1.1.8"
  )
}

fork in run := true
connectInput in run := true
