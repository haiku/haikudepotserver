
ALTER TABLE haikudepot.repository_source
    ADD COLUMN architecture_id BIGINT;

ALTER TABLE haikudepot.repository_source
    ADD FOREIGN KEY (architecture_id)
        REFERENCES haikudepot.architecture (id)
        DEFERRABLE INITIALLY DEFERRED;

