val AkkaVersion = "2.6.15"
val AlpakkaKafkaVersion = "2.1.1"
val AkkaManagementVersion = "1.1.1"
val AkkaHttpVersion = "10.2.6"
val LogbackVersion = "1.2.3"

ThisBuild / scalaVersion := "2.13.5"
ThisBuild / organization := "com.lightbend.akka.samples"
Compile / scalacOptions   ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
Compile / javacOptions   ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
Test / testOptions   += Tests.Argument("-oDF")
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
ThisBuild / resolvers ++= Seq(
  "Akka Snapshots" at "https://repo.akka.io/snapshots",
  Resolver.bintrayRepo("akka", "snapshots")
)

Global / cancelable := true // ctrl-c

lazy val `trs-task-consumer` = project.in(file(".")).aggregate(producer, processor, client)

lazy val client = project
  .in(file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-discovery" % AkkaVersion))

lazy val processor = project
  .in(file("processor"))
  .enablePlugins(Cinnamon,AkkaGrpcPlugin, JavaAgent)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(
    run / cinnamon := true,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream-kafka-cluster-sharding" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "3.0.3",
      "org.postgresql" % "postgresql" % "42.2.23",
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test) ++ CinnamonDependency

  )

lazy val producer = project
  .in(file("producer"))
  .settings(Compile / PB.targets   := Seq(scalapb.gen() -> (Compile / sourceManaged ).value))
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))

lazy val CinnamonDependency = Seq(
  // Use Coda Hale Metrics
  Cinnamon.library.cinnamonCHMetrics,
  // Use Akka instrumentation
  Cinnamon.library.cinnamonAkka,
  Cinnamon.library.cinnamonAkkaTyped,
  // Use Akka Persistence instrumentation
  Cinnamon.library.cinnamonAkkaPersistence,
  // Use Akka Projection instrumentation
  Cinnamon.library.cinnamonAkkaProjection,
  // Use Akka HTTP instrumentation
  Cinnamon.library.cinnamonAkkaHttp,
  // Use Akka gRPC instrumentation
  Cinnamon.library.cinnamonAkkaGrpc,

  Cinnamon.library.cinnamonJvmMetricsProducer,

  Cinnamon.library.cinnamonCHMetricsElasticsearchReporter,

  Cinnamon.library.cinnamonSlf4jEvents
)



