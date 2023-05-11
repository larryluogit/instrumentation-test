/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

class ConfigPropertiesUtilTest {

  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @SetSystemProperty(key = "test.property.string", value = "sys")
  @Test
  void getString_systemProperty() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isEqualTo("sys");
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_STRING", value = "env")
  @Test
  void getString_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isEqualTo("env");
  }

  @Test
  void getString_none() {
    assertThat(ConfigPropertiesUtil.getString("test.property.string")).isNull();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "12")
  @SetSystemProperty(key = "test.property.int", value = "42")
  @Test
  void getInt_systemProperty() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(42);
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_INT", value = "12")
  @Test
  void getInt_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(12);
  }

  @Test
  void getInt_none() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(-1);
  }

  @SetSystemProperty(key = "test.property.int", value = "not a number")
  @Test
  void getInt_invalidNumber() {
    assertThat(ConfigPropertiesUtil.getInt("test.property.int", -1)).isEqualTo(-1);
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "false")
  @SetSystemProperty(key = "test.property.boolean", value = "true")
  @Test
  void getBoolean_systemProperty() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isTrue();
  }

  @SetEnvironmentVariable(key = "TEST_PROPERTY_BOOLEAN", value = "true")
  @Test
  void getBoolean_environmentVariable() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isTrue();
  }

  @Test
  void getBoolean_none() {
    assertThat(ConfigPropertiesUtil.getBoolean("test.property.boolean", false)).isFalse();
  }
}
