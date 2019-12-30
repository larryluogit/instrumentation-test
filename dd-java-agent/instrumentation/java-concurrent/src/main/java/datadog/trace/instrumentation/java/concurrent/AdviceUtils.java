package datadog.trace.instrumentation.java.concurrent;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import lombok.extern.slf4j.Slf4j;

/** Helper utils for Runnable/Callable instrumentation */
@Slf4j
public class AdviceUtils {

  /**
   * Start scope for a given task
   *
   * @param contextStore context storage for task's state
   * @param task task to start scope for
   * @param <T> task's type
   * @return scope if scope was started, or null
   */
  public static <T> AgentScope startTaskScope(
      final ContextStore<T, State> contextStore, final T task) {
    final State state = contextStore.get(task);
    if (state != null) {
      final AgentSpan parentSpan = state.getAndResetParentSpan();
      if (parentSpan != null) {
        return activateSpan(parentSpan, false);
      }
    }
    return null;
  }

  public static void endTaskScope(final AgentScope scope) {
    if (scope != null) {
      scope.close();
    }
  }
}
