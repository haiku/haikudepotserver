-- The payload length incorporates the length of the pkg version download into the
-- PkgVersion entity.

ALTER TABLE haikudepot.pkg_version ADD COLUMN payload_length BIGINT;

-- store a title for the package.

CREATE TABLE haikudepot.pkg_localization (
  id BIGINT NOT NULL,
  create_timestamp TIMESTAMP NOT NULL,
  modify_timestamp TIMESTAMP NOT NULL,
  pkg_id BIGINT NOT NULL,
  natural_language_id BIGINT NOT NULL,
  title VARCHAR(1024),
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.pkg_localization_seq START WITH 8821 INCREMENT BY 1;

ALTER TABLE haikudepot.pkg_localization ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);
ALTER TABLE haikudepot.pkg_localization ADD FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language (id);

CREATE UNIQUE INDEX pkg_localization_idx01 ON haikudepot.pkg_localization(pkg_id,natural_language_id);

