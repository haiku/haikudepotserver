-- ------------------------------------------------------
-- ADD SOME MODIFY + CREATE TIMESTAMPS
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg_version_localization ALTER COLUMN modify_timestamp SET NOT NULL;
ALTER TABLE haikudepot.pkg_version_localization ALTER COLUMN create_timestamp SET NOT NULL;