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

package io.opentelemetry.auto.integration.classloading

import com.google.common.collect.MapMaker
import com.google.common.reflect.ClassPath
import io.opentelemetry.auto.test.IntegrationTestUtils
import spock.lang.Specification

class ShadowPackageRenamingTest extends Specification {
  def "agent dependencies renamed"() {
    setup:
    Class<?> clazz =
      IntegrationTestUtils.getAgentClassLoader()
        .loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller")
    URL userGuava =
      MapMaker.getProtectionDomain().getCodeSource().getLocation()
    URL agentGuavaDep =
      clazz
        .getClassLoader()
        .loadClass("com.google.common.collect.MapMaker")
        .getProtectionDomain()
        .getCodeSource()
        .getLocation()
    URL agentSource =
      clazz.getProtectionDomain().getCodeSource().getLocation()

    expect:
    agentSource.getFile() == "/"
    agentSource.getProtocol() == "x-internal-jar"
    agentSource == agentGuavaDep
    agentSource.getFile() != userGuava.getFile()
  }

  def "agent classes not visible"() {
    when:
    ClassLoader.getSystemClassLoader().loadClass("io.opentelemetry.javaagent.tooling.AgentInstaller")
    then:
    thrown ClassNotFoundException
  }

  def "agent jar contains no bootstrap classes"() {
    setup:
    ClassPath agentClasspath = ClassPath.from(IntegrationTestUtils.getAgentClassLoader())

    ClassPath bootstrapClasspath = ClassPath.from(IntegrationTestUtils.getBootstrapProxy())
    Set<String> bootstrapClasses = new HashSet<>()
    String[] bootstrapPrefixes = IntegrationTestUtils.getBootstrapPackagePrefixes()
    String[] agentPrefixes = IntegrationTestUtils.getAgentPackagePrefixes()
    List<String> badBootstrapPrefixes = []
    for (ClassPath.ClassInfo info : bootstrapClasspath.getAllClasses()) {
      bootstrapClasses.add(info.getName())
      // make sure all bootstrap classes can be loaded from system
      ClassLoader.getSystemClassLoader().loadClass(info.getName())
      boolean goodPrefix = false
      for (int i = 0; i < bootstrapPrefixes.length; ++i) {
        if (info.getName().startsWith(bootstrapPrefixes[i])) {
          goodPrefix = true
          break
        }
      }
      if (info.getName() == 'io.opentelemetry.javaagent.OpenTelemetryAgent') {
        // io.opentelemetry.javaagent.OpenTelemetryAgent isn't needed in the bootstrap prefixes
        // because it doesn't live in the bootstrap class loader, but it's still "good" for the
        // purpose of this test which is just checking all the classes sitting directly inside of
        // the agent jar
        goodPrefix = true
      }
      if (!goodPrefix) {
        badBootstrapPrefixes.add(info.getName())
      }
    }

    List<ClassPath.ClassInfo> agentDuplicateClassFile = new ArrayList<>()
    List<String> badAgentPrefixes = []
    for (ClassPath.ClassInfo classInfo : agentClasspath.getAllClasses()) {
      if (bootstrapClasses.contains(classInfo.getName())) {
        agentDuplicateClassFile.add(classInfo)
      }
      boolean goodPrefix = false
      for (int i = 0; i < agentPrefixes.length; ++i) {
        if (classInfo.getName().startsWith(agentPrefixes[i])) {
          goodPrefix = true
          break
        }
      }
      if (!goodPrefix) {
        badAgentPrefixes.add(classInfo.getName())
      }
    }

    expect:
    agentDuplicateClassFile == []
    badBootstrapPrefixes == []
    badAgentPrefixes == []
  }
}
