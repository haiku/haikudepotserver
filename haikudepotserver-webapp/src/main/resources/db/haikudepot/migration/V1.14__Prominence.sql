-- ------------------------------------------------------
-- ADD PROMINENCE FOR A PACKAGE
-- ------------------------------------------------------

CREATE TABLE haikudepot.prominence (
  id BIGINT NOT NULL,
  ordering INT NOT NULL,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.prominence_seq START WITH 3112 INCREMENT BY 1;

CREATE UNIQUE INDEX prominence_idx01 ON haikudepot.prominence(ordering);
CREATE UNIQUE INDEX prominence_idx02 ON haikudepot.prominence(name);

INSERT INTO haikudepot.prominence (id,ordering,name) VALUES ((SELECT nextval('haikudepot.prominence_seq')), 100, '1st');
INSERT INTO haikudepot.prominence (id,ordering,name) VALUES ((SELECT nextval('haikudepot.prominence_seq')), 200, '2nd');
INSERT INTO haikudepot.prominence (id,ordering,name) VALUES ((SELECT nextval('haikudepot.prominence_seq')), 300, '3rd');
INSERT INTO haikudepot.prominence (id,ordering,name) VALUES ((SELECT nextval('haikudepot.prominence_seq')), 1000, 'Last');

ALTER TABLE haikudepot.pkg ADD COLUMN prominence_id BIGINT;
UPDATE haikudepot.pkg SET prominence_id = (SELECT id FROM haikudepot.prominence WHERE ordering=1000);
ALTER TABLE haikudepot.pkg ALTER COLUMN prominence_id SET NOT NULL;

ALTER TABLE haikudepot.pkg ADD FOREIGN KEY (prominence_id) REFERENCES haikudepot.prominence (id);

INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_editprominence', 'Edit Package Prominence');
