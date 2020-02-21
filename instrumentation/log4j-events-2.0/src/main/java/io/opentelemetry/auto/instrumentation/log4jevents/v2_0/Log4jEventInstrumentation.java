package io.opentelemetry.auto.instrumentation.log4jevents.v2_0;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

@AutoService(Instrumenter.class)
public class Log4jEventInstrumentation extends Instrumenter.Default {
  public Log4jEventInstrumentation() {
    super("log4j-events");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.logging.log4j.spi.ExtendedLogger"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Log4jEvents"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("logMessage"))
            .and(takesArguments(5))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(2, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(3, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(4, named("java.lang.Throwable"))),
        Log4jEventInstrumentation.class.getName() + "$LogMessageAdvice");
    return transformers;
  }

  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(1) final Level level,
        @Advice.Argument(3) final Message message,
        @Advice.Argument(4) final Throwable t) {
      Log4jEvents.capture(logger, level, message, t);
    }
  }
}
