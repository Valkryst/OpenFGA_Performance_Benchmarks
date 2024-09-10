[![Java CI with Maven](https://github.com/Valkryst/OpenFGA_Performance_Benchmarks/actions/workflows/maven.yml/badge.svg)](https://github.com/Valkryst/OpenFGA_Performance_Benchmarks/actions/workflows/maven.yml)

## Table of Contents

* [Local Setup](#local-setup)
  * [Generate Certificates](#generate-certificates)
  * [Managing Containers](#managing-containers)
    * [Build](#build)
    * [Start](#start)
    * [Stop](#stop)
* [Misc. Notes](#misc-notes)
  * [OpenFGA API Keys](#openfga-api-keys)
  * [Use of _System.exit(1)_](#use-of-systemexit1)
  * [Verify OpenFGA Migrations](#verify-openfga-migrations)

## Local Setup

### Generate Certificates

You _must_ have Java & OpenSSL installed to be able to generate the certificates.

```shell
rm ./volumes/cacerts/tls.crt ./volumes/cacerts/tls.der ./volumes/cacerts/tls.jks ./volumes/cacerts/tls.key

keytool -genkeypair -alias openfga -keyalg RSA -keystore ./volumes/cacerts/tls.jks -storepass changeit -dname "CN=openfga, OU=IT, O=MyCompany, L=MyCity, S=MyState, C=US"
keytool -export -alias openfga -file ./volumes/cacerts/tls.der -keystore ./volumes/cacerts/tls.jks -storepass changeit
openssl x509 -inform DER -in ./volumes/cacerts/tls.der -out ./volumes/cacerts/tls.crt -outform PEM
openssl pkcs12 -in ./volumes/cacerts/tls.jks -nocerts -nodes -out ./volumes/cacerts/tls.key -passin pass:changeit
```

### Managing Containers

#### Build

If your only changes are to the `src` folder, then run `docker compose build application`. Otherwise run `docker compose
build`.

#### Start

```shell
docker compose up -d
```

#### Stop

```shell
docker compose down --remove-orphans
```

## Misc. Notes

- We _do not_ persist the PostgreSQL DB between runs. This is to ensure that the database is always in a clean state and
  that your storage is not permanently consumed by the database.

### OpenFGA API Keys

You can see the existing API keys beside the `--authn-preshared-keys` parameter which is under the `openfga` container
definition within the `docker-compose.yml` file. There must be at least one key defined, but you may also add additional
keys. They must be separated by a comma.

### Use of `System.exit(1)`

In almost all instances, I have explicitly used `System.exit(1)` when some part of the benchmark fails. I did this to
ensure immediate feedback, so that any issues can be addressed and resolved without wasting any time. JMH may be able
to handle thrown exceptions in the `@Benchmark` functions, but I did not want to take any chances.

### Verify OpenFGA Migrations

If there is ever a need to verify that the DB is not persisted between runs, and/or that the OpenFGA migrations are
running correctly, you can run the following commands:

```shell
# Start PostgreSQL, then log in with your DB client of choice and you'll see that the DB is empty.
docker compose up postgres -d

# Run the OpenFGA migrations, then refresh your view of the DB and you'll see that the DB has been populated.
docker compose up migrate -d
```