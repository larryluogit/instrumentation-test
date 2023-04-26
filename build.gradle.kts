import java.time.Duration

plugins {
  id("idea")

  id("com.github.ben-manes.versions")
  id("io.github.gradle-nexus.publish-plugin")
  id("otel.spotless-conventions")
}

apply(from = "version.gradle.kts")

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

  transitionCheckOptions {
    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    maxRetries.set(300)
    delayBetween.set(Duration.ofSeconds(10))
  }
}

description = "OpenTelemetry instrumentations for Java"

// total of 4 partitions (see modulo 4 below)
var testPartition = (project.findProperty("testPartition") as String?)?.toInt()
if (testPartition != null) {
  var testPartitionCounter = 0
  subprojects {
    // relying on predictable ordering of subprojects
    // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
    // since we are splitting these tasks across different github action jobs
    val enabled = testPartitionCounter++ % 4 == testPartition
    tasks.withType<Test>().configureEach {
      this.enabled = enabled
    }
  }
}

allprojects {
  repositories {
//    maven {
//      setUrl("https://maven.aliyun.com/nexus/content/groups/public/")
//    }
    mavenCentral()
    mavenLocal()
    jcenter {
      setUrl("https://jcenter.bintray.com/")
    }
    google()
  }
}
