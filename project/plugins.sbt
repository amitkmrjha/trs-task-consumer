addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.0.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.3")
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.16.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.11.1"
