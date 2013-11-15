-- ------------------------------------------------------
-- SETUP CAPTCHA SCHEMA OBJECTS
-- ------------------------------------------------------

-- Flyway will take care of this.
-- CREATE SCHEMA captcha;

CREATE TABLE captcha.responses (token CHAR(36) NOT NULL, response VARCHAR(255) NOT NULL, create_timestamp TIMESTAMP NOT NULL);
CREATE UNIQUE INDEX responses_idx01 ON captcha.responses(token);



