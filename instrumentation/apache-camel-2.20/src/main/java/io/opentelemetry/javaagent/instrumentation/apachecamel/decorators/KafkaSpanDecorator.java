/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class KafkaSpanDecorator extends MessagingSpanDecorator {

  private static final String PARTITION_KEY = "kafka.PARTITION_KEY";
  private static final String PARTITION = "kafka.PARTITION";
  private static final String KEY = "kafka.KEY";
  private static final String TOPIC = "kafka.TOPIC";
  private static final String OFFSET = "kafka.OFFSET";

  public KafkaSpanDecorator() {
    super("kafka");
  }

  @Override
  public String getDestination(Exchange exchange, Endpoint endpoint) {
    String topic = (String) exchange.getIn().getHeader(TOPIC);
    if (topic == null) {
      Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
      topic = queryParameters.get("topic");
    }
    return topic != null ? topic : super.getDestination(exchange, endpoint);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    super.pre(span, exchange, endpoint, camelDirection);

    span.setAttribute(SemanticAttributes.MESSAGING_OPERATION, "process");
    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic");

    String partition = getValue(exchange, PARTITION, Integer.class);
    if (partition != null) {
      span.setAttribute("partition", partition);
    }

    String partitionKey = (String) exchange.getIn().getHeader(PARTITION_KEY);
    if (partitionKey != null) {
      span.setAttribute("partitionKey", partitionKey);
    }

    String key = (String) exchange.getIn().getHeader(KEY);
    if (key != null) {
      span.setAttribute("key", key);
    }

    String offset = getValue(exchange, OFFSET, Long.class);
    if (offset != null) {
      span.setAttribute("offset", offset);
    }
  }

  /**
   * Extracts header value from the exchange for given header
   *
   * @param exchange the {@link Exchange}
   * @param header the header name
   * @param type the class type of the exchange header
   * @return
   */
  private <T> String getValue(final Exchange exchange, final String header, Class<T> type) {
    T value = exchange.getIn().getHeader(header, type);
    return value != null ? String.valueOf(value) : exchange.getIn().getHeader(header, String.class);
  }
}
