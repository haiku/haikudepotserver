-- initial setup of the job schema

CREATE SCHEMA job;

CREATE SEQUENCE job.job_seq
    START WITH 523
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_state_seq
    START WITH 523
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_type_seq
    START WITH 632
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_specification_seq
    START WITH 121
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_supplied_data_seq
    START WITH 564
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_generated_data_seq
    START WITH 299
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_data_encoding_seq
    START WITH 126
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE job.job_data_media_type_seq
    START WITH 882
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE job.job
(
    id                  bigint                      NOT NULL,
    modify_timestamp    timestamp without time zone NOT NULL,
    create_timestamp    timestamp without time zone NOT NULL,
    code                varchar(255)                NOT NULL,
    job_type_id         bigint                      NOT NULL,
    expiry_timestamp    timestamp without time zone,
    owner_user_nickname varchar(255),
    job_state_id bigint NOT NULL,
    job_specification_id bigint NOT NULL
);

CREATE TABLE job.job_state
(
    id               bigint                      NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    start_timestamp  timestamp without time zone,
    finish_timestamp timestamp without time zone,
    queue_timestamp  timestamp without time zone,
    fail_timestamp   timestamp without time zone,
    cancel_timestamp timestamp without time zone,
    progress_percent int
);

-- The `job_id` can be NULL because you can add `job_data`
-- prior to creating a job and then associate them later on.

CREATE TABLE job.job_supplied_data
(
    id                     bigint                      NOT NULL,
    job_id                 bigint,
    modify_timestamp       timestamp without time zone NOT NULL,
    create_timestamp       timestamp without time zone NOT NULL,
    code                   varchar(255)                NOT NULL,
    use_code               varchar(255),
    storage_code           varchar(255)                NOT NULL,
    job_data_encoding_id   bigint                      NOT NULL,
    job_data_media_type_id bigint                      NOT NULL
);

CREATE TABLE job.job_generated_data
(
    id                     bigint                      NOT NULL,
    job_state_id                 bigint                      NOT NULL,
    modify_timestamp       timestamp without time zone NOT NULL,
    create_timestamp       timestamp without time zone NOT NULL,
    code                   varchar(255)                NOT NULL,
    use_code               varchar(255)                NOT NULL,
    storage_code           varchar(255)                NOT NULL,
    job_data_encoding_id   bigint                      NOT NULL,
    job_data_media_type_id bigint                      NOT NULL
);

CREATE TABLE job.job_data_encoding
(
    id   bigint       NOT NULL,
    code varchar(255) NOT NULL
);

CREATE TABLE job.job_data_media_type
(
    id   bigint       NOT NULL,
    code varchar(255) NOT NULL
);

CREATE TABLE job.job_specification
(
    id               bigint                      NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    data             jsonb                       NOT NULL
);

CREATE TABLE job.job_type
(
    id   bigint       NOT NULL,
    code varchar(255) NOT NULL
);

ALTER TABLE ONLY job.job
    ADD CONSTRAINT job_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_state
    ADD CONSTRAINT job_state_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_supplied_data
    ADD CONSTRAINT job_data_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_data_encoding
    ADD CONSTRAINT job_data_encoding_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_data_media_type
    ADD CONSTRAINT job_data_media_type_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_specification
    ADD CONSTRAINT job_specification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY job.job_type
    ADD CONSTRAINT job_type_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX job_idx01 ON job.job USING btree (code);
CREATE UNIQUE INDEX job_idx02 ON job.job USING btree (job_state_id);
CREATE UNIQUE INDEX job_idx03 ON job.job USING btree (job_specification_id);
CREATE UNIQUE INDEX job_supplied_data_idx01 ON job.job_supplied_data USING btree (code);
CREATE UNIQUE INDEX job_generated_data_idx01 ON job.job_generated_data USING btree (code);
CREATE UNIQUE INDEX job_data_encoding_idx01 ON job.job_data_encoding USING btree (code);
CREATE UNIQUE INDEX job_data_media_type_idx01 ON job.job_data_media_type USING btree (code);
CREATE UNIQUE INDEX job_type_idx01 ON job.job_type USING btree (code);

ALTER TABLE ONLY job.job
    ADD CONSTRAINT job_type_id_fkey FOREIGN KEY (job_type_id) REFERENCES job.job_type (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job
    ADD CONSTRAINT job_state_id_fkey FOREIGN KEY (job_state_id) REFERENCES job.job_state (id)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job
    ADD CONSTRAINT job_specification_id_fkey FOREIGN KEY (job_specification_id) REFERENCES job.job_specification (id)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_generated_data
    ADD CONSTRAINT job_generated_data_job_state_id_fkey FOREIGN KEY (job_state_id) REFERENCES job.job_state (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_generated_data
    ADD CONSTRAINT job_generated_data_encoding_id_fkey FOREIGN KEY (job_data_encoding_id) REFERENCES job.job_data_encoding (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_generated_data
    ADD CONSTRAINT job_generated_data_media_type_id_fkey FOREIGN KEY (job_data_media_type_id) REFERENCES job.job_data_media_type (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_supplied_data
    ADD CONSTRAINT job_supplied_data_job_id_fkey FOREIGN KEY (job_id) REFERENCES job.job (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_supplied_data
    ADD CONSTRAINT job_supplied_data_encoding_id_fkey FOREIGN KEY (job_data_encoding_id) REFERENCES job.job_data_encoding (id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY job.job_supplied_data
    ADD CONSTRAINT job_supplied_data_media_type_id_fkey FOREIGN KEY (job_data_media_type_id) REFERENCES job.job_data_media_type (id)
        DEFERRABLE INITIALLY DEFERRED;

INSERT INTO job.job_data_encoding (id, code)
VALUES (NEXTVAL('job.job_data_encoding_seq'), 'none');
INSERT INTO job.job_data_encoding (id, code)
VALUES (NEXTVAL('job.job_data_encoding_seq'), 'gzip');

ALTER TABLE ONLY job.job_type
    ADD CONSTRAINT job_type_ck01 CHECK ( code ~ '^[a-z0-9]+$' );

ALTER TABLE ONLY job.job_data_media_type
    ADD CONSTRAINT job_data_media_type_ck01 CHECK ( code ~ '^\w+/[-+.\w]+$' );