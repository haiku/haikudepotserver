-- re-establish the constraints to the supplement table.  This has to be done in a separate migration because it is not
-- possible to mutate the data and then add constraints in the same commit.  This is a bit risky, but can be tested
-- prior.

ALTER TABLE haikudepot.pkg_changelog ADD CONSTRAINT pkg_changelog_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_icon ADD CONSTRAINT pkg_icon_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_localization ADD CONSTRAINT pkg_localization_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_pkg_category ADD CONSTRAINT pkg_pkg_category_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.pkg_screenshot ADD CONSTRAINT pkg_screenshots_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;

-- constraint for the new table.

ALTER TABLE haikudepot.pkg ADD CONSTRAINT pkg_pkg_supplement_id_fkey
  FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
  DEFERRABLE INITIALLY DEFERRED;