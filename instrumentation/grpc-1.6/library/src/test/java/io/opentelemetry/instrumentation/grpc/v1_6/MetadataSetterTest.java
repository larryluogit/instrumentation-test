package io.opentelemetry.instrumentation.grpc.v1_6;

import io.opentelemetry.context.Context;
import io.grpc.Metadata;
import io.opentelemetry.instrumentation.grpc.v1_6.propagator.GrpcPropagator;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;

class MetadataSetterTest {

    @Test
    void checkThatIterableSizeEqualsOne() {
        GrpcPropagator tested = new GrpcPropagator();
        Metadata metadata = new Metadata();
        tested.inject(Context.current(), metadata, MetadataSetter.INSTANCE);

        int size = 0;
        for (String s : metadata.getAll(
                Metadata.Key.of(GrpcPropagator.FIELD, Metadata.ASCII_STRING_MARSHALLER))) {
            if (s != null) {
                size++;
            }
        }
        assertTrue(size == 1);
    }
}