-- Unfortunately the default root password does not seem to work so here
-- we reset it to a default. The password here is `zimmer`.

UPDATE haikudepot.user
SET password_hash = 'f925e5a026bb425cc5691e101605eacaf5f05f71a10cf5a9ffb2f96828f8a0c6',
    password_salt = 'cad3422ea02761f8'
WHERE 1 = 1
  AND nickname = 'root'
  AND last_authentication_timestamp IS NULL
  AND password_hash = '1debaaa4aa99077a0cb564d9ef94cf645bb64323871d2dc70668703c7791172a';