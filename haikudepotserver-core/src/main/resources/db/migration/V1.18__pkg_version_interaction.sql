-- This change will break the view count for the `pkg_version` out into another table.

CREATE SEQUENCE haikudepot.pkg_version_interaction_seq
    START WITH 5342
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_version_interaction
(
    id           BIGINT NOT NULL,
    view_counter BIGINT NOT NULL,
    PRIMARY KEY (id)
)
;

ALTER TABLE haikudepot.pkg_version
    ADD COLUMN pkg_version_interaction_id BIGINT;

INSERT INTO haikudepot.pkg_version_interaction
    (id, view_counter)
SELECT pv1.id, pv1.view_counter
FROM haikudepot.pkg_version pv1
WHERE pv1.view_counter IS NOT NULL AND pv1.view_counter > 0;

UPDATE haikudepot.pkg_version
SET pkg_version_interaction_id = id
WHERE view_counter IS NOT NULL AND view_counter > 0;

ALTER TABLE haikudepot.pkg_version
    DROP COLUMN view_counter;

ALTER TABLE ONLY haikudepot.pkg_version
    ADD CONSTRAINT pkg_version_interaction_id_fkey FOREIGN KEY (pkg_version_interaction_id) REFERENCES haikudepot.pkg_version_interaction (id) DEFERRABLE INITIALLY DEFERRED;

SELECT SETVAL(
               'haikudepot.pkg_version_interaction_seq',
               (SELECT MAX(id) + 2000 FROM haikudepot.pkg_version_interaction)
       );