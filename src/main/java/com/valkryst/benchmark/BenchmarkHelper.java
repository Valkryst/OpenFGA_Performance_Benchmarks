package com.valkryst.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.errors.FgaApiValidationError;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class BenchmarkHelper {
    /** Path to the OpenFGA Authorization Model file. */
    private static final String MODEL_FILE_PATH = "/openfga/model.json";

    /** Client used when interacting with the OpenFGA API. */
    private OpenFgaClient openFgaClient;

    /** A list of tuples which have been written to the OpenFGA API, and which must be deleted. */
    protected List<ClientTupleKeyWithoutCondition> deleteQueue = new ArrayList<>();

    /** Deletes all tuples in the {@link #deleteQueue}, from the OpenFGA API, and clears the queue. */
    protected void teardown() {
        final var body = new ClientWriteRequest();

        while (!deleteQueue.isEmpty()) {
            final var subset = new ArrayList<>(deleteQueue.subList(0, Math.min(1000, deleteQueue.size())));
            deleteQueue.removeAll(subset);

            body.deletes(subset);
            writeToOpenFGA(body);
        }
    }

    /**
     * Creates one or more groups, each with a unique hierarchy of groups.
     *
     * @param totalGroups The total number of groups to create.
     * @param batchSize Number of groups to create in each batch.
     * @param hierarchyDepth Number of groups to create in each hierarchy.
     * @return Created groups, including their parent groups.
     */
    protected List<ClientTupleKey> createGroups(int totalGroups, final int batchSize, final int hierarchyDepth) {
        if (totalGroups < 1) {
            throw new IllegalArgumentException("totalGroups must be greater than or equal to 1.");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than or equal to 1.");
        }

        if (hierarchyDepth < 1) {
            throw new IllegalArgumentException("hierarchyDepth must be greater than or equal to 1.");
        }

        final var body = new ClientWriteRequest();
        final var groups = new ArrayList<ClientTupleKey>(totalGroups * hierarchyDepth);

        while (totalGroups > 0) {
            final var tuples = new ArrayList<ClientTupleKey>(Math.min(totalGroups, batchSize) * hierarchyDepth);

            for (int i = 0 ; i < Math.min(totalGroups, batchSize) ; i++) {
                var currentUUID = UUID.randomUUID().toString();
                var nextUUID = UUID.randomUUID().toString();

                for (int j = 0 ; j < hierarchyDepth ; j++) {
                    final var tuple = new ClientTupleKey();
                    tuple.user("group:" + currentUUID);
                    tuple.relation("subgroup");
                    tuple._object("group:" + nextUUID);
                    tuples.add(tuple);

                    currentUUID = nextUUID;
                    nextUUID = UUID.randomUUID().toString();
                }
            }

            body.writes(tuples);
            writeToOpenFGA(body);

            groups.addAll(tuples);
            totalGroups -= batchSize;
        }

        return groups;
    }

    /**
     * Creates one or more users and optionally adds them to OpenFGA VIA its API.
     *
     * @param totalUsers Total number of users to create.
     * @param batchSize Number of users to create in each batch.
     * @return Created users.
     */
    protected List<ClientTupleKey> createUsers(int totalUsers, final int batchSize, final boolean addToOpenFGA) {
        if (totalUsers < 1) {
            throw new IllegalArgumentException("totalUsers must be greater than or equal to 1.");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be greater than or equal to 1.");
        }

        final var body = new ClientWriteRequest();
        final var users = new ArrayList<ClientTupleKey>(totalUsers);

        while (totalUsers > 0) {
            final var tuples = new ArrayList<ClientTupleKey>(Math.min(totalUsers, batchSize));

            for (int i = 0 ; i < Math.min(totalUsers, batchSize) ; i++) {
                final var tuple = new ClientTupleKey();
                tuple.user("user:" + UUID.randomUUID());
                tuple.relation("reader");
                tuple._object("report:" + UUID.randomUUID());
                tuples.add(tuple);
            }

            if (!addToOpenFGA) {
                users.addAll(tuples);
                totalUsers -= batchSize;
                continue;
            }

            body.writes(tuples);
            writeToOpenFGA(body);

            users.addAll(tuples);
            totalUsers -= batchSize;
        }

        return users;
    }

    /**
     * Sends a write request to OpenFGA.
     *
     * @param body Request body.
     */
    protected void writeToOpenFGA(final @NonNull ClientWriteRequest body) {
        try {
            final var response = this.getClient().write(body, null).get();
            if (response.getStatusCode() != 200) {
                System.err.println(response.getRawResponse());
                System.exit(1);
            }
        } catch (final Exception e) {
            e.printStackTrace();

            if (e instanceof ExecutionException) {
                if (e.getCause() instanceof FgaApiValidationError) {
                    System.err.println("Validation Error: " + ((FgaApiValidationError) e.getCause()).getResponseData());
                }
            }

            System.exit(1);
        }
    }

    /**
     * <p>
     * Creates a new Authorization Model within OpenFGA, using the Authorization Model file located at
     * {@link #MODEL_FILE_PATH}.
     * </p>
     *
     * @param client {@link OpenFgaClient} to create the Authorization Model with.
     * @return ID of the created Authorization Model, or an empty {@link Optional} if the creation failed.
     *
     * @throws FgaInvalidParameterException If there's an issue creating the Authorization Model. See {@link OpenFgaClient#writeAuthorizationModel(WriteAuthorizationModelRequest)}.
     * @throws ExecutionException If there's an issue creating the Authorization Model. See {@link CompletableFuture#get()}.
     * @throws InterruptedException If there's an issue creating the Authorization Model. See {@link CompletableFuture#get()}.
     * @throws IOException If there's an issue reading the Authorization Model file.
     * @throws NullPointerException If {@link #MODEL_FILE_PATH} is blank or if the input stream is null.
     */
    private Optional<String> createAuthorizationModel(final @NonNull OpenFgaClient client) throws FgaInvalidParameterException, ExecutionException, InterruptedException, IOException, NullPointerException {
        final var stringBuilder = new StringBuilder();
        try (
            final var inputStream = Objects.requireNonNull(BenchmarkHelper.class.getResourceAsStream(MODEL_FILE_PATH));
            final var inputStreamReader = new InputStreamReader(inputStream);
            final var bufferedReader = new BufferedReader(inputStreamReader);
        ) {
            while (bufferedReader.ready()) {
                stringBuilder.append(bufferedReader.readLine());
            }
        }

        if (stringBuilder.isEmpty()) {
            throw new RuntimeException("The OpenFGA Authorization Model file is empty.");
        }

        return client.writeAuthorizationModel(
            new ObjectMapper().findAndRegisterModules().readValue(
                stringBuilder.toString(),
                WriteAuthorizationModelRequest.class
            )
        ).get().getAuthorizationModelId().describeConstable();
    }

    /**
     * <p>Creates a new Store within OpenFGA.</p>
     *
     * @param client {@link OpenFgaClient} to create the Authorization Model with.
     * @return ID of the created Store, or an empty {@link Optional} if the creation failed.
     *
     * @throws ExecutionException If there's an issue creating the store. See {@link CompletableFuture#get()}.
     * @throws FgaInvalidParameterException If there's an issue creating the store. See {@link OpenFgaClient#createStore(CreateStoreRequest)}.
     * @throws InterruptedException If there's an issue creating the store. See {@link CompletableFuture#get()}.
     */
    private Optional<String> createStore(final @NonNull OpenFgaClient client) throws ExecutionException, FgaInvalidParameterException, InterruptedException {
        final var request = new CreateStoreRequest();
        request.setName(UUID.randomUUID().toString());
        return client.createStore(request).get().getId().describeConstable();
    }

    /**
     * Constructs a new {@link OpenFgaClient} and initializes it with a Store and Authorization Model.
     *
     * @return Constructed {@link OpenFgaClient}.
     *
     * @throws FgaInvalidParameterException If there's an issue creating the Store or Authorization Model.
     * @throws ExecutionException If there's an issue creating the Store or Authorization Model.
     * @throws InterruptedException If there's an issue creating the Store or Authorization Model.
     * @throws IOException If there's an issue reading the Authorization Model file.
     * @throws NullPointerException If {@link #MODEL_FILE_PATH} is blank.
     */
    public synchronized OpenFgaClient getClient() throws FgaInvalidParameterException, ExecutionException, InterruptedException, IOException, NullPointerException {
        if (this.openFgaClient != null) {
            return this.openFgaClient;
        }

        final var config = new ClientConfiguration();
        config.apiUrl(this.getEnvironmentVariable("OPENFGA_API_URL"));
        config.credentials(new Credentials(new ApiToken(this.getEnvironmentVariable("OPENFGA_AUTHN_PRESHARED_KEYS"))));
        final var client = new OpenFgaClient(config);

        var id = this.createStore(client);
        if (id.isEmpty()) {
            throw new RuntimeException("There was a failure when creating a Store in OpenFGA. The Store ID is blank.");
        }
        client.setStoreId(id.get());

        id = this.createAuthorizationModel(client);
        if (id.isEmpty()) {
            throw new RuntimeException("There was a failure when creating an Authorization Model in OpenFGA. The Authorization Model ID is blank.");
        }
        client.setAuthorizationModelId(id.get());

        this.openFgaClient = client;
        return client;
    }

    /**
     * Retrieves the value of an environment variable.
     *
     * @param key Key of the environment variable.
     * @return Value of the environment variable.
     *
     * @throws FgaInvalidParameterException If the key is blank or the environment variable is not set or is blank.
     */
    private String getEnvironmentVariable(final @NonNull String key) throws FgaInvalidParameterException {
        if (key.isBlank()) {
            throw new IllegalArgumentException("The key for the environment variable cannot be blank.");
        }

        final var value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new FgaInvalidParameterException("The '" + key + "' environment variable is either not set or is blank.");
        }
        return value;
    }
}
