ALTER TABLE haikudepot.pkg ADD COLUMN icon_modify_timestamp TIMESTAMP;
UPDATE haikudepot.pkg SET icon_modify_timestamp = modify_timestamp;