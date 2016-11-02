-- ------------------------------------------------------
-- STORE A DERIVED USER RATING ON THE PACKAGE
-- ------------------------------------------------------

-- had to be split into a separate migration to get into a different
-- transaction or else it causes;
-- ERROR: cannot ALTER TABLE "pkg" because it has pending trigger events

ALTER TABLE haikudepot.pkg ALTER COLUMN derived_rating_sample_size SET NOT NULL;
