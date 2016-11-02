-- ------------------------------------------------------
-- STORE A DERIVED USER RATING ON THE PACKAGE
-- ------------------------------------------------------

ALTER TABLE haikudepot.pkg ADD COLUMN derived_rating FLOAT;
ALTER TABLE haikudepot.pkg ADD COLUMN derived_rating_sample_size INT4;
UPDATE haikudepot.pkg SET derived_rating_sample_size = 0;

