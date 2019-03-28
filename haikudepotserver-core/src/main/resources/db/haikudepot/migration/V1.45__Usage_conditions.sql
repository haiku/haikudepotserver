-- A small addition to the user data structures to hold settings related to
-- users agreeing to the user usage conditions.

CREATE TABLE haikudepot.user_usage_conditions_agreement (
  id BIGINT NOT NULL,
  active BOOLEAN NOT NULL,
  user_id BIGINT NOT NULL,
  timestamp_agreed TIMESTAMP NOT NULL,
  user_usage_conditions_id BIGINT NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE haikudepot.user_usage_conditions (
  id BIGINT NOT NULL,
  code VARCHAR(255) NOT NULL,
  copy_markdown VARCHAR (65535) NOT NULL,
  ordering INTEGER NOT NULL,
  minimum_age INTEGER NOT NULL,
  PRIMARY KEY (id)
);

ALTER TABLE haikudepot.user_usage_conditions_agreement
  ADD FOREIGN KEY (user_usage_conditions_id)
    REFERENCES haikudepot.user_usage_conditions (id)
    DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE haikudepot.user_usage_conditions_agreement
  ADD FOREIGN KEY (user_id)
    REFERENCES haikudepot.user (id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX user_usage_conditions_agreement_idx01 ON
  haikudepot.user_usage_conditions_agreement(user_id, user_usage_conditions_id);

CREATE UNIQUE INDEX user_usage_conditions_idx01 ON
  haikudepot.user_usage_conditions(code);

CREATE SEQUENCE haikudepot.user_usage_conditions_agreement_seq
  START WITH 1992 INCREMENT BY 1;

CREATE SEQUENCE haikudepot.user_usage_conditions_seq
  START WITH 8112 INCREMENT BY 1;