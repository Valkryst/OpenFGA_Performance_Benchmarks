package com.valkryst.benchmark;

import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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
    private final List<ClientTupleKeyWithoutCondition> deleteQueue = new ArrayList<>();

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
            final var subset = deleteQueue.subList(0, Math.min(1000, deleteQueue.size()));
            deleteQueue.removeAll(subset);

            body.deletes(subset);
            super.writeToOpenFGA(body);
        }
    }

    @Benchmark
    public void benchmark() {
        final ClientTupleKeyWithoutCondition tuple;
        try {
            tuple = deleteQueue.removeFirst();
        } catch (final NoSuchElementException e) {
            System.err.println("Failed to retrieve tuple from deleteQueue. The queue is empty. Try increasing TOTAL_PRECREATED_RELATIONSHIPS.");
            System.exit(1);
            return;
        } catch (final Exception e) {
            e.printStackTrace();
            System.exit(1);
            return;
        }

        super.writeToOpenFGA(
            new ClientWriteRequest().deletes(
                List.of(tuple)
            )
        );
    }
}
