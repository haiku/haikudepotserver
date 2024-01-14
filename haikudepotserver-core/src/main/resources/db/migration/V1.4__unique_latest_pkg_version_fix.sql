-- The index implemented in V1.3 ran afoul of a situation where statements from Cayenne could be applied
-- in different orders in a transaction. Because of this the index is being swapped for a deferrable
-- constraint that uses the `EXCLUDE` algorithm.

DROP INDEX haikudepot.pkg_version_idx04;

ALTER TABLE haikudepot.pkg_version ADD CONSTRAINT pkg_version_uniq_latest
    EXCLUDE (
        pkg_id WITH =,
        architecture_id WITH =,
        repository_source_id WITH =
    )
    WHERE (is_latest = true AND active = true)
    DEFERRABLE INITIALLY DEFERRED ;