--
-- Copyright 2020, Andrew Lindesay
-- Distributed under the terms of the MIT License.
--
-- -------------
--
-- This script is designed to be run on an HDS database in order to remove all
-- data related to a repository by its code.  It should be run inside a
-- transaction.  For example, if this were run from the standard `psql` tool
-- then the commands issued would be something like;
--
-- BEGIN;
-- CREATE OR REPLACE FUN...
-- SELECT hds_purge_repository('somerepocode');
-- DROP FUNCTION hds_purge_repository;
-- COMMIT;
--
-- Note that the repository code given in this script is merely
-- an example; 'somerepocode' should be exchanged for the code of the
-- repository that should be purged from the database.
--
-- -------------

CREATE OR REPLACE FUNCTION
  hds_purge_repository(repository_code TEXT)
RETURNS void
LANGUAGE PLPGSQL
AS $$
DECLARE
BEGIN
  CREATE TEMPORARY TABLE tmp_package_version (id bigint) ON COMMIT DROP;

  INSERT INTO tmp_package_version (id)
  SELECT pv.id
  FROM haikudepot.pkg_version pv
           JOIN haikudepot.repository_source rs ON rs.id = pv.repository_source_id
           JOIN haikudepot.repository r ON r.id = rs.repository_id
  WHERE r.code = repository_code;

  DELETE FROM haikudepot.pkg_version_url WHERE pkg_version_id IN (
    SELECT id FROM tmp_package_version);
  DELETE FROM haikudepot.pkg_version_copyright WHERE pkg_version_id IN (
      SELECT id FROM tmp_package_version);
  DELETE FROM haikudepot.pkg_version_license WHERE pkg_version_id IN (
      SELECT id FROM tmp_package_version);
  DELETE FROM haikudepot.pkg_version_localization WHERE pkg_version_id IN (
      SELECT id FROM tmp_package_version);
  DELETE FROM haikudepot.user_rating WHERE pkg_version_id IN (
      SELECT id FROM tmp_package_version);
  DELETE FROM haikudepot.pkg_version WHERE id IN (
      SELECT id FROM tmp_package_version);

  DELETE FROM haikudepot.pkg_user_rating_aggregate WHERE repository_id = (
      SELECT r.id from haikudepot.repository r WHERE r.code = repository_code);
  DELETE FROM haikudepot.pkg_prominence WHERE repository_id = (
      SELECT r.id from haikudepot.repository r WHERE r.code = repository_code);

  DELETE FROM haikudepot.repository_source_mirror
  WHERE repository_source_id IN (
      SELECT rs.id FROM haikudepot.repository_source rs
        JOIN haikudepot.repository r ON r.id = rs.repository_id
    WHERE r.code = repository_code);

  DELETE FROM haikudepot.repository_source_extra_identifier
  WHERE repository_source_id IN (
      SELECT rs.id FROM haikudepot.repository_source rs
        JOIN haikudepot.repository r ON r.id = rs.repository_id
      WHERE r.code = repository_code);

  DELETE FROM haikudepot.repository_source
  WHERE repository_id = (
      SELECT r.id from haikudepot.repository r WHERE r.code = repository_code);

  DELETE FROM haikudepot.repository WHERE code = repository_code;

  DROP TABLE tmp_package_version;
END; $$;

SELECT hds_purge_repository('somerepocode');
-- ^^ example repository code
DROP FUNCTION hds_purge_repository;