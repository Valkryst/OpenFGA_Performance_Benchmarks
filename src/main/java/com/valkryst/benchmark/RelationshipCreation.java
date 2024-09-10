package com.valkryst.benchmark;

import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

@State(Scope.Benchmark)
public class RelationshipCreation extends BenchmarkBase {
    /**
     * <p>The number of relationships to pre-create and add to the {@link #writeQueue} before the benchmark begins.</p>
     *
     * <p>
     *     This is a <i>magic number</i> which I decided based on an educated guess. If the benchmarks pass without
     *     failing to retrieve a tuple from the {@link #writeQueue}, then we can lower the number by a few thousand
     *     and re-test. It may save a few seconds, and some RAM, when running the benchmarks.
     * </p>
     */
    private static final int TOTAL_PRECREATED_RELATIONSHIPS = 40_000;

    /** A pool of pre-created tuples which can be used to write relationships to the OpenFGA API. */
    private final Queue<ClientTupleKey> writeQueue = new ConcurrentLinkedQueue<>();

    /** A list of tuples which have been written to the OpenFGA API, and which must be deleted. */
    private final Queue<ClientTupleKey> deleteQueue = new ConcurrentLinkedQueue<>();

    @Setup
    public void setup() {
        for (int i = 0 ; i < TOTAL_PRECREATED_RELATIONSHIPS ; i++) {
            final var tuple = new ClientTupleKey();
            tuple.user("user:" + UUID.randomUUID());
            tuple.relation("reader");
            tuple._object("report:" + UUID.randomUUID());
            writeQueue.offer(tuple);
        }
    }

    @TearDown
    public void teardown() {
        final var body = new ClientWriteRequest();

        while (!deleteQueue.isEmpty()) {
            final var tuple = deleteQueue.poll();

            body.deletes(List.of(tuple));

            try {
                final var response = super.openFgaClient.write(body, null).get();
                if (response.getStatusCode() != 200) {
                    System.err.println("Failed to delete relationship:\n" + response.getRawResponse());
                    System.exit(1);
                }
            } catch (final FgaInvalidParameterException | InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            } catch (final ExecutionException e) {
                e.getCause().printStackTrace();
                System.exit(1);
            }
        }
    }

//    @Benchmark
    public void benchmark() {
        final var tuple = writeQueue.poll();
        if (tuple == null) {
            System.err.println("Failed to retrieve tuple from writeQueue. The queue is empty. Try increasing TOTAL_PRECREATED_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientWriteRequest();
        body.writes(List.of(tuple));

        try {
            final var response = super.openFgaClient.write(body, null).get();
            if (response.getStatusCode() != 200) {
                System.err.println("Failed to create relationship:\n" + response.getRawResponse());
                System.exit(1);
            }

            deleteQueue.offer(tuple);
        } catch (final FgaInvalidParameterException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (final ExecutionException e) {
            e.getCause().printStackTrace();
            System.exit(1);
        }
    }
}
