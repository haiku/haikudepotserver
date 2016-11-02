-- Adds change log data to the package.

CREATE TABLE haikudepot.pkg_changelog (
  id BIGINT NOT NULL,
  create_timestamp TIMESTAMP NOT NULL,
  modify_timestamp TIMESTAMP NOT NULL,
  pkg_id BIGINT NOT NULL,
  content VARCHAR(65535) NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.pkg_changelog_seq START WITH 4121 INCREMENT BY 1;

ALTER TABLE haikudepot.pkg_changelog ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);

CREATE UNIQUE INDEX pkg_changelog_idx01 ON haikudepot.pkg_changelog(pkg_id);

INSERT INTO
  haikudepot.permission (id,code,name)
VALUES
  (nextval('haikudepot.permission_seq'), 'pkg_editchangelog', 'Edit Package Changelog');