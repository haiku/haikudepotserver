-- adding new persisted structure for keeping track of how often a natural language is used.

CREATE SEQUENCE haikudepot.natural_language_use_seq
    START WITH 992233
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.natural_language_use
(
    id                  BIGINT    NOT NULL,
    natural_language_id BIGINT    NOT NULL,
    last_use_timestamp  TIMESTAMP NOT NULL,
    create_timestamp    TIMESTAMP NOT NULL,
    modify_timestamp    TIMESTAMP NOT NULL,
    count               BIGINT    NOT NULL
);

ALTER TABLE ONLY haikudepot.natural_language_use
    ADD CONSTRAINT natural_language_use_natural_language_fkey
        FOREIGN KEY (natural_language_id)
            REFERENCES haikudepot.natural_language (id)
            DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX natural_language_use_idx01 ON haikudepot.natural_language_use USING btree (natural_language_id);

ALTER TABLE ONLY haikudepot.natural_language_use
    ADD CONSTRAINT natural_language_use_pkey PRIMARY KEY (id);