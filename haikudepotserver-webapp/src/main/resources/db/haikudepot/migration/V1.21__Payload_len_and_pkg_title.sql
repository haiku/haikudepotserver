-- The payload length incorporates the length of the pkg version download into the
-- PkgVersion entity.

ALTER TABLE haikudepot.pkg_version ADD COLUMN payload_length BIGINT;