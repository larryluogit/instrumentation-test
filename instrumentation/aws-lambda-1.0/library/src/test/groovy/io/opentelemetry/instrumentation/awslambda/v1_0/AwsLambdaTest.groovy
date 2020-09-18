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

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.auto.test.InstrumentationTestTrait

class AwsLambdaTest extends AbstractAwsLambdaTest implements InstrumentationTestTrait {

  def cleanup() {
    assert testWriter.forceFlushCalled()
  }

  class TestRequestHandler extends TracingRequestHandler<String, String> {

    @Override
    protected String doHandleRequest(String input, Context context) {
      return AbstractAwsLambdaTest.doHandleRequest(input, context)
    }
  }

  @Override
  RequestHandler<String, String> handler() {
    return new TestRequestHandler()
  }
}
