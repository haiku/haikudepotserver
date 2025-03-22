INSERT INTO haikudepot.natural_language
(id, name, is_popular, create_timestamp,
 modify_timestamp, language_code, script_code, country_code)
VALUES (NEXTVAL('haikudepot.natural_language_seq'),
        'Chinese (Traditional)',
        false,
        now(),
        now(),
        'zh',
        'Hant',
        NULL);