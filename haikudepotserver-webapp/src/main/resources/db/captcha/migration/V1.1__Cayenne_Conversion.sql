-- ------------------------------------------------------
-- CONVERT CAPTCHA TO USE ORM
-- ------------------------------------------------------

DELETE FROM captcha.responses;

ALTER TABLE captcha.responses RENAME TO response;

CREATE SEQUENCE captcha.response_seq START WITH 7294 INCREMENT BY 10;

ALTER TABLE captcha.response ADD COLUMN id BIGINT NOT NULL;

ALTER TABLE captcha.response ADD PRIMARY KEY (id);
