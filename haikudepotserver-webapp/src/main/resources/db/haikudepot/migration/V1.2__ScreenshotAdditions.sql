-- ------------------------------------------------------
-- ADDITIONAL MATERIAL FOR SCREENSHOTS
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg_screenshot ADD COLUMN length INTEGER NOT NULL;
ALTER TABLE haikudepot.pkg_screenshot ADD COLUMN width INTEGER NOT NULL;
ALTER TABLE haikudepot.pkg_screenshot ADD COLUMN height INTEGER NOT NULL;
ALTER TABLE haikudepot.pkg_screenshot ADD COLUMN create_timestamp TIMESTAMP NOT NULL;
ALTER TABLE haikudepot.pkg_screenshot ADD COLUMN modify_timestamp TIMESTAMP NOT NULL;