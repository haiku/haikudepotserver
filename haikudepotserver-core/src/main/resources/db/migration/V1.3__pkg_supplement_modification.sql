-- add new data structure for the pkg supplement modifications.

CREATE TABLE haikudepot.pkg_supplement_modification
(
    id                        BIGINT         NOT NULL,
    create_timestamp          TIMESTAMP      NOT NULL,
    modify_timestamp          TIMESTAMP      NOT NULL,
    pkg_supplement_id         BIGINT         NOT NULL,
    user_id                   BIGINT NULL,
    content                   VARCHAR(65535) NOT NULL,
    origin_system_description VARCHAR(1024) NULL,
    user_description          VARCHAR(1024) NULL,
    PRIMARY KEY (id)
)
;

CREATE INDEX pkg_supplement_modification_idx02
    ON haikudepot.pkg_supplement_modification USING btree (pkg_supplement_id);

ALTER TABLE haikudepot.pkg_supplement_modification
    ADD FOREIGN KEY (user_id) REFERENCES haikudepot.user (id)
;

ALTER TABLE haikudepot.pkg_supplement_modification
    ADD FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement (id)
;

CREATE SEQUENCE captcha.pkg_supplement_modification_seq
    START WITH 236482
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;