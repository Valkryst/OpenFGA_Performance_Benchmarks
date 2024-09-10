package com.valkryst.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ApiToken;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.configuration.Credentials;
import dev.openfga.sdk.api.model.CreateStoreRequest;
import dev.openfga.sdk.api.model.WriteAuthorizationModelRequest;
import dev.openfga.sdk.errors.FgaApiValidationError;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class BenchmarkBase {
    /** Client used when interacting with the OpenFGA API. */
    protected OpenFgaClient openFgaClient;

    public BenchmarkBase() {
        final var config = new ClientConfiguration();
        config.apiUrl(System.getenv("OPENFGA_API_URL"));
        config.credentials(new Credentials(new ApiToken(System.getenv("OPENFGA_API_TOKEN"))));

        try {
            openFgaClient = new OpenFgaClient(config);
        } catch (final FgaInvalidParameterException e) {
            e.printStackTrace();
            System.exit(1);
        }

        // We can't write the authorization model until a valid Store ID has been set.
        final var storeId = createStore(openFgaClient);
        if (storeId.isEmpty()) {
            System.exit(1);
        } else {
            openFgaClient.setStoreId(storeId.get());
        }

        try {
            final var mapper = new ObjectMapper().findAndRegisterModules();
            final var response = openFgaClient.writeAuthorizationModel(
                // 2024-09-09 12:00:01 Validation Error: {"code":"invalid_authorization_model","message":"the relation type 'user#member' on 'member' in object type 'group' is not valid"}
                mapper.readValue(
                    """
                        {
                            "schema_version": "1.1",
                            "type_definitions": [
                                {
                                    "type": "group",
                                    "relations": {
                                        "member": {
                                            "this": {}
                                        },
                                        "subgroup": {
                                            "this": {}
                                        }
                                    },
                                    "metadata": {
                                        "relations": {
                                            "member": {
                                                "directly_related_user_types": [
                                                    {
                                                        "type": "user"
                                                    }
                                                ]
                                            },
                                            "subgroup": {
                                                "directly_related_user_types": [
                                                    {
                                                        "type": "group"
                                                    }
                                                ]
                                            }
                                        }
                                    }
                                },
                                {
                                    "type": "report",
                                    "relations": {
                                        "reader": {
                                            "this": {}
                                        }
                                    },
                                    "metadata": {
                                        "relations": {
                                            "reader": {
                                                "directly_related_user_types": [
                                                    {
                                                        "type": "group"
                                                    },
                                                    {
                                                        "type": "user"
                                                    }
                                                ]
                                            }
                                        }
                                    }
                                },
                                {
                                    "type": "user"
                                }
                            ]
                        }
                    """,
                    WriteAuthorizationModelRequest.class
                )
            ).get();

            openFgaClient.setAuthorizationModelId(response.getAuthorizationModelId());
        } catch (final ExecutionException e) {
            final var cause = e.getCause();
            if (cause instanceof FgaApiValidationError) {
                System.err.println("Validation Error: " + ((FgaApiValidationError) cause).getResponseData());
            } else {
                e.printStackTrace();
            }
            System.exit(1);
        } catch (final FgaInvalidParameterException | InterruptedException | JsonProcessingException e) {
            e.printStackTrace();
            System.exit(1);
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

            try {
                final var response = openFgaClient.write(body, null).get();
                if (response.getStatusCode() != 200) {
                    System.err.println(response.getRawResponse());
                    System.exit(1);
                }
            } catch (final FgaInvalidParameterException | InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            } catch (final ExecutionException e) {
                e.getCause().printStackTrace();
                System.exit(1);
            }

            groups.addAll(tuples);
            totalGroups -= batchSize;
        }

        return groups;
    }

    /**
     * Creates a new Store VIA the OpenFGA API.
     *
     * @param client Client used to interact with the OpenFGA API.
     *
     * @return ID of the created store, or an empty optional if the store could not be created.
     */
    private Optional<String> createStore(final @NonNull OpenFgaClient client) {
        final var body = new CreateStoreRequest();
        body.setName(UUID.randomUUID().toString());

        try {
            return client.createStore(body).get().getId().describeConstable();
        } catch (final ExecutionException e) {
            final var cause = e.getCause();

            if (cause instanceof FgaApiValidationError) {
                System.err.println("Response Data:\t" + ((FgaApiValidationError) cause).getResponseData());
            } else {
                e.printStackTrace();
            }

            return Optional.empty();
        } catch (final FgaInvalidParameterException | InterruptedException e) {
            e.printStackTrace();
            return Optional.empty();
        }
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

            try {
                final var response = openFgaClient.write(body, null).get();
                if (response.getStatusCode() != 200) {
                    System.err.println(response.getRawResponse());
                    System.exit(1);
                }
            } catch (final FgaInvalidParameterException | InterruptedException e) {
                e.printStackTrace();
                System.exit(1);
            } catch (final ExecutionException e) {
                e.getCause().printStackTrace();
                System.exit(1);
            }

            users.addAll(tuples);
            totalUsers -= batchSize;
        }

        return users;
    }
}
