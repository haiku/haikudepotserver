
-- ------------------------------------------------------
-- PERMISSIONS WE DON'T NEED
-- ------------------------------------------------------

DELETE FROM haikudepot.permission WHERE code NOT IN (
    'pkg_editicon',
    'pkg_editscreenshot',
    'pkg_editcategories',
    'pkg_editversionlocalization');

-- ------------------------------------------------------
-- CHANGE OF PLAN - DON'T NEED PUBLIC PERMISSIONS OR
-- REPOSITORY PERMISSIONS EITHER
-- ------------------------------------------------------

DROP SEQUENCE haikudepot.permission_public_repository_seq;
DROP TABLE haikudepot.permission_public_repository;
DROP SEQUENCE haikudepot.permission_user_repository_seq;
DROP TABLE haikudepot.permission_user_repository;

-- ------------------------------------------------------
-- CREATE TIMESTAMPS ON RULES
-- ------------------------------------------------------

ALTER TABLE haikudepot.permission_user_pkg ADD COLUMN create_timestamp TIMESTAMP NOT NULL;
ALTER TABLE haikudepot.permission_user_pkg ALTER COLUMN pkg_id DROP NOT NULL;

-- ------------------------------------------------------
-- SNEAK IN A COUPLE OF ADDITIONAL CHANGES...
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN modify_timestamp TIMESTAMP;
ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN create_timestamp TIMESTAMP;
UPDATE haikudepot.pkg_version_localization pvl SET modify_timestamp=(SELECT create_timestamp FROM haikudepot.pkg_version pv WHERE pv.id=pvl.pkg_version_id);
UPDATE haikudepot.pkg_version_localization pvl SET create_timestamp=modify_timestamp;
ALTER TABLE haikudepot.pkg_version_localization ALTER COLUMN modify_timestamp SET NOT NULL;
ALTER TABLE haikudepot.pkg_version_localization ALTER COLUMN create_timestamp SET NOT NULL;