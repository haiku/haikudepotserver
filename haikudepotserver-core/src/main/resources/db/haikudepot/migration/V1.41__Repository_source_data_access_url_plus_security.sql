-- This change allows for the OPTIONAL storage of an additional URL for
-- the pull-down of data for the repository source.  Also there is the
-- introduction of a password hash for authenticating access to the
-- repository update functions.

ALTER TABLE haikudepot.repository ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE haikudepot.repository ADD COLUMN password_salt VARCHAR(255);
UPDATE haikudepot.repository SET password_salt = MD5(id || random()::text);

ALTER TABLE haikudepot.repository_source ADD COLUMN forced_internal_base_url VARCHAR(1024);

UPDATE haikudepot.repository_source
  SET forced_internal_base_url =
    'http://buildmaster:80/haikuports/repository/master/x86_gcc2/current'
  WHERE code = 'haikuports_x86_gcc2';

UPDATE haikudepot.repository_source
  SET forced_internal_base_url =
    'http://buildmaster:80/haikuports/repository/master/x86_64/current'
  WHERE code = 'haikuports_x86_64';

UPDATE haikudepot.repository_source_mirror
  SET base_url = 'https://eu.hpkg.haiku-os.org/haiku/master/x86_gcc2/current'
  WHERE base_url = 'http://buildmaster:80/haikuports/repository/master/x86_gcc2/current';

UPDATE haikudepot.repository_source_mirror
  SET base_url = 'https://eu.hpkg.haiku-os.org/haiku/master/x86_64/current'
  WHERE base_url = 'http://buildmaster:80/haikuports/repository/master/x86_64/current';