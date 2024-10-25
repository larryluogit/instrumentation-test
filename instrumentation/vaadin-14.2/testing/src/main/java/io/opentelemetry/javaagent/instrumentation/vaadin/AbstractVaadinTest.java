/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vaadin.flow.server.Version;
import com.vaadin.flow.spring.annotation.EnableVaadin;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public abstract class AbstractVaadinTest
    extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  private static final Logger logger = LoggerFactory.getLogger(AbstractVaadinTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private BrowserWebDriverContainer<?> browser;

  @SpringBootApplication
  @EnableVaadin("test.vaadin")
  static class TestApplication {
    public TestApplication() {}

    static ConfigurableApplicationContext start(int port, String contextPath) {
      SpringApplication app = new SpringApplication(TestApplication.class);
      Map<String, Object> properties = new HashMap<>();
      properties.put("server.port", port);
      properties.put("server.servlet.contextPath", contextPath);
      properties.put("server.error.include-message", "always");
      app.setDefaultProperties(properties);
      return app.run();
    }
  }

  @BeforeAll
  protected void setup() throws URISyntaxException {
    startServer();

    Testcontainers.exposeHostPorts(port);

    browser =
        new BrowserWebDriverContainer<>()
            .withCapabilities(new ChromeOptions())
            .withLogConsumer(new Slf4jLogConsumer(logger));
    browser.start();

    address = new URI("http://host.testcontainers.internal:" + port + getContextPath() + "/");
  }

  @AfterAll
  void cleanup() {
    cleanupServer();
    if (browser != null) {
      browser.stop();
    }
  }

  protected void prepareVaadinBaseDir(File baseDir) {}

  protected static void copyClasspathResource(String resource, Path destination) {
    if (!Files.exists(destination)) {
      try (InputStream inputStream = AbstractVaadinTest.class.getResourceAsStream(resource)) {
        Files.copy(inputStream, destination);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    File baseDir = new File("build/vaadin-" + Version.getFullVersion());
    baseDir.mkdirs();
    prepareVaadinBaseDir(baseDir);

    // set directory for files generated by vaadin development mode
    // by default these go to project root
    System.setProperty("vaadin.project.basedir", baseDir.getAbsolutePath());
    return TestApplication.start(port, getContextPath());
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  public String getContextPath() {
    return "/xyz";
  }

  private void waitForStart(RemoteWebDriver driver) {
    // In development mode ui javascript is compiled when application starts
    // this involves downloading and installing npm and a bunch of packages
    // and running webpack. Wait until all of this is done before starting test.
    driver.manage().timeouts().implicitlyWait(Duration.ofMinutes(3));
    driver.get(address.resolve("main").toString());
    // wait for page to load
    driver.findElement(By.id("main.label"));
    // clear traces so test would start from clean state
    testing.clearData();

    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
  }

  private RemoteWebDriver getWebDriver() {
    return new RemoteWebDriver(browser.getSeleniumAddress(), new ChromeOptions(), false);
  }

  abstract void assertFirstRequest();

  private void assertButtonClick() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              assertThat(traces.get(0))
                  .satisfies(
                      spans -> {
                        assertThat(spans.get(0))
                            .hasName("POST " + getContextPath() + "/main")
                            .hasNoParent()
                            .hasKind(SpanKind.SERVER);
                        assertThat(spans.get(1))
                            .hasName("SpringVaadinServletService.handleRequest")
                            .hasParent(spans.get(0))
                            .hasKind(SpanKind.INTERNAL);
                        // we don't assert all the handler spans as these vary between
                        // vaadin versions
                        assertThat(spans.get(spans.size() - 2))
                            .hasName("UidlRequestHandler.handleRequest")
                            .hasParent(spans.get(1))
                            .hasKind(SpanKind.INTERNAL);
                        assertThat(spans.get(spans.size() - 1))
                            .hasName("EventRpcHandler.handle/click")
                            .hasParent(spans.get(spans.size() - 2))
                            .hasKind(SpanKind.INTERNAL);
                      });
            });
  }

  @Test
  public void navigateFromMainToOtherView() {
    RemoteWebDriver driver = getWebDriver();
    waitForStart(driver);

    // fetch the test page
    driver.get(address.resolve("main").toString());

    // wait for page to load
    assertThat(driver.findElement(By.id("main.label")).getText()).isEqualTo("Main view");
    assertFirstRequest();

    testing.clearData();

    // click a button to trigger calling java code in MainView
    driver.findElement(By.id("main.button")).click();

    // wait for page to load
    assertThat(driver.findElement(By.id("other.label")).getText()).isEqualTo("Other view");
    assertButtonClick();

    driver.close();
  }
}