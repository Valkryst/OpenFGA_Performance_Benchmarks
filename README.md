[![Java CI with Maven](https://github.com/Valkryst/OpenFGA_Performance_Benchmarks/actions/workflows/maven.yml/badge.svg)](https://github.com/Valkryst/OpenFGA_Performance_Benchmarks/actions/workflows/maven.yml)

## Table of Contents

* [Results](#results)
* [Local Setup](#local-setup)
  * [Generate Certificates](#generate-certificates)
  * [Running Tests](#running-tests)
    * [Start](#start)
    * [Stop](#stop)
* [Misc. Notes](#misc-notes)
  * [OpenFGA API Keys](#openfga-api-keys)
  * [Use of _System.exit(1)_](#use-of-systemexit1)

## Results

These are the results of my most-recent run of the benchmarks.

| Benchmark                                            | Node  | Cnt | Score   | Error   | Units |
|------------------------------------------------------|-------|-----|---------|---------|-------|
| RelationshipCreation.benchmark                       | thrpt | 25  | 305.050 | ±10.125 | ops/s |
| RelationshipDeletion.benchmark                       | thrpt | 25  | 326.905 | ±10.668 | ops/s |
| RelationshipLookup.benchmarkExistingRelationships    | thrpt | 25  | 470.288 | ±10.133 | ops/s |
| RelationshipLookup.benchmarkNonexistentRelationships | thrpt | 25  | 457.692 | ±20.235 | ops/s |
| TransitiveRelationshipLookup.benchmark               | thrpt | 25  | ???     | ±???    | ops/s |

## Local Setup

### Generate Certificates

You _must_ have Java & OpenSSL installed to be able to generate the certificates.

```shell
rm ./volumes/cacerts/tls.crt ./volumes/cacerts/tls.der ./volumes/cacerts/tls.jks ./volumes/cacerts/tls.key

keytool -genkeypair -alias openfga -keyalg RSA -keystore ./volumes/cacerts/tls.jks -storepass changeit -dname "CN=openfga, OU=IT, O=MyCompany, L=MyCity, S=MyState, C=US" -ext "SAN=DNS:localhost,DNS:openfga,DNS:postgres,IP:127.0.0.1"
keytool -export -alias openfga -file ./volumes/cacerts/tls.der -keystore ./volumes/cacerts/tls.jks -storepass changeit
openssl x509 -inform DER -in ./volumes/cacerts/tls.der -out ./volumes/cacerts/tls.crt -outform PEM
openssl pkcs12 -in ./volumes/cacerts/tls.jks -nocerts -nodes -out ./volumes/cacerts/tls.key -passin pass:changeit
```

### Running Tests

#### Start

```shell
docker compose up --build
```

#### Stop

```shell
docker compose down --remove-orphans
```

## Misc. Notes

- We _do not_ persist the PostgreSQL DB between runs. This is to ensure that the database is always in a clean state and
  that your storage is not permanently consumed by the database.

### Use of `System.exit(1)`

In almost all instances, I have explicitly used `System.exit(1)` when some part of the benchmark fails. I did this to
ensure immediate feedback, so that any issues can be addressed and resolved without wasting any time. JMH may be able
to handle thrown exceptions in the `@Benchmark` functions, but I did not want to take any chances.