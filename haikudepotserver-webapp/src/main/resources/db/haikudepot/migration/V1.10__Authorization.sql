-- ------------------------------------------------------
-- ADD AN EMAIL TO A USER
-- ------------------------------------------------------

ALTER TABLE haikudepot.user ADD COLUMN email VARCHAR(256);

-- ------------------------------------------------------
-- FORGOT PASSWORD
-- ------------------------------------------------------

CREATE TABLE haikudepot.user_password_reset_token (
  id BIGINT NOT NULL,
  code VARCHAR(255) NOT NULL,
  create_timestamp TIMESTAMP NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.user_password_reset_token_seq START WITH 4251 INCREMENT BY 1;

ALTER TABLE haikudepot.user_password_reset_token ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id);

-- ------------------------------------------------------
-- AUTHORIZATION
-- ------------------------------------------------------

CREATE TABLE haikudepot.permission (
  id BIGINT NOT NULL,
  code VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE haikudepot.permission_user_pkg (
  id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  pkg_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE haikudepot.permission_user_repository (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  repository_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE haikudepot.permission_public_repository (
  id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  repository_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE SEQUENCE haikudepot.permission_seq START WITH 6999 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.permission_user_pkg_seq START WITH 5554 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.permission_user_repository_seq START WITH 1122 INCREMENT BY 1;
CREATE SEQUENCE haikudepot.permission_public_repository_seq START WITH 9174 INCREMENT BY 1;

ALTER TABLE haikudepot.permission_user_pkg ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id);
ALTER TABLE haikudepot.permission_user_pkg ADD FOREIGN KEY (permission_id) REFERENCES haikudepot.permission (id);
ALTER TABLE haikudepot.permission_user_pkg ADD FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg (id);
ALTER TABLE haikudepot.permission_user_repository ADD FOREIGN KEY (repository_id) REFERENCES haikudepot.repository (id);
ALTER TABLE haikudepot.permission_user_repository ADD FOREIGN KEY (permission_id) REFERENCES haikudepot.permission (id);
ALTER TABLE haikudepot.permission_user_repository ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id);
ALTER TABLE haikudepot.permission_public_repository ADD FOREIGN KEY (permission_id) REFERENCES haikudepot.permission (id);
ALTER TABLE haikudepot.permission_public_repository ADD FOREIGN KEY (repository_id) REFERENCES haikudepot.repository (id);

INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_view', 'View Repository');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_edit','Edit Repository');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_import','Import Repository');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_list','List Repositories');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_list_inactive','List Inactive Repositories');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'repository_add','Add Repository');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'user_view','View User');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'user_edit','Edit User');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'user_changepassword','Change User Password');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'user_list','List Users');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'userrating_edit','Edit User Rating');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_createuserrating','Create User Rating');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_editicon','Edit Package Icon');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_editscreenshot','Edit Package Screenshot');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_editcategories','Edit Package Categories');
INSERT INTO haikudepot.permission (id, code, name) VALUES ((SELECT nextval('haikudepot.permission_seq')), 'pkg_editversionlocalization','Edit Version Localization');