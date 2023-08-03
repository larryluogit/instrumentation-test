import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.ktor.v1_0")

dependencies {
  library("io.ktor:ktor-server-core:1.0.0")

  implementation(project(":instrumentation:ktor:ktor-common:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:1.0.0")

  latestDepTestLibrary("io.ktor:ktor-server-core:1.+") // see ktor-2.0 module
  latestDepTestLibrary("io.ktor:ktor-server-netty:1.+") // see ktor-2.0 module
}

tasks {
  withType(KotlinCompile::class).configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }
}
