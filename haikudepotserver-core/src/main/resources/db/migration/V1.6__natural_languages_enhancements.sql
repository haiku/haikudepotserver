ALTER TABLE haikudepot.natural_language ADD COLUMN language_code VARCHAR(3);
ALTER TABLE haikudepot.natural_language ADD COLUMN script_code VARCHAR(4);
ALTER TABLE haikudepot.natural_language ADD COLUMN country_code VARCHAR(2);

DROP INDEX haikudepot.natural_language_idx01;
CREATE UNIQUE INDEX natural_language_idx01
    ON haikudepot.natural_language USING btree (language_code, script_code, country_code);

ALTER TABLE ONLY haikudepot.natural_language
    ADD CONSTRAINT natural_language_ck01 CHECK ( language_code ~ '^[a-z]{2,3}$' );
ALTER TABLE ONLY haikudepot.natural_language
    ADD CONSTRAINT natural_language_ck02 CHECK ( script_code ~ '^[A-Z][a-z]{3}$' );
ALTER TABLE ONLY haikudepot.natural_language
    ADD CONSTRAINT natural_language_ck03 CHECK ( country_code ~ '^[A-Z0-9]{2,3}$' );
-- ^ note that some countries such as "Latin America" are represented by three digits instead.

UPDATE haikudepot.natural_language SET language_code = code;

ALTER TABLE haikudepot.natural_language ALTER COLUMN language_code SET NOT NULL;

ALTER TABLE haikudepot.natural_language DROP COLUMN code;