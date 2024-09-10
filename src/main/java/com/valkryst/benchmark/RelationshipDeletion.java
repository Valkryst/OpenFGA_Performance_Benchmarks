package com.valkryst.benchmark;

import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@State(Scope.Benchmark)
public class RelationshipDeletion extends BenchmarkBase {
    /**
     * <p>
     *     The number of relationships to pre-create and add to the {@link #deleteQueue}, and to write to the OpenFGA
     *     API, before the benchmark begins.
     * </p>
     *
     * <p>
     *     This is a <i>magic number</i> which I decided based on an educated guess. If the benchmarks pass without
     *     failing to retrieve a tuple from the {@link #deleteQueue}, then we can lower the number by a few thousand
     *     and re-test. It may save a few seconds, and some RAM, when running the benchmarks.
     * </p>
     */
    private static final int TOTAL_PRECREATED_RELATIONSHIPS = 40_000;

    /** A list of tuples which have been written to the OpenFGA API, and which must be deleted. */
    private final Queue<ClientTupleKey> deleteQueue = new ConcurrentLinkedQueue<>();

    @Setup
    public void setup() {
        deleteQueue.addAll(
            super.createUsers(TOTAL_PRECREATED_RELATIONSHIPS, 1000, true)
        );
    }

    @TearDown
    public void teardown() {
        final var body = new ClientWriteRequest();

        while (!deleteQueue.isEmpty()) {
            final var tuple = deleteQueue.poll();

            body.deletes(List.of(tuple));

            super.writeToOpenFGA(body);
        }
    }

    @Benchmark
    public void benchmark() {
        final var tuple = deleteQueue.poll();
        if (tuple == null) {
            System.err.println("Failed to retrieve tuple from deleteQueue. The queue is empty. Try increasing TOTAL_PRECREATED_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientWriteRequest();
        body.deletes(List.of(tuple));

        super.writeToOpenFGA(body);
    }
}
