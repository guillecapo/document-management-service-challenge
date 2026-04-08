CREATE SCHEMA IF NOT EXISTS document_schema;

CREATE TABLE document_schema.documents (
    id          VARCHAR(26)  NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    file_size   BIGINT       NOT NULL,
    file_type   VARCHAR(127) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id)
);

CREATE INDEX idx_documents_user_id  ON document_schema.documents (user_id);
CREATE INDEX idx_documents_name     ON document_schema.documents (name);
CREATE INDEX idx_documents_created_at ON document_schema.documents (created_at DESC);

CREATE TABLE document_schema.document_tags (
    id          BIGSERIAL    NOT NULL,
    document_id VARCHAR(26)  NOT NULL,
    tag         VARCHAR(255) NOT NULL,
    CONSTRAINT pk_document_tags PRIMARY KEY (id),
    CONSTRAINT fk_document_tags_document FOREIGN KEY (document_id)
        REFERENCES document_schema.documents (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_document_tags_tag         ON document_schema.document_tags (tag);
CREATE INDEX idx_document_tags_document_id ON document_schema.document_tags (document_id);
