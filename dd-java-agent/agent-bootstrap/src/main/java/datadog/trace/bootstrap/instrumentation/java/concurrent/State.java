package datadog.trace.bootstrap.instrumentation.java.concurrent;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.instrumentation.api.AgentScope;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class State {

  public static ContextStore.Factory<State> FACTORY =
      new ContextStore.Factory<State>() {
        @Override
        public State create() {
          return new State();
        }
      };

  private final AtomicReference<AgentScope.Continuation> continuationRef =
      new AtomicReference<>(null);

  private State() {}

  public boolean setContinuation(final AgentScope.Continuation continuation) {
    final boolean result = continuationRef.compareAndSet(null, continuation);
    if (!result) {
      log.debug(
          "Failed to set continuation because another continuation is already set {}: new: {}, old: {}",
          this,
          continuation,
          continuationRef.get());
    }
    return result;
  }

  public void closeContinuation() {
    continuationRef.set(null);
  }

  public AgentScope.Continuation getAndResetContinuation() {
    return continuationRef.getAndSet(null);
  }
}
