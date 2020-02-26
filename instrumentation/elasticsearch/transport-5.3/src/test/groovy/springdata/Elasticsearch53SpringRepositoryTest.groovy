/*
 * Copyright 2020, OpenTelemetry Authors
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
package springdata

import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.sdk.trace.SpanData
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

class Elasticsearch53SpringRepositoryTest extends AgentTestRunner {
  // Setting up appContext & repo with @Shared doesn't allow
  // spring-data instrumentation to applied.
  // To change the timing without adding ugly checks everywhere -
  // use a dynamic proxy.  There's probably a more "groovy" way to do this.

  @Shared
  DocRepository repo = Proxy.newProxyInstance(
    getClass().getClassLoader(),
    [DocRepository] as Class[],
    new LazyProxyInvoker())

  static class LazyProxyInvoker implements InvocationHandler {
    def repo

    DocRepository getOrCreateRepository() {
      if (repo != null) {
        return repo
      }

      def applicationContext = new AnnotationConfigApplicationContext(Config)
      repo = applicationContext.getBean(DocRepository)

      return repo
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return method.invoke(getOrCreateRepository(), args)
    }
  }

  def setup() {
    TEST_WRITER.clear()
    runUnderTrace("delete") {
      repo.deleteAll()
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(1) {
      trace(0, 2) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.findAll"
            "$Tags.COMPONENT" "spring-data"
          }
        }

        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          errored false
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }

  def "test CRUD"() {
    when:
    def doc = new Doc()

    then:
    repo.index(doc) == doc

    and:
    assertTraces(2) {
      trace(0, 3) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "ElasticsearchRepository.index"
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
          }
        }
        span(2) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "PutMappingAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "PutMappingAction"
            "elasticsearch.request" "PutMappingRequest"
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findById("1").get() == doc

    and:
    assertTraces(1) {
      trace(0, 2) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.findById"
            "$Tags.COMPONENT" "spring-data"
          }
        }

        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then:
    repo.index(doc) == doc
    repo.findById("1").get() == doc

    and:
    assertTraces(2) {
      trace(0, 3) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "ElasticsearchRepository.index"
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "IndexAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 200
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
          }
        }
        span(2) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }
      trace(1, 2) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.findById"
            "$Tags.COMPONENT" "spring-data"
          }
        }

        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "GetAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.deleteById("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    assertTraces(2) {
      trace(0, 3) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.deleteById"
            "$Tags.COMPONENT" "spring-data"
          }
        }

        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "DeleteAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
          }
        }
        span(2) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "RefreshAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
          }
        }
      }

      trace(1, 2) {
        sortSpans {
          sort(spans)
        }
        span(0) {
          operationName "repository.operation"
          spanKind INTERNAL
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.findAll"
            "$Tags.COMPONENT" "spring-data"
          }
        }

        span(1) {
          operationName "elasticsearch.query"
          spanKind CLIENT
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.RESOURCE_NAME" "SearchAction"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.DB_TYPE" "elasticsearch"
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }

  def sort(List<SpanData> spans) {
    // need to normalize span ordering since they are finished by different threads
    if (spans[1].name == "repository.operation") {
      def tmp = spans[1]
      spans[1] = spans[0]
      spans[0] = tmp
    }
  }
}
