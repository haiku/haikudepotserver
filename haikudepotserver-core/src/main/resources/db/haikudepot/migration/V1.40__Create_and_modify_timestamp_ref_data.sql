-- This schema migration will add a create timestamp to some reference data so
-- that it is possible to more easily synchronize the data with the client
-- HaikuDepot systems.

ALTER TABLE haikudepot.pkg_category ADD COLUMN create_timestamp TIMESTAMP;
ALTER TABLE haikudepot.pkg_category ADD COLUMN modify_timestamp TIMESTAMP;
ALTER TABLE haikudepot.natural_language ADD COLUMN create_timestamp TIMESTAMP;
ALTER TABLE haikudepot.natural_language ADD COLUMN modify_timestamp TIMESTAMP;
ALTER TABLE haikudepot.country ADD COLUMN create_timestamp TIMESTAMP;
ALTER TABLE haikudepot.country ADD COLUMN modify_timestamp TIMESTAMP;

-- these are just made-up values.
UPDATE haikudepot.pkg_category SET
  create_timestamp = '2010-01-01', modify_timestamp = '2010-01-01';
UPDATE haikudepot.natural_language SET
  create_timestamp = '2010-01-01', modify_timestamp = '2010-01-01';
UPDATE haikudepot.country SET
  create_timestamp = '2010-01-01', modify_timestamp = '2010-01-01';

-- adjust a few values to help with testing.
UPDATE haikudepot.pkg_category SET
  create_timestamp = '2010-02-01', modify_timestamp = '2010-02-01'
  WHERE code = 'productivity';
UPDATE haikudepot.natural_language SET
  create_timestamp = '2010-03-01', modify_timestamp = '2010-03-01'
  WHERE code = 'cs';
UPDATE haikudepot.country SET
  create_timestamp = '2010-04-01', modify_timestamp = '2010-04-01'
  WHERE code = 'CZ';

ALTER TABLE haikudepot.pkg_category ALTER COLUMN create_timestamp SET NOT NULL;
ALTER TABLE haikudepot.pkg_category ALTER COLUMN modify_timestamp SET NOT NULL;
ALTER TABLE haikudepot.natural_language ALTER COLUMN create_timestamp SET NOT NULL;
ALTER TABLE haikudepot.natural_language ALTER COLUMN modify_timestamp SET NOT NULL;
ALTER TABLE haikudepot.country ALTER COLUMN create_timestamp SET NOT NULL;
ALTER TABLE haikudepot.country ALTER COLUMN modify_timestamp SET NOT NULL;