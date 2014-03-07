// This will fail to bootstrap if the artifact can't be fetched, in which case you'll
// need to comment this out (as well as all the references to the plugin) and publish a working binary
// locally.
addSbtPlugin("coop.plausible" %% "sbt-keychain" % "1.0-SNAPSHOT")