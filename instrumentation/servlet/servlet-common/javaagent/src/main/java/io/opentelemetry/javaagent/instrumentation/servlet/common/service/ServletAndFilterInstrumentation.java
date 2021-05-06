/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.service;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServletAndFilterInstrumentation implements TypeInstrumentation {
  private final String basePackageName;
  private final String adviceClassName;
  private final String servletInitAdviceClassName;
  private final String filterInitAdviceClassName;

  public ServletAndFilterInstrumentation(
      String basePackageName,
      String adviceClassName,
      String servletInitAdviceClassName,
      String filterInitAdviceClassName) {
    this.basePackageName = basePackageName;
    this.adviceClassName = adviceClassName;
    this.servletInitAdviceClassName = servletInitAdviceClassName;
    this.filterInitAdviceClassName = filterInitAdviceClassName;
  }

  public ServletAndFilterInstrumentation(String basePackageName, String adviceClassName) {
    this(basePackageName, adviceClassName, null, null);
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(basePackageName + ".Servlet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(namedOneOf(basePackageName + ".Filter", basePackageName + ".Servlet"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        namedOneOf("doFilter", "service")
            .and(takesArgument(0, named(basePackageName + ".ServletRequest")))
            .and(takesArgument(1, named(basePackageName + ".ServletResponse")))
            .and(isPublic()),
        adviceClassName);
    if (servletInitAdviceClassName != null) {
      transformers.put(
          named("init").and(takesArgument(0, named(basePackageName + ".ServletConfig"))),
          servletInitAdviceClassName);
    }
    if (filterInitAdviceClassName != null) {
      transformers.put(
          named("init").and(takesArgument(0, named(basePackageName + ".FilterConfig"))),
          filterInitAdviceClassName);
    }
    return transformers;
  }
}
