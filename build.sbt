name := "gan"

version := "1.0"

scalaVersion := "2.12.4"

//resolvers += Resolver.bintrayIvyRepo("alpeb", "sbt-plugins")
//resolvers += Resolver.typesafeResolver
//resolvers += Resolver.jcenterRepo
//resolvers += Classpaths.typesafeResolver
//resolvers += Seq("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

//libraryDependencies += "org.spire-math" %% "jawn-parser" % "0.11.0"

//libraryDependencies += "org.spire-math" %% "jawn-ast" % "0.11.0"

assemblyMergeStrategy in assembly := { 
case PathList("META-INF","io.netty.versions.properties", xs @ _*) => MergeStrategy.last
case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
// case x => val oldStrategy = (assemblyMergeStrategy in assembly).value oldStrategy(x)
libraryDependencies += "com.paulgoldbaum" %% "scala-influxdb-client" % "0.5.2"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"

//libraryDependencies += "org.asynchttpclient" % "async-http-client" % "2.0.32" exclude("org.jboss.netty","netty") exclude("io.netty","netty")
//libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
//libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"