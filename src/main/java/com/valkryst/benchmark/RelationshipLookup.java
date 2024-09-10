package com.valkryst.benchmark;

import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

@State(Scope.Benchmark)
public class RelationshipLookup extends BenchmarkBase {
    /**
     * <p>
     *     The number of relationships to pre-create and add to the {@link #deleteQueue}, and to write to the OpenFGA
     *     API, before the benchmark begins.
     * </p>
     *
     * <p>
     *     This is a <i>magic number</i> which I decided based on an educated guess. If the benchmarks pass without
     *     failing to retrieve a tuple from the {@link #existentLookupQueue} or {@link #nonExistentLookupQueue}, then we
     *     can lower the number by a few thousand and re-test. It may save a few seconds, and some RAM, when running the
     *     benchmarks.
     * </p>
     */
    private static final int TOTAL_PRECREATED_RELATIONSHIPS = 100_000;

    /** A list of tuples which have been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientTupleKey> existentLookupQueue = new ConcurrentLinkedQueue<>();

    /** A list of tuples which <i>have not</i> been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientTupleKey> nonExistentLookupQueue = new ConcurrentLinkedQueue<>();

    /** A list of tuples which have been written to the OpenFGA API, and which must be deleted. */
    private final Queue<ClientTupleKey> deleteQueue = new ConcurrentLinkedQueue<>();

    @Setup
    public void setup() {
        existentLookupQueue.addAll(
            super.createUsers(TOTAL_PRECREATED_RELATIONSHIPS, 1000, true)
        );

        nonExistentLookupQueue.addAll(
            super.createUsers(TOTAL_PRECREATED_RELATIONSHIPS, 1000, false)
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

        existentLookupQueue.clear();
        nonExistentLookupQueue.clear();
    }

    @Benchmark
    public void benchmarkExistingRelationships() {
        final var tuple = existentLookupQueue.poll();
        if (tuple == null) {
            System.err.println("Failed to retrieve tuple from existentLookupQueue. The queue is empty. Try increasing TOTAL_PRECREATED_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientCheckRequest();
        body.user(tuple.getUser());
        body.relation(tuple.getRelation());
        body._object(tuple.getObject());

        try {
            final var response = super.openFgaClient.check(body, null).get();

            if (response.getStatusCode() != 200) {
                System.err.println("Failed to lookup relationship:\n" + response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.FALSE.equals(response.getAllowed())) {
                System.err.println("Relationship does not exist, but it should:\n" + response.getRawResponse());
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

    @Benchmark
    public void benchmarkNonexistentRelationships() {
        final var tuple = nonExistentLookupQueue.poll();
        if (tuple == null) {
            System.err.println("Failed to retrieve tuple from nonexistentLookupQueue. The queue is empty. Try increasing TOTAL_PRECREATED_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientCheckRequest();
        body.user(tuple.getUser());
        body.relation(tuple.getRelation());
        body._object(tuple.getObject());

        try {
            final var response = super.openFgaClient.check(body, null).get();

            if (response.getStatusCode() != 200) {
                System.err.println("Failed to lookup relationship:\n" + response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.TRUE.equals(response.getAllowed())) {
                System.err.println("Relationship exists, but it should not:\n" + response.getRawResponse());
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
