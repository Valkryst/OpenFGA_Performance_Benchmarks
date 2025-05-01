package com.valkryst.benchmark;

import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import lombok.extern.log4j.Log4j2;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Log4j2
@State(Scope.Benchmark)
public class RelationshipCreation extends BenchmarkHelper {
    /** The number of relationships to pre-create and add to the {@link #writeQueue} before each iteration begins. */
    private static final int MAX_RELATIONSHIPS = 20_000;

    /** A pool of pre-created tuples which can be used to write relationships to the OpenFGA API. */
    private final Queue<ClientTupleKey> writeQueue = new ConcurrentLinkedQueue<>();

    @Setup(Level.Iteration)
    public void setupTrial() {
        final int tuplesToCreate = MAX_RELATIONSHIPS - writeQueue.size();
        if (tuplesToCreate <= 0) {
            return;
        }

        writeQueue.addAll(super.createUsers(tuplesToCreate, 1000, false));
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }

    @Benchmark
    public void benchmark() {
        final var tuple = writeQueue.poll();
        if (tuple == null) {
            System.err.println("Failed to retrieve tuple from writeQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientWriteRequest();
        body.writes(List.of(tuple));
        super.writeToOpenFGA(body);

        super.deleteQueue.add(tuple);
    }
}
