/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "20.0.0.12", jdk = "8")
class LibertyServletOnlySmokeTest extends LibertySmokeTest {

  @Override
  protected Map<String, String> getExtraResources() {
    return ["liberty-servlet.xml": "/config/server.xml"]
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/app/hello.txt":
      case "/app/file-that-does-not-exist":
        return "HTTP GET"
    }
    return super.getSpanName(path)
  }
}
