ALTER TABLE job2.job
    ADD COLUMN data_timestamp timestamp without time zone;

ALTER TABLE job2.job
    ADD COLUMN description VARCHAR(1024);

CREATE SEQUENCE job2.job_tag_seq
    START WITH 191919
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE job2.job_tag
(
    code   VARCHAR(255)  NOT NULL,
    value  VARCHAR(1024) NOT NULL,
    id     BIGINT        NOT NULL,
    job_id BIGINT        NOT NULL,
    PRIMARY KEY (id)
)
;

ALTER TABLE ONLY job2.job_tag
    ADD CONSTRAINT job_tag_job_id_fkey FOREIGN KEY (job_id) REFERENCES job2.job (id) DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX job_tag_idx01 ON job2.job_tag USING btree (job_id, code);
