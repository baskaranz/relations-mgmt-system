name := "akka-quickstart-scala"

version := "1.0"

scalaVersion := "2.13.1"

lazy val akkaVersion = "2.5.23"

resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.mockito"       % "mockito-all" % "1.10.19" % "test",
  "org.scalatest" % "scalatest_2.13" % "3.0.8" % "test",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2",
)

fork in Test := true