/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import io.opentelemetry.javaagent.IntegrationTestUtils
import io.opentelemetry.javaagent.instrumentation.api.SafeServiceLoader
import java.lang.reflect.Field
import java.lang.reflect.Method
import spock.lang.Specification

class MuzzleBytecodeTransformTest extends Specification {

  def "muzzle fields added to all instrumentation"() {
    setup:
    List<Class> unMuzzledClasses = []
    List<Class> nonLazyFields = []
    List<Class> unInitFields = []
    def instrumentationModuleClass = IntegrationTestUtils.getAgentClassLoader().loadClass("io.opentelemetry.javaagent.tooling.InstrumentationModule")
    for (Object instrumenter : SafeServiceLoader.load(instrumentationModuleClass, IntegrationTestUtils.getAgentClassLoader())) {
      if (!instrumentationModuleClass.isAssignableFrom(instrumenter.getClass())) {
        // muzzle only applies to default instrumenters
        continue
      }
      Field f
      Method m
      try {
        f = instrumenter.getClass().getDeclaredField("muzzleReferenceMatcher")
        f.setAccessible(true)
        if (f.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass())
        }
        m = instrumenter.getClass().getDeclaredMethod("getMuzzleReferenceMatcher")
        m.setAccessible(true)
        m.invoke(instrumenter)
        if (f.get(instrumenter) == null) {
          unInitFields.add(instrumenter.getClass())
        }
      } catch (NoSuchFieldException | NoSuchMethodException e) {
        unMuzzledClasses.add(instrumenter.getClass())
      } finally {
        if (null != f) {
          f.setAccessible(false)
        }
        if (null != m) {
          m.setAccessible(false)
        }
      }
    }
    expect:
    unMuzzledClasses == []
    nonLazyFields == []
    unInitFields == []
  }

}
