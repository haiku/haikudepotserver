-- In May 2015, people started to want to add in other repositories and it became clear that the
-- data structure that was in place was not well suited to supporting that.  This script will
-- modify the data-structures so that "other repositories" are better catered for.

-- STEP 1; MIGRATE EXISTING REPOSITORY TO REPOSITORY_SOURCE AND CREATE REPOSITORY

ALTER TABLE haikudepot.repository RENAME TO repository_source;
CREATE SEQUENCE haikudepot.repository_source_seq START WITH 9173 INCREMENT BY 1;
ALTER TABLE haikudepot.repository_source DROP CONSTRAINT repository_architecture_id_fkey;
ALTER TABLE haikudepot.repository_source DROP COLUMN architecture_id;
ALTER TABLE haikudepot.repository_source DROP COLUMN create_timestamp;
ALTER TABLE haikudepot.repository_source DROP COLUMN modify_timestamp;
ALTER INDEX haikudepot.repository_pkey RENAME TO repository_source_pkey;
ALTER INDEX haikudepot.repository_idx01 RENAME TO repository_source_idx01;
-- column was dropped.
-- ALTER INDEX haikudepot.repository_idx02 RENAME TO repository_source_idx02;

ALTER TABLE haikudepot.pkg_version RENAME COLUMN repository_id TO repository_source_id;
ALTER TABLE haikudepot.pkg_version RENAME CONSTRAINT pkg_version_repository_id_fkey TO pkg_version_repository_source_id_fkey;
DROP INDEX version_idx01;

CREATE UNIQUE INDEX pkg_version_idx01 ON haikudepot.pkg_version (
  pkg_id, major, minor, micro, revision, pre_release, architecture_id, repository_source_id
);

CREATE TABLE haikudepot.repository (
  id BIGINT NOT NULL,
  code VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(1024),
  active BOOLEAN NOT NULL,
  information_url VARCHAR(1024),
  create_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  modify_timestamp TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  PRIMARY KEY(id)
);

INSERT INTO haikudepot.repository
(id,code,name,description,active,information_url,create_timestamp,modify_timestamp)
VALUES
  (
    100,
    'haikuports', 'Haiku Ports',
    'HaikuPorts is a centralized collection of software ported to the Haiku platform.',
    TRUE, 'http://bb.haikuports.org/haikuports/wiki/Home',
      now(),now()
  );

ALTER TABLE haikudepot.repository_source ADD COLUMN repository_id BIGINT;
UPDATE haikudepot.repository_source SET repository_id = 100;
ALTER TABLE haikudepot.repository_source ALTER COLUMN repository_id SET NOT NULL;

ALTER TABLE haikudepot.repository_source
  ADD CONSTRAINT repository_source_repository_fkey
  FOREIGN KEY (repository_id)
  REFERENCES haikudepot.repository (id)
  DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX repository_source_idx03 ON haikudepot.repository_source (repository_id, url);
CREATE UNIQUE INDEX repository_idx01 ON haikudepot.repository (code);

-- STEP 2; USER RATINGS

CREATE TABLE haikudepot.pkg_user_rating_aggregate (
  id BIGINT,
  pkg_id BIGINT NOT NULL,
  repository_id BIGINT NOT NULL,
  derived_rating DOUBLE PRECISION NOT NULL,
  derived_rating_sample_size INTEGER NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE haikudepot.pkg DROP COLUMN derived_rating;
ALTER TABLE haikudepot.pkg DROP COLUMN derived_rating_sample_size;

CREATE SEQUENCE haikudepot.pkg_user_rating_aggregate_seq START WITH 4184 INCREMENT BY 1;

CREATE UNIQUE INDEX pkg_user_rating_aggregate_idx01 ON haikudepot.pkg_user_rating_aggregate (repository_id, pkg_id);

ALTER TABLE haikudepot.pkg_user_rating_aggregate
  ADD CONSTRAINT pkg_user_rating_aggregate_repository_fkey
  FOREIGN KEY (repository_id)
  REFERENCES haikudepot.repository (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_user_rating_aggregate
  ADD CONSTRAINT pkg_user_rating_aggregate_pkg_fkey
  FOREIGN KEY (pkg_id)
  REFERENCES haikudepot.pkg (id)
  DEFERRABLE INITIALLY DEFERRED;

-- STEP 3; PROMINENCE

CREATE TABLE haikudepot.pkg_prominence (
  id BIGINT,
  pkg_id BIGINT NOT NULL,
  repository_id BIGINT NOT NULL,
  prominence_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.pkg_prominence_seq START WITH 2166 INCREMENT BY 1;

CREATE UNIQUE INDEX pkg_prominence_idx01 ON haikudepot.pkg_prominence (repository_id, pkg_id);

ALTER TABLE haikudepot.pkg_prominence
  ADD CONSTRAINT pkg_prominence_repository_fkey
  FOREIGN KEY (repository_id)
  REFERENCES haikudepot.repository (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_prominence
  ADD CONSTRAINT pkg_prominence_pkg_fkey
  FOREIGN KEY (pkg_id)
  REFERENCES haikudepot.pkg (id)
  DEFERRABLE INITIALLY DEFERRED;

INSERT INTO haikudepot.pkg_prominence (id, pkg_id, repository_id, prominence_id)
  (SELECT
     nextval('haikudepot.pkg_prominence_seq'),
     p.id, 100, p.prominence_id
   FROM
     haikudepot.pkg p);

ALTER TABLE haikudepot.pkg DROP COLUMN prominence_id;

-- STEP 4; UPDATE CODES A BIT

UPDATE haikudepot.repository_source SET code='haikuportsx8664' WHERE code='haikuports64';
UPDATE haikudepot.repository_source SET code='haikuportsx86' WHERE code='haikuports';
UPDATE haikudepot.repository_source SET code='haikuportsx86gcc2' WHERE code='haikuportsgcc2';





