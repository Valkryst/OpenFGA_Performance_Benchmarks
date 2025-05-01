package com.valkryst.benchmark.throughput;

import com.valkryst.benchmark.BenchmarkHelper;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.errors.FgaApiValidationError;
import lombok.extern.log4j.Log4j2;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

@Log4j2
@State(Scope.Benchmark)
public class TransitiveRelationshipLookup extends BenchmarkHelper {
    /**
     * <p>
     *     The number of relationships (in this case, they're group hierarchies) to pre-create and add to the
     *     {@link #lookupQueue}, and to write to the OpenFGA API, before the benchmark begins.
     * </p>
     *
     * <p>
     *     This is a <i>magic number</i> which I decided based on an educated guess. If the benchmarks pass without
     *     failing to retrieve a tuple from the {@link #lookupQueue}, then we can lower the number by a few thousand and
     *     re-test. It may save a few seconds, and some RAM, when running the benchmarks.
     * </p>
     */
    private static final int TOTAL_PRECREATED_HIERARCHIES = 20_000;

    /** Number of groups to create in each hierarchy. */
    private static final int HIERARCHY_DEPTH = 5;

    /** A list of tuples which have been written to the OpenFGA API, and which can be used to lookup relationships. */
    private final Queue<ClientTupleKey> lookupQueue = new ConcurrentLinkedQueue<>();

    /** UUID of the report to lookup. This is the same for all groups, just to make things easy. */
    private final String reportUUID = UUID.randomUUID().toString();

    @Setup(Level.Iteration)
    public void setup() {
        final var groups = super.createGroups(TOTAL_PRECREATED_HIERARCHIES, HIERARCHY_DEPTH);
        super.deleteQueue.addAll(groups);

        final var newGroups = new ArrayList<ClientTupleKey>(TOTAL_PRECREATED_HIERARCHIES);

        /*
         * We want to run a lookup from the lowest level of each hierarchy. Knowing the internals of the createGroups
         * function, we can skip through the list of groups and populate the lookupQueue with the leaf nodes.
         */
        for (int i = 1 ; i != TOTAL_PRECREATED_HIERARCHIES ; i++) {
            final var leaf = groups.get((i * HIERARCHY_DEPTH) - 1);
            lookupQueue.offer(leaf);

            // We need to be able to determine if the leaf has access to a report VIA the highest level group, so we
            // create that relationship here.
            final var root = groups.get((i - 1) * HIERARCHY_DEPTH);

            final var tuple = new ClientTupleKey();
            tuple.user(root.getObject()); // The object of the highest-level group is the actual root node.
            tuple.relation("reader");
            tuple._object("report:" + reportUUID);

            newGroups.add(tuple);
            super.deleteQueue.add(tuple);
        }

        while (!newGroups.isEmpty()) {
            final var body = new ClientWriteRequest();

            final var subset = newGroups.subList(0, Math.min(1000, newGroups.size()));
            body.writes(subset);

            super.writeToOpenFGA(body);

            newGroups.removeAll(subset);
        }
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }

    @Benchmark
    public void benchmark() {
        final var tuple = lookupQueue.poll();
        if (tuple == null) {
            log.error("Failed to retrieve tuple from lookupQueue. The queue is empty. Try increasing TOTAL_PRECREATED_HIERARCHIES.");
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
}
