ALTER TABLE haikudepot.pkg
    ADD COLUMN is_native_desktop BOOLEAN;
UPDATE haikudepot.pkg
SET is_native_desktop = false;
ALTER TABLE haikudepot.pkg
    ALTER COLUMN is_native_desktop SET NOT NULL;

INSERT INTO haikudepot.permission (id, code, name)
VALUES (NEXTVAL('haikudepot.permission_seq'),
        'pkg_editnativedesktop',
        'Edit Package Native Desktop')