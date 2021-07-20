import java.time.Duration

plugins {
  `kotlin-dsl`
  `maven-publish`

  id("com.gradle.plugin-publish")
  id("io.github.gradle-nexus.publish-plugin")
}

group = "io.opentelemetry.instrumentation"
version = "0.1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
  }
}

dependencies {
  implementation("com.google.guava:guava:30.1.1-jre")
  implementation("net.bytebuddy:byte-buddy-gradle-plugin:1.11.2")
  implementation("io.opentelemetry.javaagent:opentelemetry-muzzle:1.4.0-alpha-SNAPSHOT")
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:1.4.0-alpha-SNAPSHOT")
}

pluginBundle {
  website = "https://opentelemetry.io"
  vcsUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation"
  tags = listOf("opentelemetry", "instrumentation")
}

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))
}
