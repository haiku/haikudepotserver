-- Puts in place a data schema for storing binary data in the database in streams.
-- This is used in the job system.

CREATE SCHEMA datastore;

CREATE SEQUENCE datastore.object_head_seq
    START WITH 5633
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


CREATE SEQUENCE datastore.object_part_seq
    START WITH 5633
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE datastore.object_part_ordering_seq
    START WITH 2341
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE datastore.object_head
(
    id                bigint                      NOT NULL,
    modify_timestamp  timestamp without time zone NOT NULL,
    create_timestamp  timestamp without time zone NOT NULL,
    length bigint NOT NULL,
    code              varchar(256)                NOT NULL
);

CREATE TABLE datastore.object_part
(
    id   bigint NOT NULL,
    object_head_id bigint NOT NULL,
    data bytea  NOT NULL,
    length bigint NOT NULL,
    ordering int NOT NULL
);


ALTER TABLE ONLY datastore.object_part
    ADD CONSTRAINT object_part_pkey PRIMARY KEY (id);

ALTER TABLE ONLY datastore.object_head
    ADD CONSTRAINT object_head_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX object_head_idx01 ON datastore.object_head USING btree (code);

CREATE UNIQUE INDEX object_part_idx01 ON datastore.object_part USING btree (ordering, id);

ALTER TABLE ONLY datastore.object_part
    ADD CONSTRAINT object_id_fkey FOREIGN KEY (object_head_id) REFERENCES datastore.object_head(id)
        DEFERRABLE INITIALLY DEFERRED;