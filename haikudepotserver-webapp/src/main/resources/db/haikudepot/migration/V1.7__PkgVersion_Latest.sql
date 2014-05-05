-- ------------------------------------------------------
-- NEED A FLAG TO MANAGE THE LATEST VERSION OF A PKG
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg_version ADD COLUMN is_latest BOOL;
UPDATE haikudepot.pkg_version SET is_latest=false;
ALTER TABLE haikudepot.pkg_version ALTER COLUMN is_latest SET NOT NULL;

-- The version ordering is quite complex, so it is going to be difficult to
-- get the latest version 100% perfect, but the reality is that this has not
-- been deployed for too long and because of this it will be possible to just
-- take the latest version and assume that this is the latest one.

UPDATE haikudepot.pkg_version SET is_latest=true WHERE id IN (
  SELECT
    pv.id
  FROM
    haikudepot.pkg_version pv
    JOIN haikudepot.pkg p ON pv.pkg_id = p.id
    JOIN haikudepot.architecture a ON pv.architecture_id = a.id
  WHERE
    pv.id = (
      SELECT
        pv2.id
      FROM
        haikudepot.pkg_version pv2
      WHERE
        pv2.pkg_id = p.id
        AND pv2.architecture_id = a.id
      ORDER BY
        pv2.create_timestamp DESC
      LIMIT 1)
);

-- would be nice to get a unique partial index across the architecture, pkg and latest flag, but
-- it seems that there is no way to do this (at the time of writing) where a deferred check is
-- also achieved.

-- speaking of which all constraints should be deferred.  These have to be dropped and then re-created.

ALTER TABLE haikudepot.user_rating DROP CONSTRAINT user_rating_pkg_version_id_fkey;
ALTER TABLE haikudepot.user_rating DROP CONSTRAINT user_rating_user_id_fkey;
ALTER TABLE haikudepot.user_rating DROP CONSTRAINT user_rating_user_rating_stability_id_fkey;
ALTER TABLE haikudepot.user_rating DROP CONSTRAINT user_rating_natural_language_id_fkey;
ALTER TABLE haikudepot.user DROP CONSTRAINT user_natural_language_id_fkey;
ALTER TABLE haikudepot.pkg_pkg_category DROP CONSTRAINT pkg_pkg_category_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_pkg_category DROP CONSTRAINT pkg_pkg_category_pkg_category_id_fkey;
ALTER TABLE haikudepot.pkg_icon DROP CONSTRAINT pkg_icon_media_type_id_fkey;
ALTER TABLE haikudepot.pkg_icon DROP CONSTRAINT pkg_icon_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_icon_image DROP CONSTRAINT pkg_icon_image_pkg_icon_id_fkey;
ALTER TABLE haikudepot.pkg_screenshot DROP CONSTRAINT pkg_screenshot_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_screenshot_image DROP CONSTRAINT pkg_screenshot_image_media_type_id_fkey;
ALTER TABLE haikudepot.pkg_screenshot_image DROP CONSTRAINT pkg_screenshot_image_pkg_screenshot_id_fkey;
ALTER TABLE haikudepot.pkg DROP CONSTRAINT pkg_publisher_id_fkey;
ALTER TABLE haikudepot.repository DROP CONSTRAINT repository_architecture_id_fkey;
ALTER TABLE haikudepot.pkg_version DROP CONSTRAINT pkg_version_architecture_id_fkey;
ALTER TABLE haikudepot.pkg_version DROP CONSTRAINT pkg_version_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_version DROP CONSTRAINT pkg_version_repository_id_fkey;
ALTER TABLE haikudepot.pkg_version_copyright DROP CONSTRAINT pkg_version_copyright_pkg_version_id_fkey;
ALTER TABLE haikudepot.pkg_version_localization DROP CONSTRAINT pkg_version_localization_pkg_version_id_fkey;
ALTER TABLE haikudepot.pkg_version_license DROP CONSTRAINT pkg_version_license_pkg_version_id_fkey;
ALTER TABLE haikudepot.pkg_version_url DROP CONSTRAINT pkg_version_url_pkg_url_type_id_fkey;
ALTER TABLE haikudepot.pkg_version_url DROP CONSTRAINT pkg_version_url_pkg_version_id_fkey;

ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (user_rating_stability_id) REFERENCES haikudepot.user_rating_stability (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.user ADD FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_pkg_category ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_pkg_category ADD FOREIGN KEY (pkg_category_id) REFERENCES haikudepot.pkg_category (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_icon ADD FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_icon ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_icon_image ADD FOREIGN KEY (pkg_icon_id) REFERENCES haikudepot.pkg_icon (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_screenshot ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_screenshot_image ADD FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_screenshot_image ADD FOREIGN KEY (pkg_screenshot_id) REFERENCES haikudepot.pkg_screenshot (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg ADD FOREIGN KEY (publisher_id) REFERENCES haikudepot.publisher (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.repository ADD FOREIGN KEY (architecture_id) REFERENCES haikudepot.architecture (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version ADD FOREIGN KEY (architecture_id) REFERENCES haikudepot.architecture (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version ADD FOREIGN KEY (repository_id) REFERENCES haikudepot.repository (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version_copyright ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version_localization ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version_license ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version_url ADD FOREIGN KEY (pkg_url_type_id) REFERENCES haikudepot.pkg_url_type (id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE haikudepot.pkg_version_url ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id) DEFERRABLE INITIALLY DEFERRED;
