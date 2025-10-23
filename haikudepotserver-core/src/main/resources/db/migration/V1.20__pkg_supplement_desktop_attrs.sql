ALTER TABLE haikudepot.pkg ADD COLUMN is_desktop BOOLEAN;

UPDATE haikudepot.pkg SET is_desktop = false;

ALTER TABLE haikudepot.pkg ALTER COLUMN is_desktop SET NOT NULL;