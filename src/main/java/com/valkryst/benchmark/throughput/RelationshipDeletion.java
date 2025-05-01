package com.valkryst.benchmark.throughput;

import com.valkryst.benchmark.BenchmarkHelper;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import lombok.extern.log4j.Log4j2;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.NoSuchElementException;

@Log4j2
@State(Scope.Benchmark)
public class RelationshipDeletion extends BenchmarkHelper {
    /** The number of relationships to pre-create and add to OpenFGA before each iteration begins. */
    private static final int MAX_RELATIONSHIPS = 20_000;

    @Setup(Level.Iteration)
    public void setup() {
        super.deleteQueue.addAll(
            super.createUsers(MAX_RELATIONSHIPS, true)
        );
    }

    @TearDown
    public void teardown() {
        super.teardown();
    }

    @Benchmark
    public void benchmark() {
        final ClientTupleKeyWithoutCondition tuple;
        try {
            tuple = super.deleteQueue.removeFirst();
        } catch (final NoSuchElementException e) {
            log.error("Failed to retrieve tuple from deleteQueue. The queue is empty. Try increasing MAX_RELATIONSHIPS.");
            System.exit(1);
            return;
        } catch (final Exception e) {
            log.error(e);
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
