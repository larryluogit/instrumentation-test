/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.result.view.RedirectView;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;

@SpringBootApplication
class TestWebfluxSpringBootApp {

  static ConfigurableApplicationContext start(int port) {
    Properties props = new Properties();
    props.put("server.port", port);

    SpringApplication app = new SpringApplication(TestWebfluxSpringBootApp.class);
    app.setDefaultProperties(props);
    return app.run();
  }

  @Bean
  WebFilter telemetryFilter() {
    return SpringWebfluxServerTelemetry.builder(GlobalOpenTelemetry.get())
        .setCapturedRequestHeaders(singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
        .build()
        .createWebFilter();
  }

  @Controller
  static class TestController {

    @RequestMapping("/success")
    @ResponseBody
    Flux<String> success() {
      return Flux.defer(() -> Flux.just(controller(SUCCESS, SUCCESS::getBody)));
    }

    @RequestMapping("/query")
    @ResponseBody
    String query_param(@RequestParam("some") String param) {
      return controller(QUERY_PARAM, () -> "some=" + param);
    }

    @RequestMapping("/redirect")
    @ResponseBody
    RedirectView redirect() {
      return controller(REDIRECT, () -> new RedirectView(REDIRECT.getBody()));
    }

    @RequestMapping("/error-status")
    Flux<ResponseEntity<String>> error() {
      return Flux.defer(
          () ->
              Flux.just(
                  controller(
                      ERROR,
                      () ->
                          new ResponseEntity<>(
                              ERROR.getBody(), HttpStatus.valueOf(ERROR.getStatus())))));
    }

    @RequestMapping("/exception")
    Flux<ResponseEntity<String>> exception() {
      return Flux.just(
          controller(
              EXCEPTION,
              () -> {
                throw new RuntimeException(EXCEPTION.getBody());
              }));
    }

    @RequestMapping("/captureHeaders")
    ResponseEntity<String> capture_headers(
        @RequestHeader("X-Test-Request") String testRequestHeader) {
      return controller(
          CAPTURE_HEADERS,
          () ->
              ResponseEntity.ok()
                  .header("X-Test-Response", testRequestHeader)
                  .body(CAPTURE_HEADERS.getBody()));
    }

    @RequestMapping("/path/{id}/param")
    @ResponseBody
    String path_param(@PathVariable("id") int id) {
      return controller(PATH_PARAM, () -> String.valueOf(id));
    }

    @RequestMapping("/child")
    @ResponseBody
    String indexed_child(@RequestParam("id") String id) {
      return controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(name -> name.equals("id") ? id : null);
            return INDEXED_CHILD.getBody();
          });
    }

    @ExceptionHandler
    ResponseEntity<String> handleException(Throwable throwable) {
      return new ResponseEntity<>(throwable.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
