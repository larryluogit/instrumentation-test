/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.instrumentationNames;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.isTracingEnabled;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.spring.batch.chunk.StepBuilderInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.item.ChunkOrientedTaskletInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.item.JsrChunkProcessorInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.item.SimpleChunkProcessorInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.item.SimpleChunkProviderInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.job.JobBuilderHelperInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.job.JobFactoryBeanInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.job.JobParserJobFactoryBeanInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.step.StepBuilderHelperInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringBatchInstrumentationModule extends InstrumentationModule {
  public SpringBatchInstrumentationModule() {
    super(instrumentationNames());
  }

  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // JSR-352 Batch API
    return hasClassesNamed("org.springframework.batch.core.jsr.launch.JsrJobOperator");
  }

  @Override
  protected Map<String, String> contextStore() {
    Map<String, String> context = new HashMap<>();
    String contextAndScope =
        "io.opentelemetry.javaagent.instrumentation.spring.batch.ContextAndScope";
    context.put("org.springframework.batch.core.JobExecution", contextAndScope);
    context.put("org.springframework.batch.core.StepExecution", contextAndScope);
    context.put("org.springframework.batch.core.scope.context.ChunkContext", contextAndScope);
    return context;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> instrumentations = new ArrayList<>();
    if (isTracingEnabled("job", true)) {
      instrumentations.add(new JobBuilderHelperInstrumentation());
      instrumentations.add(new JobFactoryBeanInstrumentation());
      instrumentations.add(new JobParserJobFactoryBeanInstrumentation());
    }
    if (isTracingEnabled("step", true)) {
      instrumentations.add(new StepBuilderHelperInstrumentation());
    }
    if (isTracingEnabled("chunk", true)) {
      instrumentations.add(new StepBuilderInstrumentation());
    }
    // the item level instrumentation is very chatty so it's disabled by default
    if (isTracingEnabled("item", false)) {
      instrumentations.add(new ChunkOrientedTaskletInstrumentation());
      instrumentations.add(new SimpleChunkProviderInstrumentation());
      instrumentations.add(new SimpleChunkProcessorInstrumentation());
      instrumentations.add(new JsrChunkProcessorInstrumentation());
    }
    return instrumentations;
  }

  protected boolean defaultEnabled() {
    // TODO: replace this with an experimental flag
    return false;
  }
}
