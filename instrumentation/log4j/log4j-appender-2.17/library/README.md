# Log4j2 Appender

This module provides a Log4j2 [appender](https://logging.apache.org/log4j/2.x/manual/appenders.html)
which forwards Log4j2 log events to the
[OpenTelemetry Log SDK](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk/logs).

To use it, add the following modules to your application's classpath.

Replace `OPENTELEMETRY_VERSION` with the latest
stable [release](https://search.maven.org/search?q=g:io.opentelemetry.instrumentation).

**Maven**

```xml

<dependencies>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-appender-2.17</artifactId>
    <version>OPENTELEMETRY_VERSION</version>
  </dependency>
</dependencies>
```

**Gradle**

```kotlin
dependencies {
  runtimeOnly("io.opentelemetry.instrumentation:opentelemetry-log4j-appender-2.17:OPENTELEMETRY_VERSION")
}
```

The following demonstrates how you might configure the appender in your `log4j.xml` configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" packages="io.opentelemetry.instrumentation.log4j.appender.v2_17">
  <Appenders>
    <Console name="Console" target="SYSTEM_OUT">
      <PatternLayout
          pattern="%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} trace_id: %X{trace_id} span_id: %X{span_id} trace_flags: %X{trace_flags} - %msg%n"/>
    </Console>
    <OpenTelemetry name="OpenTelemetryAppender"/>
  </Appenders>
  <Loggers>
    <Root>
      <AppenderRef ref="OpenTelemetryAppender" level="All"/>
      <AppenderRef ref="Console" level="All"/>
    </Root>
  </Loggers>
</Configuration>
```

Next, associate the `OpenTelemetryAppender` configured via `log4j2.xml` with
an `SdkLogEmitterProvider` in your application:

```
SdkLogEmitterProvider logEmitterProvider =
  SdkLogEmitterProvider.builder()
    .setResource(Resource.create(...))
    .addLogProcessor(...)
    .build();
OpenTelemetryAppender.setSdkLogEmitterProvider(logEmitterProvider);
```

In this example Log4j2 log events will be sent to both the console appender and
the `OpenTelemetryAppender`, which will drop the logs until
`OpenTelemetryAppender.setSdkLogEmitterProvider(..)` is called. Once initialized, logs will be
emitted to a `LogEmitter` obtained from the `SdkLogEmitterProvider`.
