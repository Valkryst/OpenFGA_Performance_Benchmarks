package com.valkryst.benchmark.throughput;

import com.valkryst.benchmark.BenchmarkHelper;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.errors.FgaApiValidationError;
import lombok.extern.log4j.Log4j2;
import org.openjdk.jmh.annotations.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

@Log4j2
@State(Scope.Benchmark)
public class RelationshipLookup extends BenchmarkHelper {
    /** The number of relationships to pre-create and add to the queues before each iteration begins. */
    private static final int MAX_RELATIONSHIPS = 20_000;

    /** A list of tuples which have been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientTupleKey> existentLookupQueue = new ConcurrentLinkedQueue<>();

    /** A list of tuples which <i>have not</i> been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientTupleKey> nonExistentLookupQueue = new ConcurrentLinkedQueue<>();

    @Setup(Level.Iteration)
    public void setup() {
        final var users = super.createUsers(MAX_RELATIONSHIPS, 1000, true);
        existentLookupQueue.addAll(users);
        super.deleteQueue.addAll(users);

        nonExistentLookupQueue.addAll(super.createUsers(MAX_RELATIONSHIPS, 1000, false));
    }

    @TearDown
    public void teardown() {
        super.teardown();

        existentLookupQueue.clear();
        nonExistentLookupQueue.clear();
    }

    @Benchmark
    public void benchmarkExistingRelationships() {
        final var tuple = existentLookupQueue.poll();
        if (tuple == null) {
            log.error("Failed to retrieve tuple from existentLookupQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientCheckRequest();
        body.user(tuple.getUser());
        body.relation(tuple.getRelation());
        body._object(tuple.getObject());

        try {
            final var response = super.getClient().check(body, null).get();

            if (response.getStatusCode() != 200) {
                log.error("Failed to lookup relationship:\n{}", response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.FALSE.equals(response.getAllowed())) {
                log.error("Relationship does not exist, but it should:\n{}", response.getRawResponse());
                System.exit(1);
            }
        } catch (final Exception e) {
            log.error(e);

            if (e instanceof ExecutionException) {
                if (e.getCause() instanceof FgaApiValidationError) {
                    log.error("Validation Error: {}", ((FgaApiValidationError) e.getCause()).getResponseData());
                }
            }

            System.exit(1);
        }
    }

    @Benchmark
    public void benchmarkNonexistentRelationships() {
        final var tuple = nonExistentLookupQueue.poll();
        if (tuple == null) {
            log.error("Failed to retrieve tuple from nonexistentLookupQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
        }

        final var body = new ClientCheckRequest();
        body.user(tuple.getUser());
        body.relation(tuple.getRelation());
        body._object(tuple.getObject());

        try {
            final var response = super.getClient().check(body, null).get();

            if (response.getStatusCode() != 200) {
                log.error("Failed to lookup relationship:\n{}", response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.TRUE.equals(response.getAllowed())) {
                log.error("Relationship exists, but it should not:\n{}", response.getRawResponse());
                System.exit(1);
            }
        } catch (final Exception e) {
            log.error(e);

            if (e instanceof ExecutionException) {
                if (e.getCause() instanceof FgaApiValidationError) {
                    log.error("Validation Error: {}", ((FgaApiValidationError) e.getCause()).getResponseData());
                }
            }

            System.exit(1);
        }
    }
}
