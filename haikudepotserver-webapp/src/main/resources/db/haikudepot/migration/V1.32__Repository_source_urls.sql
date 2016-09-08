-- Here the repository sources' URLs need to have the trailing "../repo" removed and
-- the URLs should be the same or equivalent to the public URLs used by Haiku-OS users
-- ----------------------------

-- swap some of the existing file URLs over to http URLs;

UPDATE haikudepot.repository_source SET
  url = 'http://packages.haiku-os.org/haikuports/master/repo/x86_gcc2/current'
WHERE
  url = 'file:///srv/www/packages/haikuports/master/repo/x86_gcc2/current/repo'
  AND code = 'haikuports_x86_gcc2';

UPDATE haikudepot.repository_source SET
  url = 'http://packages.haiku-os.org/haikuports/master/repo/x86_64/current'
WHERE
  url = 'file:///srv/www/packages/haikuports/master/repo/x86_64/current/repo'
  AND code = 'haikuports_x86_64';

UPDATE haikudepot.repository_source SET
  url = 'http://packages.haiku-os.org/haikuports/master/repo/x86/current'
WHERE
  url = 'file:///srv/www/packages/haikuports/master/repo/x86/current/repo'
  AND code = 'haikuports_x86';

-- remove the trailing "repo" on the URLs;

UPDATE haikudepot.repository_source SET
  url = regexp_replace(url, '(.+)/repo$', '\1')
WHERE
  url LIKE '%/repo';
