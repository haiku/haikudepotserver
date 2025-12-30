-- Implements a database structure for storing jobs. This is a new structure that is used from
-- Apache Cayenne rather than the old one that uses Hibernate and is "job 1".

CREATE SCHEMA job2;

CREATE SEQUENCE job2.job_seq
    START WITH 221
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_data_seq
    START WITH 412
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_assignment_seq
    START WITH 911
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_data_encoding_seq
    START WITH 972
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_data_type_seq
    START WITH 752
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_type_seq
    START WITH 811
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job2.job_data_media_type_seq
    START WITH 811
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE job2.job_data_encoding
(
    code VARCHAR(255) NOT NULL,
    id   BIGINT       NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job_data_type
(
    code VARCHAR(255) NOT NULL,
    id   BIGINT       NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job_type
(
    code VARCHAR(48) NOT NULL,
    id   BIGINT      NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job_data_media_type
(
    code VARCHAR(255) NOT NULL,
    id   BIGINT       NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job_assignment
(
    code VARCHAR(36) NULL,
    id   BIGINT      NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job
(
    code                 VARCHAR(36)   NULL,
    expiry_timestamp     TIMESTAMP     NOT NULL,
    id                   BIGINT        NOT NULL,
    job_type_id          BIGINT        NOT NULL,
    job_assignment_id   BIGINT        NOT NULL,
    modify_timestamp     TIMESTAMP     NOT NULL,
    owner_user_nickname  VARCHAR(255)  NULL,
    cancel_timestamp     TIMESTAMP     NULL,
    create_timestamp     TIMESTAMP     NOT NULL,
    fail_timestamp       TIMESTAMP     NULL,
    finish_timestamp     TIMESTAMP     NULL,
    progress_percent     INTEGER       NULL,
    queue_timestamp      TIMESTAMP     NULL,
    start_timestamp      TIMESTAMP     NULL,
    specification        VARCHAR(8192) NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE TABLE job2.job_data
(
    code                   VARCHAR(48)  NOT NULL,
    create_timestamp       TIMESTAMP    NOT NULL,
    id                     BIGINT       NOT NULL,
    job_data_encoding_id   BIGINT       NOT NULL,
    job_data_media_type_id BIGINT       NOT NULL,
    job_data_type_id       BIGINT       NOT NULL,
    job_id                 BIGINT       NULL,
    modify_timestamp       TIMESTAMP    NOT NULL,
    storage_code           VARCHAR(255) NOT NULL,
    use_code               VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
)
;

CREATE UNIQUE INDEX job_idx01 ON job2.job USING btree (code);
CREATE UNIQUE INDEX job_assignment_idx01 ON job2.job_assignment USING btree (code);
CREATE UNIQUE INDEX job_data_idx01 ON job2.job_data USING btree (code);
CREATE UNIQUE INDEX job_data_encoding_idx01 ON job2.job_data_encoding USING btree (code);
CREATE UNIQUE INDEX job_data_media_type_idx01 ON job2.job_data_media_type USING btree (code);
CREATE UNIQUE INDEX job_type_idx01 ON job2.job_type USING btree (code);
CREATE UNIQUE INDEX job_data_type_idx01 ON job2.job_data_type USING btree (code);

ALTER TABLE job2.job
    ADD CONSTRAINT job_type_id_fkey
        FOREIGN KEY (job_type_id) REFERENCES job2.job_type (id)
;

ALTER TABLE job2.job
    ADD CONSTRAINT job_assignment_id_fkey
        FOREIGN KEY (job_assignment_id) REFERENCES job2.job_assignment (id)
;

ALTER TABLE job2.job_data
    ADD CONSTRAINT job_id_fkey
        FOREIGN KEY (job_id) REFERENCES job2.job (id)
;

ALTER TABLE job2.job_data
    ADD CONSTRAINT job_data_encoding_id_fkey
        FOREIGN KEY (job_data_encoding_id) REFERENCES job2.job_data_encoding (id)
;

ALTER TABLE job2.job_data
    ADD CONSTRAINT job_data_media_type_id_fkey
        FOREIGN KEY (job_data_media_type_id) REFERENCES job2.job_data_media_type (id)
;

ALTER TABLE job2.job_data
    ADD CONSTRAINT job_data_type_id_fkey
        FOREIGN KEY (job_data_type_id) REFERENCES job2.job_data_type (id);

INSERT INTO job2.job_data_encoding (id, code)
VALUES (NEXTVAL('job2.job_data_encoding_seq'), 'none');
INSERT INTO job2.job_data_encoding (id, code)
VALUES (NEXTVAL('job2.job_data_encoding_seq'), 'gzip');

INSERT INTO job2.job_data_type (id, code)
VALUES (NEXTVAL('job2.job_data_type_seq'), 'supplied');
INSERT INTO job2.job_data_type (id, code)
VALUES (NEXTVAL('job2.job_data_type_seq'), 'generated');

ALTER TABLE ONLY job2.job_type
    ADD CONSTRAINT job_type_ck01 CHECK ( code ~ '^[a-z0-9]+$' );

ALTER TABLE ONLY job2.job_data_media_type
    ADD CONSTRAINT job_data_media_type_ck01 CHECK ( code ~ '^\w+/[-+.\w]+$' );