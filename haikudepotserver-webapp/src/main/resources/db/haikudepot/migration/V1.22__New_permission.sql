INSERT INTO
  haikudepot.permission (id,code,name)
VALUES (
  NEXTVAL('haikudepot.permission_seq'),
  'pkg_editlocalization',
  'Edit Localization'
);

UPDATE
  haikudepot.permission
SET
  name = 'Edit Package Version Localization'
WHERE
  code='pkg_editversionlocalization';