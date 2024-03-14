val stableVersion = "2.2.0"
val alphaVersion = "2.2.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
