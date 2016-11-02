-- ------------------------------------------------------
-- ADD SOME MODIFY + CREATE TIMESTAMPS
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN modify_timestamp TIMESTAMP;
ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN create_timestamp TIMESTAMP;
UPDATE haikudepot.pkg_version_localization pvl SET modify_timestamp=(SELECT create_timestamp FROM haikudepot.pkg_version pv WHERE pv.id=pvl.pkg_version_id);
UPDATE haikudepot.pkg_version_localization pvl SET create_timestamp=modify_timestamp;