-- Some of the pkg_localizations seem to have empty strings in them; convert those to be NULL.

UPDATE haikudepot.pkg_localization SET title = NULL WHERE 0 = LENGTH(TRIM(both from title));
UPDATE haikudepot.pkg_localization SET summary = NULL WHERE 0 = LENGTH(TRIM(both from summary));
UPDATE haikudepot.pkg_localization SET description = NULL WHERE 0 = LENGTH(TRIM(both from description));