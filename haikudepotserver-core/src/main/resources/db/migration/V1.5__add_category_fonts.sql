INSERT INTO haikudepot.pkg_category (id, code, name, create_timestamp, modify_timestamp)
    VALUES (
            NEXTVAL('haikudepot.pkg_category_seq'),
            'fonts', 'Fonts',
            now(), now());