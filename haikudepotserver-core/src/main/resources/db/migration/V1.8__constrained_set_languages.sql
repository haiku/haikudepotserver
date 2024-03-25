-- sometimes we get longer country codes than just two

ALTER TABLE haikudepot.natural_language
    ALTER COLUMN country_code TYPE VARCHAR(3);

-- HDS is to follow the set of languages supported by Haiku itself and Polygot. These are able to
-- express languages using coordinates of the code, country and script. This migration will reduce
-- the list of languages down to the intended set.

CREATE TEMPORARY TABLE natural_language_tmp
(
    language_code VARCHAR(255),
    country_code  VARCHAR(255),
    script_code   VARCHAR(255),
    name          VARCHAR(255)
) ON COMMIT DROP;

WITH nl_core -- these are the base languages
         AS (SELECT language_code, country_code, script_code, name
             FROM haikudepot.natural_language
             WHERE language_code IN ('ca', 'de', 'en', 'es', 'ja', 'pt', 'ro', 'ru', 'sk', 'tr', 'zh')),
     nl_data -- these are languages for which HDS already has data
         AS (SELECT nl.language_code, nl.country_code, nl.script_code, nl.name
             FROM haikudepot.natural_language nl
             WHERE EXISTS(SELECT pl1.id FROM haikudepot.pkg_localization pl1 WHERE pl1.natural_language_id = nl.id)
                OR EXISTS(SELECT u1.id FROM haikudepot.user u1 WHERE u1.natural_language_id = nl.id)
                OR EXISTS(SELECT ur1.id FROM haikudepot.user_rating ur1 WHERE ur1.natural_language_id = nl.id)
                OR EXISTS(SELECT pvl1.id
                          FROM haikudepot.pkg_version_localization pvl1
                          WHERE pvl1.natural_language_id = nl.id)),
     nl_polygot(language_code, country_code, script_code, name) -- these are values from Polygot
         AS (VALUES ('ar', NULL, NULL, 'Arabic'),
                    ('ast', NULL, NULL, 'Asturian'),
                    ('be', NULL, NULL, 'Belarusian'),
                    ('bg', NULL, NULL, 'Bulgarian'),
                    ('ca', NULL, NULL, 'Catalan'),
                    ('zh', NULL, 'Hans', 'Chinese (simplified)'),
                    ('hr', NULL, NULL, 'Croatian'),
                    ('cs', NULL, NULL, 'Czech'),
                    ('da', NULL, NULL, 'Danish'),
                    ('nl', NULL, NULL, 'Dutch'),
                    ('en', 'AU', NULL, 'English (Australian)'),
                    ('en', 'GB', NULL, 'English (British)'),
                    ('en', 'CA', NULL, 'English (Canada)'),
                    ('eo', NULL, NULL, 'Esperanto'),
                    ('fi', NULL, NULL, 'Finnish'),
                    ('fr', NULL, NULL, 'French'),
                    ('fur', NULL, NULL, 'Friulian'),
                    ('de', NULL, NULL, 'German'),
                    ('el', NULL, NULL, 'Greek (Modern)'),
                    ('hi', NULL, NULL, 'Hindi'),
                    ('hu', NULL, NULL, 'Hungarian'),
                    ('id', NULL, NULL, 'Indonesian'),
                    ('it', NULL, NULL, 'Italian'),
                    ('ja', NULL, NULL, 'Japanese'),
                    ('ko', NULL, NULL, 'Korean'),
                    ('lt', NULL, NULL, 'Lithuanian'),
                    ('nds', NULL, NULL, 'Low German'),
                    ('mi', NULL, NULL, 'Maori'),
                    ('nb', NULL, NULL, 'Norwegian'),
                    ('pa', NULL, NULL, 'Panjabi'),
                    ('fa', NULL, NULL, 'Persian'),
                    ('pl', NULL, NULL, 'Polish'),
                    ('pt', NULL, NULL, 'Portuguese'),
                    ('pt', 'BR', NULL, 'Portuguese (Brazil)'),
                    ('ro', NULL, NULL, 'Romanian'),
                    ('ru', NULL, NULL, 'Russian'),
                    ('sc', NULL, NULL, 'Sardinian'),
                    ('sr', NULL, NULL, 'Serbian (Cyrillic)'),
                    ('sr', NULL, 'Latn', 'Serbian (Latin)'),
                    ('sk', NULL, NULL, 'Slovak'),
                    ('sl', NULL, NULL, 'Slovenian'),
                    ('es', NULL, NULL, 'Spanish'),
                    ('es', '419', NULL, 'Spanish (Latin American)'),
                    ('sv', NULL, NULL, 'Swedish'),
                    ('th', NULL, NULL, 'Thai'),
                    ('tr', NULL, NULL, 'Turkish'),
                    ('uk', NULL, NULL, 'Ukrainian')),
     nl_all AS (SELECT *
                FROM nl_core
                UNION
                SELECT *
                FROM nl_data
                UNION
                SELECT *
                FROM nl_polygot)
INSERT
INTO natural_language_tmp (language_code, country_code, script_code, name)
SELECT DISTINCT language_code, country_code, script_code, name
FROM nl_all;

-- delete all of the natural languages from HDS that are no longer required.

DELETE
FROM haikudepot.natural_language nl
WHERE NOT EXISTS(SELECT *
                 FROM natural_language_tmp nlt
                 WHERE COALESCE(nlt.language_code, '') = COALESCE(nl.language_code, '')
                   AND COALESCE(nlt.country_code, '') = COALESCE(nl.country_code, '')
                   AND COALESCE(nlt.script_code, '') = COALESCE(nl.script_code, ''));

-- add all of the natural languages that were previously missing.

INSERT INTO haikudepot.natural_language (id, name, is_popular, create_timestamp, modify_timestamp, language_code,
                                         country_code, script_code)
SELECT NEXTVAL('haikudepot.natural_language_seq'),
       nlt.name,
       false,
       now(),
       now(),
       nlt.language_code,
       nlt.country_code,
       nlt.script_code
FROM natural_language_tmp nlt
WHERE NOT EXISTS(SELECT *
                 FROM haikudepot.natural_language nl1
                 WHERE COALESCE(nlt.language_code, '') = COALESCE(nl1.language_code, '')
                   AND COALESCE(nlt.country_code, '') = COALESCE(nl1.country_code, '')
                   AND COALESCE(nlt.script_code, '') = COALESCE(nl1.script_code, ''))
ORDER BY COALESCE(nlt.language_code, '') ASC, COALESCE(nlt.country_code, '') ASC,
         COALESCE(nlt.script_code, '') ASC;