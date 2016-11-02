-- remove the permission for pkg version editing because this is no longer required.

DELETE FROM haikudepot.permission_user_pkg WHERE permission_id = (
  SELECT id FROM haikudepot.permission WHERE code='pkg_editversionlocalization');

DELETE FROM haikudepot.permission WHERE code='pkg_editversionlocalization';

-- extend the pkg_localization with summary and description.

ALTER TABLE haikudepot.pkg_localization ADD COLUMN summary VARCHAR(8192);
ALTER TABLE haikudepot.pkg_localization ADD COLUMN description VARCHAR(8192);

-- where there might be some already localized data in pkg_version_localization,
-- an entry for pkg_localization should be made, but only for non-english.

INSERT INTO haikudepot.pkg_localization (
  id,
  create_timestamp,
  modify_timestamp,
  pkg_id,
  natural_language_id
)
  SELECT
      DISTINCT ON (p.id,nl.id)
      nextval('haikudepot.pkg_localization_seq'),now(),now(),
      p.id AS "pkg_id",
      nl.id AS "natural_language_id"
  FROM
    haikudepot.pkg_version_localization pvl
    JOIN haikudepot.pkg_version pv ON pv.id = pvl.pkg_version_id
    JOIN haikudepot.pkg p ON p.id = pv.pkg_id
    JOIN haikudepot.natural_language nl ON nl.id = pvl.natural_language_id
  WHERE
    pv.is_latest = true
    AND nl.code <> 'en'
    AND NOT EXISTS(
      SELECT id FROM haikudepot.pkg_localization pl2
      WHERE pl2.pkg_id = p.id AND pl2.natural_language_id = nl.id
    )
  ORDER BY
    p.id ASC, nl.id ASC;

-- now for non-english pkg_version data, transfer any latest pkg_version_localization
-- over to pkg_localization.  This just happens once as a migration.

UPDATE haikudepot.pkg_localization upl
SET summary=sq.sq_summary, description=sq.sq_description
FROM
  (
    SELECT
      p.id AS sq_pkg_id,
      nl.id AS sq_natural_language_id,
      pv.architecture_id AS sq_architecture_id,
      pvl.summary AS sq_summary,
      pvl.description AS sq_description,
      rank() OVER (PARTITION BY p.id, nl.id ORDER BY pv.id ASC) AS sq_rank
    FROM
      haikudepot.pkg_version_localization pvl
      JOIN haikudepot.pkg_version pv ON pv.id = pvl.pkg_version_id
      JOIN haikudepot.pkg p ON p.id = pv.pkg_id
      JOIN haikudepot.natural_language nl ON nl.id = pvl.natural_language_id
    WHERE
      pv.is_latest = true
      AND nl.code <> 'en'
    ORDER BY
      p.id ASC,
      pv.id ASC,
      nl.id ASC,
      pvl.id ASC
  ) AS sq
WHERE
  sq.sq_pkg_id = pkg_id
  AND sq.sq_natural_language_id = natural_language_id
  AND sq.sq_rank = 1;

-- finally we can now loose any pkg_version_localizations that are not english because
-- now they will be fall-backs for all versions of the package.

DELETE FROM haikudepot.pkg_version_localization WHERE id IN (
    SELECT pvl.id FROM
      haikudepot.pkg_version_localization pvl
      JOIN haikudepot.pkg_version pv ON pvl.pkg_version_id = pv.id
      JOIN haikudepot.natural_language nl ON nl.id = pvl.natural_language_id
    WHERE
      nl.code <> 'en'
);

-- an extra optimization is to not store the same localized text more than once.

CREATE TABLE haikudepot.localization_content (
  id BIGINT NOT NULL,
  content VARCHAR(65535) NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.localization_content_seq START WITH 7777 INCREMENT BY 1;
CREATE UNIQUE INDEX localization_content_idx01 ON haikudepot.localization_content(content);

INSERT INTO haikudepot.localization_content (
  id,
  content
)
    WITH c AS (
      SELECT summary AS content FROM haikudepot.pkg_version_localization
      UNION SELECT description AS content FROM haikudepot.pkg_version_localization
    )
      SELECT
        DISTINCT ON (content)
        nextval('haikudepot.localization_content_seq'),
        content
    FROM c;

ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN summary_localization_content_id int8;
ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN description_localization_content_id int8;
ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN title_localization_content_id int8;

ALTER TABLE haikudepot.pkg_version_localization ADD FOREIGN KEY (summary_localization_content_id)
REFERENCES haikudepot.localization_content (id);

ALTER TABLE haikudepot.pkg_version_localization ADD FOREIGN KEY (description_localization_content_id)
REFERENCES haikudepot.localization_content (id);

ALTER TABLE haikudepot.pkg_version_localization ADD FOREIGN KEY (title_localization_content_id)
REFERENCES haikudepot.localization_content (id);

UPDATE haikudepot.pkg_version_localization SET
  summary_localization_content_id = (SELECT lc.id FROM haikudepot.localization_content lc WHERE lc.content=summary);

UPDATE haikudepot.pkg_version_localization SET
  description_localization_content_id = (SELECT lc.id FROM haikudepot.localization_content lc WHERE lc.content=description);

-- no title to populate yet.