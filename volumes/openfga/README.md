The "source of truth" is `model.fga`. Any changes to the Authorization Model _must_ be done to that file and then
copied-over to `model.json`.

The OpenFGA API doesn't support its own FGA files, so we need to convert our Authorization Model into JSON. The JSON
file is then used by the tests to create ephemeral OpenFGAClient objects with their own Store and Authorization Model,
so that we can do end-to-end testing.

To generate the `model.json` file, run the following command and then commit the changes:

```shell
docker compose run openfga fga model transform --file /tmp/openfga/model.json --input-format json --output-format fga > ./volumes/openfga/model.fga
```

Ensure that the `model.json` file uses the UTF-8 format, or your tests may fail with an exception similar to the
following:

```shell
com.fasterxml.jackson.core.JsonParseException: Unexpected character ('�' (code 65533 / 0xfffd)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]
```