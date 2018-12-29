-- This change allows for the OPTIONAL storage of an additional URL for
-- the pull-down of data for the repository source.  Also there is the
-- introduction of a password hash for authenticating access to the
-- repository update functions.

ALTER TABLE haikudepot.repository ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE haikudepot.repository ADD COLUMN password_salt VARCHAR(255);
UPDATE haikudepot.repository SET password_salt = MD5(id || random()::text);

ALTER TABLE haikudepot.repository_source ADD COLUMN forced_internal_base_url VARCHAR(1024);

-- a small addition to store the time of last import against the repository.

ALTER TABLE haikudepot.repository_source
  ADD COLUMN last_import_timestamp TIMESTAMP;