-- ------------------------------------------------------
-- IMAGES RELATED TO PACKAGES
-- ------------------------------------------------------

CREATE SEQUENCE haikudepot.pkg_screenshot_seq START WITH 1922 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.pkg_screenshot_image_seq START WITH 7332 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.media_type_seq START WITH 4195 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.pkg_icon_seq START WITH 3385 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.pkg_icon_image_seq START WITH 2175 INCREMENT BY 1;

CREATE TABLE haikudepot.media_type (code VARCHAR(255) NOT NULL, id BIGINT NOT NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.pkg_icon (id BIGINT NOT NULL, media_type_id BIGINT NOT NULL, pkg_id BIGINT NOT NULL, size INTEGER NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.pkg_icon_image (data BYTEA NOT NULL, id BIGINT NOT NULL, pkg_icon_id BIGINT NOT NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.pkg_screenshot (code CHAR(36) NOT NULL, id BIGINT NOT NULL, ordering INTEGER NOT NULL, pkg_id BIGINT NOT NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.pkg_screenshot_image (data BYTEA NOT NULL, id BIGINT NOT NULL, media_type_id BIGINT NOT NULL, pkg_screenshot_id BIGINT NOT NULL, PRIMARY KEY (id));

ALTER TABLE haikudepot.pkg_icon ADD FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type (id);
ALTER TABLE haikudepot.pkg_icon ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);
ALTER TABLE haikudepot.pkg_icon_image ADD FOREIGN KEY (pkg_icon_id) REFERENCES haikudepot.pkg_icon (id);
ALTER TABLE haikudepot.pkg_screenshot ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);
ALTER TABLE haikudepot.pkg_screenshot_image ADD FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type (id);
ALTER TABLE haikudepot.pkg_screenshot_image ADD FOREIGN KEY (pkg_screenshot_id) REFERENCES haikudepot.pkg_screenshot (id);

CREATE UNIQUE INDEX pkg_icon_idx01 ON haikudepot.pkg_icon(size,media_type_id);

INSERT INTO haikudepot.media_type (id,code) VALUES (nextval('media_type_seq'), 'image/png');
