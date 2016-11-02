-- ------------------------------------------------------
-- PASSWORD HASHES ARE CHANGING
-- ------------------------------------------------------
-- It is necessary to reset the root user's password back
-- to the original value and it is also necessary to
-- remove those users who don't have email addresses as
-- it will not be possible for them to reset their
-- passwords.
-- ------------------------------------------------------

UPDATE haikudepot.user SET
  password_hash='b9c4717bc5c6d16f2be9e967ab0c752f8ac2084f95781989f39cf8736e2edeef',
  password_salt='cad3422ea02761f8'
WHERE
  nickname='root';

-- the following query will get a list of effected parties;
-- SELECT
--   nickname,
--   (SELECT true FROM haikudepot.user_password_reset_token WHERE user_id=u.id LIMIT 1) AS prt,
--   (SELECT true FROM haikudepot.permission_user_pkg WHERE user_id=u.id LIMIT 1) AS pup,
--   (SELECT true FROM haikudepot.user_rating WHERE user_id=u.id LIMIT 1) AS ur
-- FROM
--   haikudepot.user u
-- WHERE
--   u.email IS NULL
--   AND u.nickname <> 'root';

DELETE FROM
  haikudepot.permission_user_pkg
WHERE
  user_id IN (
    SELECT id FROM haikudepot.user u
    WHERE u.email IS NULL AND u.nickname <> 'root'
  );

DELETE FROM
  haikudepot.user_password_reset_token
WHERE
  user_id IN (
    SELECT id FROM haikudepot.user u
    WHERE u.email IS NULL AND u.nickname <> 'root'
  );

DELETE FROM
  haikudepot.user_rating
WHERE
  user_id IN (
    SELECT id FROM haikudepot.user u
    WHERE u.email IS NULL AND u.nickname <> 'root'
  );

DELETE FROM haikudepot.user
WHERE email IS NULL AND nickname <> 'root'


