-- ------------------------------------------------------
-- ADDITIONAL MATERIAL FOR CATEGORIES
-- ------------------------------------------------------

CREATE TABLE haikudepot.pkg_category (code VARCHAR(32) NOT NULL, id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.pkg_pkg_category (id BIGINT NOT NULL, pkg_category_id BIGINT NOT NULL, pkg_id BIGINT NOT NULL, PRIMARY KEY (id));

ALTER TABLE haikudepot.pkg_pkg_category ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);
ALTER TABLE haikudepot.pkg_pkg_category ADD FOREIGN KEY (pkg_category_id) REFERENCES haikudepot.pkg_category (id);

CREATE UNIQUE INDEX pkg_pkg_category_idx01 ON haikudepot.pkg_pkg_category(pkg_id,pkg_category_id);
CREATE UNIQUE INDEX pkg_category_idx01 ON haikudepot.pkg_category(code);

CREATE SEQUENCE haikudepot.pkg_pkg_category_seq START WITH 6194 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.pkg_category_seq START WITH 8211 INCREMENT BY 1;

-- List influenced by discussions on the mailing list around March 2013.

INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'GAMES','Games');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'BUSINESS','Business');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'AUDIO','Audio');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'VIDEO','Video');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'GRAPHICS','Graphics');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'EDUCATION','Education');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'PRODUCTIVITY','Productivity');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'SYSTEMANDUTILITIES','System and Utilities');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'INTERNETANDNETWORK','Internet and Network');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'DEVELOPMENT','Development');
INSERT INTO haikudepot.pkg_category(id,code,name) VALUES ((SELECT nextval('haikudepot.pkg_category_seq')),'SCIENCEANDMATHEMATICS','Science and Mathematics');

-- ------------------------------------------------------
-- ADDITIONAL MATERIAL FOR VERSION LOCALIZATION
-- ------------------------------------------------------

CREATE TABLE haikudepot.natural_language (code VARCHAR(32) NOT NULL, id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, PRIMARY KEY (id));

CREATE UNIQUE INDEX natural_language_idx01 ON haikudepot.natural_language(code);

CREATE SEQUENCE haikudepot.natural_language_seq START WITH 9400 INCREMENT BY 1;

-- Note that the codes used here are ISO639-3
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'en','English');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'de','German');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'fr','French');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'ja','Japanese');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'es','Spanish');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'zh','Chinese');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'pt','Portugese');
INSERT INTO haikudepot.natural_language(id,code,name) VALUES ((SELECT nextval('haikudepot.natural_language_seq')),'ru','Russian');

ALTER TABLE haikudepot.pkg_version_localization ADD COLUMN natural_language_id BIGINT;
UPDATE haikudepot.pkg_version_localization SET natural_language_id = (SELECT id FROM haikudepot.natural_language WHERE code='en');
ALTER TABLE haikudepot.pkg_version_localization ALTER COLUMN natural_language_id SET NOT NULL;

DROP INDEX haikudepot.pkg_version_localization_idx01;
CREATE UNIQUE INDEX pkg_version_localization_idx01 ON haikudepot.pkg_version_localization(pkg_version_id,natural_language_id);

ALTER TABLE haikudepot.user ADD COLUMN natural_language_id BIGINT;
UPDATE haikudepot.user SET natural_language_id = (SELECT id FROM haikudepot.natural_language WHERE code='en');
ALTER TABLE haikudepot.user ALTER COLUMN natural_language_id SET NOT NULL;
ALTER TABLE haikudepot.user ADD FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language (id);

-- ------------------------------------------------------
-- ADDITIONAL MATERIAL FOR RATINGS
-- ------------------------------------------------------

CREATE TABLE haikudepot.user_rating_stability (code VARCHAR(32) NOT NULL, id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, PRIMARY KEY (id));
CREATE TABLE haikudepot.user_rating (
  active BOOLEAN NOT NULL, comment VARCHAR(4096) NULL, create_timestamp TIMESTAMP NOT NULL,
  user_rating_stability_id BIGINT, id BIGINT NOT NULL, modify_timestamp TIMESTAMP NOT NULL,
  pkg_version_id BIGINT NOT NULL, rating SMALLINT NULL, natural_language_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL, PRIMARY KEY (id));

CREATE UNIQUE INDEX user_rating_stability_idx01 ON haikudepot.user_rating_stability(code);
CREATE UNIQUE INDEX user_rating_idx01 ON haikudepot.user_rating(pkg_version_id, user_id);
CREATE INDEX user_rating_idx02 ON haikudepot.user_rating(user_rating_stability_id);

CREATE SEQUENCE haikudepot.user_rating_stability_seq START WITH 2153 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.user_rating_seq START WITH 1022 INCREMENT BY 1;

INSERT INTO haikudepot.user_rating_stability(id,code,name) VALUES ((SELECT nextval('haikudepot.user_rating_stability_seq')),'nostart','Will Not Start');
INSERT INTO haikudepot.user_rating_stability(id,code,name) VALUES ((SELECT nextval('haikudepot.user_rating_stability_seq')),'veryunstable','Very Unstable');
INSERT INTO haikudepot.user_rating_stability(id,code,name) VALUES ((SELECT nextval('haikudepot.user_rating_stability_seq')),'unstablebutusable','Unstable but Usable');
INSERT INTO haikudepot.user_rating_stability(id,code,name) VALUES ((SELECT nextval('haikudepot.user_rating_stability_seq')),'mostlystable','Mostly Stable');
INSERT INTO haikudepot.user_rating_stability(id,code,name) VALUES ((SELECT nextval('haikudepot.user_rating_stability_seq')),'stable','Stable');

ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version (id);
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id);
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (user_rating_stability_id) REFERENCES haikudepot.user_rating_stability (id);
ALTER TABLE haikudepot.user_rating ADD FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language (id);