/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
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
    T partition = exchange.getIn().getHeader(header, type);
    return partition != null
        ? String.valueOf(partition)
        : exchange.getIn().getHeader(header, String.class);
  }
}
