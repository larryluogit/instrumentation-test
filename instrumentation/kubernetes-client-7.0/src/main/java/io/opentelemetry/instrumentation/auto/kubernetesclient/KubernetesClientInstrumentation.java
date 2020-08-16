/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.kubernetesclient;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.kubernetes.client.openapi.ApiClient;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import okhttp3.Interceptor;

@AutoService(Instrumenter.class)
public class KubernetesClientInstrumentation extends Instrumenter.Default {

  public KubernetesClientInstrumentation() {
    super("kubernetes-client");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("io.kubernetes.client.openapi.ApiClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("io.kubernetes.client.openapi.ApiClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".KubernetesClientTracer",
      packageName + ".TracingInterceptor",
      packageName + ".KubernetesRequestDigest",
      packageName + ".KubernetesResource",
      packageName + ".KubernetesVerb",
      packageName + ".ParseKubernetesResourceException",
      "com.google.common.base.Strings",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        ElementMatchers.isMethod()
            .and(named("initHttpClient"))
            .and(ElementMatchers.takesArguments(1))
            .and(ElementMatchers.takesArgument(0, named("java.util.List"))),
        KubernetesClientInstrumentation.class.getName() + "$KubernetesAdvice");
  }

  public static class KubernetesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addTracingInterceptor(
        @Advice.This ApiClient apiClient, @Advice.Argument(0) List<Interceptor> interceptors) {

      for (Interceptor interceptor : interceptors) {
        if (interceptor instanceof TracingInterceptor) {
          return;
        }
      }

      apiClient.setHttpClient(
          apiClient.getHttpClient().newBuilder().addInterceptor(new TracingInterceptor()).build());
    }
  }
}
