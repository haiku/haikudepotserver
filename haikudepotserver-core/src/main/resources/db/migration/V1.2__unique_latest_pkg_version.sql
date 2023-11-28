-- This change will ensure that there is only one latest pkg_version for a given
-- architecture and package version.

CREATE UNIQUE INDEX pkg_version_idx04
    ON haikudepot.pkg_version
        USING btree (pkg_id, architecture_id, repository_source_id)
    WHERE is_latest = true AND active = true
