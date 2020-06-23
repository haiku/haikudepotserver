-- it is possible to manually add identifiers to the repository source.

CREATE TABLE haikudepot.repository_source_extra_identifier (
  id BIGINT NOT NULL,
  identifier VARCHAR(4096) NOT NULL,
  repository_source_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE haikudepot.repository_source_extra_identifier
  ADD FOREIGN KEY (repository_source_id)
    REFERENCES haikudepot.repository_source (id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE SEQUENCE haikudepot.repository_source_extra_identifier_seq
  START WITH 913376 INCREMENT BY 1;

ALTER TABLE haikudepot.repository_source
    ALTER COLUMN identifier TYPE VARCHAR(4096);

CREATE UNIQUE INDEX repository_source_extra_identifier_idx01
    ON haikudepot.repository_source_extra_identifier(repository_source_id, identifier);
