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
  password_hash = 'c65fef911c57a7f8b5db96c7b291586d53e80ab654a33a6195aff5dcdf3246d2',
  password_salt = '984ad9a98d9c52d257b35dea5fde02ef07f65ccf0a08dd42c6f5450488583266',
  email = nickname || '@example.com';

-- remove special rights for all users except for root so that anybody getting
-- this database dump is not able to ascertain who might have root access.

UPDATE haikudepot.user SET
  is_root = false, can_manage_users = false
  WHERE nickname <> 'root';

-- now scramble the text of the comments as they are also effectively user-data.

UPDATE haikudepot.user_rating SET
  comment = hds_scramble_chars(comment);

-- clean up

DROP FUNCTION hds_scramble_chars;
