-- #195: the central schema registry. Each row is one immutable, published version of a fact
-- schema, named urn:schema:<name>:v<version>.
CREATE TABLE IF NOT EXISTS fact_schemas (
    id          UUID PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    version     INT NOT NULL,
    schema_json TEXT NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT uq_fact_schemas_name_version UNIQUE (name, version)
);
