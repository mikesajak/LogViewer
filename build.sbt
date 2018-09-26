
name := "LogViewer"

version := "0.1"

scalaVersion := "2.12.6"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// scalafx (and fxml)
libraryDependencies += "org.scalafx" %% "scalafxml-core-sfx8" % "0.3"
libraryDependencies += "org.scalafx" % "scalafxml-guice-sfx8_2.12" % "0.3"

// https://mvnrepository.com/artifact/org.controlsfx/controlsfx
libraryDependencies += "org.controlsfx" % "controlsfx" % "8.40.14"

// guice dependency injection
libraryDependencies += "com.google.inject" % "guice" % "4.1.0"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.0"

// logging
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

// config
libraryDependencies += "com.typesafe" % "config" % "1.3.1"

// ICU4J https://mvnrepository.com/artifact/com.ibm.icu/icu4j
libraryDependencies += "com.ibm.icu" % "icu4j" % "60.2"

libraryDependencies += "com.beachape" %% "enumeratum" % "1.5.13"

//libraryDependencies += "com.typesafe.akka" % "akka-actor-typed_2.12" % "2.5.9"

// https://mvnrepository.com/artifact/org.codehaus.groovy/groovy-jsr223
libraryDependencies += "org.codehaus.groovy" % "groovy-jsr223" % "2.5.2"

// https://mvnrepository.com/artifact/org.dizitart/nitrite
libraryDependencies += "org.dizitart" % "nitrite" % "3.1.0"


// testing
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"

