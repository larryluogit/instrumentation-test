package io.opentelemetry.auto.instrumentation.rmi.context.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload.GETTER;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload.TRACER;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.CONTEXT_CALL_ID;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;

import io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload;
import io.opentelemetry.trace.SpanContext;
import java.io.IOException;
import java.io.ObjectInput;
import java.rmi.Remote;
import java.rmi.server.RemoteCall;
import sun.rmi.server.Dispatcher;
import sun.rmi.transport.Target;

/**
 * ContextDispatcher is responsible for handling both initial context propagation check call and
 * following call which carries payload
 *
 * <p>Context propagation check is only expected not to throw any exception, hinting to the client
 * that its communicating with an instrumented server. Non instrumented server would've thrown
 * UnknownObjectException
 *
 * <p>Because caching of the result after first call on a connection, only payload calls are
 * expected
 */
public class ContextDispatcher implements Dispatcher {
  private static final ContextDispatcher CONTEXT_DISPATCHER = new ContextDispatcher();
  private static final NoopRemote NOOP_REMOTE = new NoopRemote();

  public static Target newDispatcherTarget() {
    return new Target(NOOP_REMOTE, CONTEXT_DISPATCHER, NOOP_REMOTE, CONTEXT_CALL_ID, false);
  }

  @Override
  public void dispatch(final Remote obj, final RemoteCall call) throws IOException {
    final ObjectInput in = call.getInputStream();
    final int operationId = in.readInt();
    in.readLong(); // skip 8 bytes

    if (PROPAGATOR.isOperationWithPayload(operationId)) {
      final ContextPayload payload = ContextPayload.read(in);
      if (payload != null) {
        SpanContext context = null;
        try {
          context = TRACER.getHttpTextFormat().extract(payload, GETTER);
        } catch (final IllegalArgumentException e) {
          // couldn't extract a context
        }
        THREAD_LOCAL_CONTEXT.set(context);
      }
    }

    // send result stream the client is expecting
    call.getResultStream(true);

    // release held streams to allow next call to continue
    call.releaseInputStream();
    call.releaseOutputStream();
    call.done();
  }

  public static class NoopRemote implements Remote {}
}
