CREATE EXTENSION IF NOT EXISTS vector;

-- one row per ingested document 
CREATE TABLE IF NOT EXISTS document (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    filename    TEXT        NOT NULL,
    cdc_type    TEXT,                          -- smth like 'reseau', 'logiciel', 'maintenance'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- chunks + their embeddings. dim = 384 
CREATE TABLE IF NOT EXISTS chunk (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id  BIGINT      NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index  INT         NOT NULL,
    content      TEXT        NOT NULL,
    embedding    vector(384) NOT NULL
);

-- approximate-nearest-neighbour index allows a fast cosine search
CREATE INDEX IF NOT EXISTS chunk_embedding_hnsw
    ON chunk USING hnsw (embedding vector_cosine_ops);

-- filter search by document type 
CREATE INDEX IF NOT EXISTS document_cdc_type_idx ON document (cdc_type);

CREATE TABLE IF NOT EXISTS cdc (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       TEXT        NOT NULL,
    cdc_type    TEXT,
    status      TEXT        NOT NULL DEFAULT 'DRAFT',
    body        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
