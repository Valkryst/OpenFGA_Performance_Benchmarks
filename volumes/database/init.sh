#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$DB_USERNAME" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS dblink;

    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_database WHERE datname = '${OPENFGA_DB_NAME}') THEN
            PERFORM dblink('dbname=postgres', 'CREATE DATABASE ${OPENFGA_DB_NAME}');
        END IF;
    END
    \$\$;
EOSQL