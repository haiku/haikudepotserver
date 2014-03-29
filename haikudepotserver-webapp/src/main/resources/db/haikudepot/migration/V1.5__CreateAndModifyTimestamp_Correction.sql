-- ------------------------------------------------------
-- USER NEEDS CREATE AND MODIFY TIMESTAMPS
-- ------------------------------------------------------

ALTER TABLE haikudepot.user ADD COLUMN create_timestamp TIMESTAMP;
ALTER TABLE haikudepot.user ADD COLUMN modify_timestamp TIMESTAMP;
UPDATE haikudepot.user SET create_timestamp=current_timestamp;
UPDATE haikudepot.user SET modify_timestamp=current_timestamp;
ALTER TABLE haikudepot.user ALTER COLUMN create_timestamp SET NOT NULL;
ALTER TABLE haikudepot.user ALTER COLUMN modify_timestamp SET NOT NULL;