package com.valkryst.benchmark.throughput;

import com.valkryst.benchmark.BenchmarkHelper;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
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
    private final Queue<ClientCheckRequest> existentLookupQueue = new ConcurrentLinkedQueue<>();

    /** A list of tuples which <i>have not</i> been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientCheckRequest> nonExistentLookupQueue = new ConcurrentLinkedQueue<>();

    @Setup(Level.Iteration)
    public void setup() {
        // Populate the existent queue.
        var users = super.createUsers(MAX_RELATIONSHIPS, true);
        super.deleteQueue.addAll(users);

        for (final var user : users) {
            final var request = new ClientCheckRequest();
            request.user(user.getUser());
            request.relation(user.getRelation());
            request._object(user.getObject());

            existentLookupQueue.add(request);
        }

        // Populate the non-existent queue.
        users = super.createUsers(MAX_RELATIONSHIPS, false);
        for (final var user : users) {
            final var request = new ClientCheckRequest();
            request.user(user.getUser());
            request.relation(user.getRelation());
            request._object(user.getObject());

            nonExistentLookupQueue.add(request);
        }
    }

    @TearDown
    public void teardown() {
        super.teardown();

        existentLookupQueue.clear();
        nonExistentLookupQueue.clear();
    }

    @Benchmark
    public void benchmarkExistingRelationships() {
        final var request = existentLookupQueue.poll();
        if (request == null) {
            log.error("Failed to retrieve request from existentLookupQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
        }

        try {
            final var response = super.getClient().check(request, null).get();

            if (response.getStatusCode() != 200) {
                log.error("Failed to lookup relationship:\n{}", response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.FALSE.equals(response.getAllowed())) {
                log.error("Relationship does not exist, but it should:\n{}", response.getRawResponse());
                System.exit(1);
            }
        } catch (final Exception e) {
            log.error("", e);

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
        final var request = nonExistentLookupQueue.poll();
        if (request == null) {
            log.error("Failed to retrieve request from nonexistentLookupQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
        }

        try {
            final var response = super.getClient().check(request, null).get();

            if (response.getStatusCode() != 200) {
                log.error("Failed to lookup relationship:\n{}", response.getRawResponse());
                System.exit(1);
            }

            if (Boolean.TRUE.equals(response.getAllowed())) {
                log.error("Relationship exists, but it should not:\n{}", response.getRawResponse());
                System.exit(1);
            }
        } catch (final Exception e) {
            log.error("", e);

            if (e instanceof ExecutionException) {
                if (e.getCause() instanceof FgaApiValidationError) {
                    log.error("Validation Error: {}", ((FgaApiValidationError) e.getCause()).getResponseData());
                }
            }

            System.exit(1);
        }
    }
}
