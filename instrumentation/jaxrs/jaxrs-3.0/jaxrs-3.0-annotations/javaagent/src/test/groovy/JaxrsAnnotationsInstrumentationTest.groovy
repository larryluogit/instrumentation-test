/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.SemanticAttributes
import spock.lang.Unroll

import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HEAD
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path

import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.utils.ClassUtils.getClassName

class JaxrsAnnotationsInstrumentationTest extends AgentInstrumentationSpecification {

  @Unroll
  def "span named '#paramName' from annotations on class '#className' when is not root span"() {
    setup:
    runWithHttpServerSpan {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "GET " + paramName
          kind SERVER
          hasNoParent()
          attributes {
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "GET"
            "$SemanticAttributes.HTTP_ROUTE" paramName
          }
        }
        span(1) {
          name "${className}.call"
          childOf span(0)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" obj.getClass().getName()
            "$SemanticAttributes.CODE_FUNCTION" "call"
          }
        }
      }
    }

    when: "multiple calls to the same method"
    runWithHttpServerSpan {
      (1..10).each {
        obj.call()
      }
    }
    then: "doesn't increase the cache size"

    where:
    paramName      | obj
    "/a"           | new Jax() {
      @Path("/a")
      void call() {
      }
    }
    "/b"           | new Jax() {
      @GET
      @Path("/b")
      void call() {
      }
    }
    "/interface/c" | new InterfaceWithPath() {
      @POST
      @Path("/c")
      void call() {
      }
    }
    "/interface"   | new InterfaceWithPath() {
      @HEAD
      void call() {
      }
    }
    "/abstract/d"  | new AbstractClassWithPath() {
      @POST
      @Path("/d")
      void call() {
      }
    }
    "/abstract"    | new AbstractClassWithPath() {
      @PUT
      void call() {
      }
    }
    "/child/e"     | new ChildClassWithPath() {
      @OPTIONS
      @Path("/e")
      void call() {
      }
    }
    "/child/call"  | new ChildClassWithPath() {
      @DELETE
      void call() {
      }
    }
    "/child/call"  | new ChildClassWithPath()
    "/child/call"  | new JavaInterfaces.ChildClassOnInterface()
    "/child/call"  | new JavaInterfaces.DefaultChildClassOnInterface()

    className = getClassName(obj.class)
  }

  def "no annotations has no effect"() {
    setup:
    runWithHttpServerSpan {
      obj.call()
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "GET"
          kind SERVER
          attributes {
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "GET"
          }
        }
      }
    }

    where:
    obj | _
    new Jax() {
      void call() {
      }
    }   | _
  }

  interface Jax {
    void call()
  }

  @Path("/interface")
  interface InterfaceWithPath extends Jax {
    @GET
    void call()
  }

  @Path("/abstract")
  static abstract class AbstractClassWithPath implements Jax {
    @PUT
    abstract void call()
  }

  @Path("child")
  static class ChildClassWithPath extends AbstractClassWithPath {
    @Path("call")
    @POST
    void call() {
    }
  }
}
