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

package io.opentelemetry.auto.test.asserts

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import java.util.regex.Pattern

class AttributesAssert {
  private final Map<String, Object> attributes
  private final Set<String> assertedAttributes = new TreeSet<>()

  private AttributesAssert(Map<String, Object> attributes) {
    this.attributes = attributes
  }

  static void assertAttributes(Map<String, Object> attributes,
                               @ClosureParams(value = SimpleType, options = ['io.opentelemetry.auto.test.asserts.AttributesAssert'])
                               @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new AttributesAssert(attributes)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertAttributesAllVerified()
  }

  def attribute(String name, value) {
    if (value == null) {
      return
    }
    assertedAttributes.add(name)
    def val = attributes.get(name)
    if (value instanceof Pattern) {
      assert val =~ value
    } else if (value instanceof Class) {
      assert ((Class) value).isInstance(val)
    } else if (value instanceof Closure) {
      assert ((Closure) value).call(val)
    } else {
      assert val == value
    }
  }

  def attribute(String name) {
    return attributes[name]
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    attribute(name, args[0])
  }

  void assertAttributesAllVerified() {
    Set<String> allAttributes = new TreeSet<>(attributes.keySet())
    Set<String> unverifiedAttributes = new TreeSet(allAttributes)
    unverifiedAttributes.removeAll(assertedAttributes)
    // The first and second condition in the assert are exactly the same
    // but both are included in order to provide better context in the error message.
    // containsAll because tests may assert more attributes than span actually has
    assert unverifiedAttributes.isEmpty() && assertedAttributes.containsAll(allAttributes)
  }
}
