

resolvers += Resolver.url("bintray-sbt-plugins", url("https://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += Resolver.bintrayRepo("sbt", "sbt-plugin-releases") 

//resolvers += Resolver.sonatypeRepo("releases")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

