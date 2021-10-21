package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;

class RockerMqProducerExperimentalAttributeExtractor implements
    AttributesExtractor<SendMessageContext, SendMessageContext> {
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_TAGS = AttributeKey
      .stringKey("messaging.rocketmq.tags");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_BROKER_ADDRESS = AttributeKey
      .stringKey("messaging.rocketmq.broker_address");
  private static final AttributeKey<String> MESSAGING_ROCKETMQ_SEND_RESULT = AttributeKey
      .stringKey("messaging.rocketmq.send_result");

  @Override
  public void onStart(AttributesBuilder attributes, SendMessageContext request) {
    set(attributes, MESSAGING_ROCKETMQ_TAGS, request.getMessage().getTags());
    set(attributes, MESSAGING_ROCKETMQ_BROKER_ADDRESS, request.getBrokerAddr());

  }

  @Override
  public void onEnd(AttributesBuilder attributes, SendMessageContext request,
      @Nullable SendMessageContext response, @Nullable Throwable error) {
    if (response != null) {
      set(attributes, MESSAGING_ROCKETMQ_SEND_RESULT,
          response.getSendResult().getSendStatus().name());
    }
  }
}
