UPDATE
  haikudepot.permission
SET
  name = 'Edit Package Localization'
WHERE
  code='pkg_editlocalization';

INSERT INTO
  haikudepot.permission (id, code, name)
VALUES
  ((SELECT nextval('haikudepot.permission_seq')),
   'pkg_editprominence',
   'Edit Package Prominence');
