--
-- Copyright 2018, Andrew Lindesay
-- Distributed under the terms of the MIT License.
--
-- -------------
--
-- This script is designed to be run on an HDS database in order to scramble
-- some data so that it contains less personal data and no password hashes.
--
-- -------------

-- helper functions

CREATE OR REPLACE FUNCTION
  hds_scramble_chars(str TEXT)
RETURNS TEXT
LANGUAGE PLPGSQL
AS $$
DECLARE
  replace TEXT := '0123456789'
    || 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
    || 'abcdefghijklmnopqrstuvwxyz';
  replace_len INTEGER := length(replace);
  result TEXT := '';
  str_len INTEGER := length(str);
  i INTEGER := 1;
BEGIN
  WHILE i <= str_len LOOP
    result := result ||
      CASE
        WHEN substr(str, i, 1) ~ '\W' THEN ' '
        ELSE substr(
          replace,
          floor(random() * replace_len)::INTEGER,
          1)
      END;
    i := i + 1;
  END LOOP;
  RETURN result;
END; $$;

CREATE OR REPLACE FUNCTION
    hds_swap_user_rating_users_for_pkg_version_id(in_pkg_version_id BIGINT)
RETURNS VOID
LANGUAGE PLPGSQL
AS
$$
DECLARE
    a_record  RECORD;
    a_user_id BIGINT;
    candidate_user_count INT;
BEGIN

    SELECT COUNT(*)
    INTO candidate_user_count
    FROM haikudepot.user_rating ur
    WHERE ur.pkg_version_id = in_pkg_version_id;

    -- Create a table of the possible users that might be able to replace
    -- the users who have previously commented on this PkgVersion.  Users
    -- who have previously commented but not on this PkgVersion are
    -- chosen.

    CREATE TEMP TABLE candidate_users AS
    SELECT u.id
    FROM haikudepot.user u
    WHERE 1 = 1
      AND EXISTS(SELECT ur.id FROM haikudepot.user_rating ur WHERE ur.user_id = u.id)
      AND NOT EXISTS(SELECT ur.id
                     FROM haikudepot.user_rating ur
                     WHERE ur.user_id = u.id AND ur.pkg_version_id = in_pkg_version_id)
    LIMIT candidate_user_count;

    -- In some cases it could come to pass that there are not enough
    -- users who match the above criteria.  In this case, choose some
    -- random users from the user pool.

    WHILE (SELECT COUNT(*) FROM candidate_users) < candidate_user_count
        LOOP
            INSERT INTO candidate_users (id)
            SELECT id
            FROM haikudepot.user u
            WHERE 1 = 1
              AND NOT EXISTS(SELECT ur2.id
                             FROM haikudepot.user_rating ur2
                             WHERE ur2.user_id = u.id
                               AND ur2.pkg_version_id = in_pkg_version_id)
            ORDER BY random()
            LIMIT 1;
        END LOOP;

    FOR a_record IN SELECT ur.id FROM haikudepot.user_rating ur WHERE ur.pkg_version_id = in_pkg_version_id
        LOOP
            SELECT id INTO a_user_id FROM candidate_users ORDER BY random() LIMIT 1;
            UPDATE haikudepot.user_rating ur SET user_id = a_user_id WHERE ur.id = a_record.id;
            DELETE FROM candidate_users WHERE id = a_user_id;
        END LOOP;

    DROP TABLE candidate_users;
END;
$$;

-- remove any captchas

DELETE FROM captcha.response;

-- remove any password change tokens

DELETE FROM haikudepot.user_password_reset_token;

-- reset all users' passwords to 'zimmer' and emails to some non-routable email
-- addresses.

UPDATE haikudepot.user SET
  nickname = 'user' || substr(md5(nickname || password_salt), 1, 6)
  WHERE nickname <> 'root';

UPDATE haikudepot.user SET
  password_hash = 'f925e5a026bb425cc5691e101605eacaf5f05f71a10cf5a9ffb2f96828f8a0c6',
  password_salt = 'cad3422ea02761f8',
  email = nickname || '@example.com';

-- remove special rights for all users except for root so that anybody getting
-- this database dump is not able to ascertain who might have root access.

UPDATE haikudepot.user SET
  is_root = false, can_manage_users = false
  WHERE nickname <> 'root';

-- now scramble the text of the comments as they are also effectively user-data.

UPDATE haikudepot.user_rating SET
  comment = hds_scramble_chars(comment);

-- now scramble the authors of the user rating comments by replacing the existing
-- author of a comment by another author of a different comment.

SELECT hds_swap_user_rating_users_for_pkg_version_id(pv.id)
FROM haikudepot.pkg_version pv
WHERE EXISTS(SELECT ur.id FROM haikudepot.user_rating ur WHERE ur.pkg_version_id = pv.id);

-- clean up

DROP FUNCTION hds_scramble_chars;
DROP FUNCTION hds_swap_user_rating_users_for_pkg_version_id;
