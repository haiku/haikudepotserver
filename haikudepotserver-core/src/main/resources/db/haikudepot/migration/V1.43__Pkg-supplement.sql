-- over time, it has become clear that some packages have shared localizations,
-- icons, screenshots etc... and that this is because they are variants of each
-- other. Such packages have known suffixes on their names so they can be easily
-- detected.  In such cases, they previously had separate supplementary data and
-- this change will bring that data together so that they are then sharing the
-- data.

-- drop the necessary constraints on the subordinate tables.

ALTER TABLE haikudepot.pkg_changelog DROP CONSTRAINT pkg_changelog_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_icon DROP CONSTRAINT pkg_icon_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_localization DROP CONSTRAINT pkg_localization_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_pkg_category DROP CONSTRAINT pkg_pkg_category_pkg_id_fkey;
ALTER TABLE haikudepot.pkg_screenshot DROP CONSTRAINT pkg_screenshot_pkg_id_fkey;

-- add the new table where the package data will be stored.

CREATE TABLE haikudepot.pkg_supplement (
  id BIGINT NOT NULL,
  base_pkg_name VARCHAR(255) NOT NULL,
  icon_modify_timestamp TIMESTAMP,
  create_timestamp TIMESTAMP NOT NULL,
  modify_timestamp TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

-- create a pkg supplement for each 'main' pkg and then make the connections to
-- the packages.

INSERT INTO haikudepot.pkg_supplement (
  id,
  base_pkg_name,
  icon_modify_timestamp,
  create_timestamp,
  modify_timestamp
)
WITH x AS (
SELECT
    id,
    CASE
      WHEN pkg.name LIKE '%_x86_devel'
        THEN substring(pkg.name from 1 for length(pkg.name) - length('_x86_devel'))
      WHEN pkg.name LIKE '%_devel'
        THEN substring(pkg.name from 1 for length(pkg.name) - length('_devel'))
      WHEN pkg.name LIKE '%_x86'
        THEN substring(pkg.name from 1 for length(pkg.name) - length('_x86'))
      ELSE pkg.name
    END AS name,
    icon_modify_timestamp,
    create_timestamp,
    modify_timestamp
  FROM haikudepot.pkg
)
SELECT DISTINCT ON (name)
  x.id,
  x.name,
  x.icon_modify_timestamp,
  x.create_timestamp,
  x.modify_timestamp
FROM x;

ALTER TABLE haikudepot.pkg ADD COLUMN pkg_supplement_id BIGINT;
ALTER TABLE haikudepot.pkg DROP COLUMN icon_modify_timestamp;

UPDATE haikudepot.pkg SET pkg_supplement_id =
  (SELECT id FROM haikudepot.pkg_supplement ps WHERE ps.base_pkg_name = pkg.name);

UPDATE haikudepot.pkg SET pkg_supplement_id =
  (SELECT id FROM haikudepot.pkg_supplement ps
    WHERE ps.base_pkg_name =
      substring(pkg.name from 1 for length(pkg.name) - length('_x86_devel')))
  WHERE name LIKE '%_x86_devel'
  AND pkg_supplement_id IS NULL;

UPDATE haikudepot.pkg SET pkg_supplement_id =
  (SELECT id FROM haikudepot.pkg_supplement ps
    WHERE ps.base_pkg_name =
      substring(pkg.name from 1 for length(pkg.name) - length('_devel')))
  WHERE name LIKE '%_devel'
  AND pkg_supplement_id IS NULL;

UPDATE haikudepot.pkg SET pkg_supplement_id =
  (SELECT id FROM haikudepot.pkg_supplement ps
    WHERE ps.base_pkg_name =
      substring(pkg.name from 1 for length(pkg.name) - length('_x86')))
  WHERE name LIKE '%_x86'
  AND pkg_supplement_id IS NULL;

ALTER TABLE haikudepot.pkg ALTER COLUMN pkg_supplement_id SET NOT NULL;

-- rename the foreign keys; the foreign key value key still holds because of the
-- way that the initial entries in the 'pkg_supplement' were inserted - see
-- above.

ALTER TABLE haikudepot.pkg_changelog RENAME COLUMN pkg_id TO pkg_supplement_id;
ALTER TABLE haikudepot.pkg_icon RENAME COLUMN pkg_id TO pkg_supplement_id;
ALTER TABLE haikudepot.pkg_localization RENAME COLUMN pkg_id TO pkg_supplement_id;
ALTER TABLE haikudepot.pkg_pkg_category RENAME COLUMN pkg_id TO pkg_supplement_id;
ALTER TABLE haikudepot.pkg_screenshot RENAME COLUMN pkg_id TO pkg_supplement_id;

-- remove those data entries where there is no connection to a package supplement

DELETE FROM haikudepot.pkg_changelog WHERE NOT EXISTS
  (SELECT ps.id FROM haikudepot.pkg_supplement ps WHERE ps.id = pkg_supplement_id);

DELETE FROM haikudepot.pkg_icon_image WHERE id IN (
  SELECT pii.id FROM haikudepot.pkg_icon_image pii
    JOIN haikudepot.pkg_icon pi ON pi.id = pii.pkg_icon_id
    WHERE 1 = 1
    AND NOT EXISTS
      (SELECT ps2.id FROM haikudepot.pkg_supplement ps2 WHERE ps2.id = pi.pkg_supplement_id)
);

DELETE FROM haikudepot.pkg_icon WHERE NOT EXISTS
  (SELECT ps.id FROM haikudepot.pkg_supplement ps WHERE ps.id = pkg_supplement_id);

DELETE FROM haikudepot.pkg_localization WHERE NOT EXISTS
  (SELECT ps.id FROM haikudepot.pkg_supplement ps WHERE ps.id = pkg_supplement_id);

DELETE FROM haikudepot.pkg_pkg_category WHERE NOT EXISTS
  (SELECT ps.id FROM haikudepot.pkg_supplement ps WHERE ps.id = pkg_supplement_id);

DELETE FROM haikudepot.pkg_screenshot_image WHERE id IN (
  SELECT psi.id FROM haikudepot.pkg_screenshot_image psi
    JOIN haikudepot.pkg_screenshot ps ON ps.id = psi.pkg_screenshot_id
    WHERE 1 = 1
    AND NOT EXISTS
      (SELECT ps2.id FROM haikudepot.pkg_supplement ps2 WHERE ps2.id = ps.pkg_supplement_id)
);

DELETE FROM haikudepot.pkg_screenshot WHERE NOT EXISTS
  (SELECT ps.id FROM haikudepot.pkg_supplement ps WHERE ps.id = pkg_supplement_id);

-- sequence for the new table.

CREATE SEQUENCE haikudepot.pkg_supplement_seq START WITH 1 INCREMENT BY 10;
SELECT setval(
  'haikudepot.pkg_supplement_seq',
  (SELECT MAX(id) + 5324 FROM haikudepot.pkg_supplement),
  false);

CREATE UNIQUE INDEX pkg_supplement_idx01 ON haikudepot.pkg_supplement(base_pkg_name);

CREATE INDEX pkg_idx03 ON haikudepot.pkg(pkg_supplement_id);
