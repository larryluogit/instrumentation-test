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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.metrics.Meter;
import application.io.opentelemetry.metrics.MeterProvider;

public class ApplicationMeterProvider implements MeterProvider {

  @Override
  public Meter get(String instrumentationName) {
    return new ApplicationMeter(
        io.opentelemetry.OpenTelemetry.getMeterProvider().get(instrumentationName));
  }

  @Override
  public Meter get(String instrumentationName, String instrumentationVersion) {
    return new ApplicationMeter(
        io.opentelemetry.OpenTelemetry.getMeterProvider()
            .get(instrumentationName, instrumentationVersion));
  }
}
