/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.serverless.proxy.model.Headers;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HeadersFactory {

  private static final Logger log = LoggerFactory.getLogger(HeadersFactory.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    OBJECT_MAPPER.registerModule(new AfterburnerModule());
  }

  static Headers ofStream(InputStream inputStream) {
    try (JsonParser jParser = new JsonFactory().createParser(inputStream)) {
      while (jParser.nextToken() != null) {
        String name = jParser.getCurrentName();
        if ("multiValueHeaders".equalsIgnoreCase(name)) {
          jParser.nextToken();
          return OBJECT_MAPPER.readValue(jParser, Headers.class);
        }
      }
    } catch (Exception e) {
      log.debug("Could not get headers from request, ", e);
    }
    return null;
  }
}
