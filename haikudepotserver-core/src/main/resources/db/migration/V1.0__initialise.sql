CREATE SCHEMA captcha;
CREATE SCHEMA haikudepot;

CREATE TABLE captcha.response (
    token character(36) NOT NULL,
    response character varying(255) NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    id bigint NOT NULL
);

CREATE SEQUENCE captcha.response_seq
    START WITH 17294
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.architecture (
    code character varying(255) NOT NULL,
    id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.architecture_seq
    START WITH 12862
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.country (
    id bigint NOT NULL,
    code character varying(2) NOT NULL,
    name character varying(255) NOT NULL,
    active boolean NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL
);

CREATE SEQUENCE haikudepot.country_seq
    START WITH 19312
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.localization_content (
    id bigint NOT NULL,
    content character varying(65535) NOT NULL
);

CREATE SEQUENCE haikudepot.localization_content_seq
    START WITH 17777
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.media_type (
    code character varying(255) NOT NULL,
    id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.media_type_seq
    START WITH 14195
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.natural_language (
    code character varying(32) NOT NULL,
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    is_popular boolean NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL
);

CREATE SEQUENCE haikudepot.natural_language_seq
    START WITH 19400
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.permission (
    id bigint NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);

CREATE SEQUENCE haikudepot.permission_seq
    START WITH 16999
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.permission_user_pkg (
    id bigint NOT NULL,
    permission_id bigint NOT NULL,
    user_id bigint NOT NULL,
    pkg_id bigint,
    create_timestamp timestamp without time zone NOT NULL
);

CREATE SEQUENCE haikudepot.permission_user_pkg_seq
    START WITH 15554
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg (
    active boolean NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    id bigint NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    name character varying(255) NOT NULL,
    publisher_id bigint,
    pkg_supplement_id bigint NOT NULL
);

CREATE TABLE haikudepot.pkg_category (
    code character varying(32) NOT NULL,
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_category_seq
    START WITH 18211
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_changelog (
    id bigint NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    pkg_supplement_id bigint NOT NULL,
    content character varying(65535) NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_changelog_seq
    START WITH 14121
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_icon (
    id bigint NOT NULL,
    media_type_id bigint NOT NULL,
    pkg_supplement_id bigint NOT NULL,
    size integer
);

CREATE TABLE haikudepot.pkg_icon_image (
    data bytea NOT NULL,
    id bigint NOT NULL,
    pkg_icon_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_icon_image_seq
    START WITH 12175
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.pkg_icon_seq
    START WITH 13385
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_localization (
    id bigint NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    pkg_supplement_id bigint NOT NULL,
    natural_language_id bigint NOT NULL,
    title character varying(1024),
    summary character varying(8192),
    description character varying(8192)
);

CREATE SEQUENCE haikudepot.pkg_localization_seq
    START WITH 18821
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_pkg_category (
    id bigint NOT NULL,
    pkg_category_id bigint NOT NULL,
    pkg_supplement_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_pkg_category_seq
    START WITH 16194
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_prominence (
    id bigint NOT NULL,
    pkg_id bigint NOT NULL,
    repository_id bigint NOT NULL,
    prominence_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_prominence_seq
    START WITH 12166
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_screenshot (
    code character(36) NOT NULL,
    id bigint NOT NULL,
    ordering integer NOT NULL,
    pkg_supplement_id bigint NOT NULL,
    length integer NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    hash_sha256 character varying(64) NOT NULL
);

CREATE TABLE haikudepot.pkg_screenshot_image (
    data bytea NOT NULL,
    id bigint NOT NULL,
    media_type_id bigint NOT NULL,
    pkg_screenshot_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_screenshot_image_seq
    START WITH 17332
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.pkg_screenshot_seq
    START WITH 11922
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.pkg_seq
    START WITH 19812
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_supplement (
    id bigint NOT NULL,
    base_pkg_name character varying(255) NOT NULL,
    icon_modify_timestamp timestamp without time zone,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_supplement_seq
    START WITH 1826
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_url_type (
    code character varying(255) NOT NULL,
    id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_url_type_seq
    START WITH 12235
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_user_rating_aggregate (
    id bigint NOT NULL,
    pkg_id bigint NOT NULL,
    repository_id bigint NOT NULL,
    derived_rating double precision NOT NULL,
    derived_rating_sample_size integer NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_user_rating_aggregate_seq
    START WITH 14184
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_version (
    active boolean NOT NULL,
    architecture_id bigint NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    id bigint NOT NULL,
    major character varying(255) NOT NULL,
    micro character varying(255),
    minor character varying(255),
    modify_timestamp timestamp without time zone NOT NULL,
    pkg_id bigint NOT NULL,
    pre_release character varying(255),
    repository_source_id bigint NOT NULL,
    revision integer,
    view_counter bigint NOT NULL,
    is_latest boolean NOT NULL,
    payload_length bigint
);

CREATE TABLE haikudepot.pkg_version_copyright (
    body character varying(4096) NOT NULL,
    id bigint NOT NULL,
    pkg_version_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_version_copyright_seq
    START WITH 18684
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_version_license (
    body character varying(4096) NOT NULL,
    id bigint NOT NULL,
    pkg_version_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.pkg_version_license_seq
    START WITH 13826
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_version_localization (
    id bigint NOT NULL,
    pkg_version_id bigint,
    natural_language_id bigint NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    summary_localization_content_id bigint,
    description_localization_content_id bigint,
    title_localization_content_id bigint
);

CREATE SEQUENCE haikudepot.pkg_version_localization_seq
    START WITH 14422
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.pkg_version_seq
    START WITH 12736
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.pkg_version_url (
    id bigint NOT NULL,
    pkg_url_type_id bigint NOT NULL,
    pkg_version_id bigint NOT NULL,
    url character varying(1024) NOT NULL,
    name character varying(1024)
);

CREATE SEQUENCE haikudepot.pkg_version_url_seq
    START WITH 18364
    INCREMENT BY 10
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.prominence (
    id bigint NOT NULL,
    ordering integer NOT NULL,
    name character varying(255) NOT NULL
);

CREATE SEQUENCE haikudepot.prominence_seq
    START WITH 13112
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.publisher (
    active boolean NOT NULL,
    code character varying(255),
    create_timestamp timestamp without time zone NOT NULL,
    email character varying(1024),
    id bigint NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    name character varying(1024) NOT NULL,
    site_url character varying(1024)
);

CREATE SEQUENCE haikudepot.publisher_seq
    START WITH 19373
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.repository (
    id bigint NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(1024),
    active boolean NOT NULL,
    information_url character varying(1024),
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    password_hash character varying(255),
    password_salt character varying(255)
);

CREATE SEQUENCE haikudepot.repository_seq
    START WITH 17629
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.repository_source (
    active boolean NOT NULL,
    code character varying(255) NOT NULL,
    id bigint NOT NULL,
    repository_id bigint NOT NULL,
    identifier character varying(4096),
    forced_internal_base_url character varying(1024),
    last_import_timestamp timestamp without time zone,
    architecture_id bigint
);

CREATE TABLE haikudepot.repository_source_extra_identifier (
    id bigint NOT NULL,
    identifier character varying(4096) NOT NULL,
    repository_source_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.repository_source_extra_identifier_seq
    START WITH 913376
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.repository_source_mirror (
    id bigint NOT NULL,
    repository_source_id bigint NOT NULL,
    country_id bigint NOT NULL,
    base_url character varying(1024) NOT NULL,
    description character varying(4096),
    active boolean NOT NULL,
    is_primary boolean NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    code character varying(36) NOT NULL
);

CREATE SEQUENCE haikudepot.repository_source_mirror_seq
    START WITH 14121
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.repository_source_seq
    START WITH 19173
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot."user" (
    active boolean NOT NULL,
    can_manage_users boolean NOT NULL,
    id bigint NOT NULL,
    is_root boolean NOT NULL,
    nickname character varying(32) NOT NULL,
    password_hash character varying(255) NOT NULL,
    password_salt character varying(255) NOT NULL,
    natural_language_id bigint NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    email character varying(256),
    last_authentication_timestamp timestamp without time zone
);

CREATE TABLE haikudepot.user_password_reset_token (
    id bigint NOT NULL,
    code character varying(255) NOT NULL,
    create_timestamp timestamp without time zone NOT NULL,
    user_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.user_password_reset_token_seq
    START WITH 14251
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.user_rating (
    active boolean NOT NULL,
    comment character varying(4096),
    create_timestamp timestamp without time zone NOT NULL,
    user_rating_stability_id bigint,
    id bigint NOT NULL,
    modify_timestamp timestamp without time zone NOT NULL,
    pkg_version_id bigint NOT NULL,
    rating smallint,
    natural_language_id bigint NOT NULL,
    user_id bigint NOT NULL,
    code character varying(255) NOT NULL
);

CREATE SEQUENCE haikudepot.user_rating_seq
    START WITH 11022
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.user_rating_stability (
    code character varying(32) NOT NULL,
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    ordering integer NOT NULL
);

CREATE SEQUENCE haikudepot.user_rating_stability_seq
    START WITH 12153
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.user_seq
    START WITH 11247
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE haikudepot.user_usage_conditions (
    id bigint NOT NULL,
    code character varying(255) NOT NULL,
    copy_markdown character varying(65535) NOT NULL,
    ordering integer NOT NULL,
    minimum_age integer NOT NULL
);

CREATE TABLE haikudepot.user_usage_conditions_agreement (
    id bigint NOT NULL,
    active boolean NOT NULL,
    user_id bigint NOT NULL,
    timestamp_agreed timestamp without time zone NOT NULL,
    user_usage_conditions_id bigint NOT NULL
);

CREATE SEQUENCE haikudepot.user_usage_conditions_agreement_seq
    START WITH 11992
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE haikudepot.user_usage_conditions_seq
    START WITH 18112
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE ONLY captcha.response
    ADD CONSTRAINT response_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.architecture
    ADD CONSTRAINT architecture_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.country
    ADD CONSTRAINT country_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.localization_content
    ADD CONSTRAINT localization_content_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.media_type
    ADD CONSTRAINT media_type_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.natural_language
    ADD CONSTRAINT natural_language_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.permission_user_pkg
    ADD CONSTRAINT permission_user_pkg_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_category
    ADD CONSTRAINT pkg_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_changelog
    ADD CONSTRAINT pkg_changelog_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_icon_image
    ADD CONSTRAINT pkg_icon_image_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_icon
    ADD CONSTRAINT pkg_icon_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_localization
    ADD CONSTRAINT pkg_localization_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg
    ADD CONSTRAINT pkg_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_pkg_category
    ADD CONSTRAINT pkg_pkg_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_prominence
    ADD CONSTRAINT pkg_prominence_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_screenshot_image
    ADD CONSTRAINT pkg_screenshot_image_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_screenshot
    ADD CONSTRAINT pkg_screenshot_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_supplement
    ADD CONSTRAINT pkg_supplement_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_url_type
    ADD CONSTRAINT pkg_url_type_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_user_rating_aggregate
    ADD CONSTRAINT pkg_user_rating_aggregate_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_version_copyright
    ADD CONSTRAINT pkg_version_copyright_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_version_license
    ADD CONSTRAINT pkg_version_license_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_version_localization
    ADD CONSTRAINT pkg_version_localization_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_version
    ADD CONSTRAINT pkg_version_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.pkg_version_url
    ADD CONSTRAINT pkg_version_url_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.prominence
    ADD CONSTRAINT prominence_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.publisher
    ADD CONSTRAINT publisher_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.repository
    ADD CONSTRAINT repository_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.repository_source_extra_identifier
    ADD CONSTRAINT repository_source_extra_identifier_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.repository_source_mirror
    ADD CONSTRAINT repository_source_mirror_is_primary_repository_source_id_excl EXCLUDE USING btree (is_primary WITH =, repository_source_id WITH =) WHERE (is_primary) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.repository_source_mirror
    ADD CONSTRAINT repository_source_mirror_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.repository_source
    ADD CONSTRAINT repository_source_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.user_password_reset_token
    ADD CONSTRAINT user_password_reset_token_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot."user"
    ADD CONSTRAINT user_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.user_rating
    ADD CONSTRAINT user_rating_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.user_rating_stability
    ADD CONSTRAINT user_rating_stability_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.user_usage_conditions_agreement
    ADD CONSTRAINT user_usage_conditions_agreement_pkey PRIMARY KEY (id);

ALTER TABLE ONLY haikudepot.user_usage_conditions
    ADD CONSTRAINT user_usage_conditions_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX responses_idx01 ON captcha.response USING btree (token);

CREATE UNIQUE INDEX architecture_idx01 ON haikudepot.architecture USING btree (code);

CREATE UNIQUE INDEX country_idx01 ON haikudepot.country USING btree (code);

CREATE UNIQUE INDEX localization_content_idx01 ON haikudepot.localization_content USING btree (content);

CREATE UNIQUE INDEX natural_language_idx01 ON haikudepot.natural_language USING btree (code);

CREATE UNIQUE INDEX pkg_category_idx01 ON haikudepot.pkg_category USING btree (code);

CREATE UNIQUE INDEX pkg_changelog_idx01 ON haikudepot.pkg_changelog USING btree (pkg_supplement_id);

CREATE UNIQUE INDEX pkg_icon_idx01 ON haikudepot.pkg_icon USING btree (size, media_type_id, pkg_supplement_id);

CREATE UNIQUE INDEX pkg_idx01 ON haikudepot.pkg USING btree (name);

CREATE INDEX pkg_idx02 ON haikudepot.pkg USING btree (publisher_id);

CREATE INDEX pkg_idx03 ON haikudepot.pkg USING btree (pkg_supplement_id);

CREATE UNIQUE INDEX pkg_localization_idx01 ON haikudepot.pkg_localization USING btree (pkg_supplement_id, natural_language_id);

CREATE UNIQUE INDEX pkg_pkg_category_idx01 ON haikudepot.pkg_pkg_category USING btree (pkg_supplement_id, pkg_category_id);

CREATE UNIQUE INDEX pkg_prominence_idx01 ON haikudepot.pkg_prominence USING btree (repository_id, pkg_id);

CREATE UNIQUE INDEX pkg_supplement_idx01 ON haikudepot.pkg_supplement USING btree (base_pkg_name);

CREATE UNIQUE INDEX pkg_url_type_idx01 ON haikudepot.pkg_url_type USING btree (code);

CREATE UNIQUE INDEX pkg_user_rating_aggregate_idx01 ON haikudepot.pkg_user_rating_aggregate USING btree (repository_id, pkg_id);

CREATE INDEX pkg_version_copyright_idx01 ON haikudepot.pkg_version_copyright USING btree (pkg_version_id);

CREATE UNIQUE INDEX pkg_version_idx01 ON haikudepot.pkg_version USING btree (pkg_id, major, minor, micro, revision, pre_release, architecture_id, repository_source_id);

CREATE INDEX pkg_version_idx02 ON haikudepot.pkg_version USING btree (repository_source_id);

CREATE INDEX pkg_version_idx03 ON haikudepot.pkg_version USING btree (architecture_id);

CREATE INDEX pkg_version_license_idx01 ON haikudepot.pkg_version_license USING btree (pkg_version_id);

CREATE UNIQUE INDEX pkg_version_localization_idx01 ON haikudepot.pkg_version_localization USING btree (pkg_version_id, natural_language_id);

CREATE INDEX pkg_version_url_idx01 ON haikudepot.pkg_version_url USING btree (pkg_url_type_id);

CREATE INDEX pkg_version_url_idx03 ON haikudepot.pkg_version_url USING btree (pkg_version_id);

CREATE UNIQUE INDEX prominence_idx01 ON haikudepot.prominence USING btree (ordering);

CREATE UNIQUE INDEX prominence_idx02 ON haikudepot.prominence USING btree (name);

CREATE UNIQUE INDEX publisher_idx01 ON haikudepot.publisher USING btree (code);

CREATE UNIQUE INDEX repository_idx01 ON haikudepot.repository USING btree (code);

CREATE UNIQUE INDEX repository_source_extra_identifier_idx01 ON haikudepot.repository_source_extra_identifier USING btree (repository_source_id, identifier);

CREATE UNIQUE INDEX repository_source_idx01 ON haikudepot.repository_source USING btree (code);

CREATE UNIQUE INDEX repository_source_mirror_idx02 ON haikudepot.repository_source_mirror USING btree (base_url, repository_source_id);

CREATE UNIQUE INDEX user_idx01 ON haikudepot."user" USING btree (nickname);

CREATE UNIQUE INDEX user_rating_idx01 ON haikudepot.user_rating USING btree (pkg_version_id, user_id);

CREATE INDEX user_rating_idx02 ON haikudepot.user_rating USING btree (user_rating_stability_id);

CREATE UNIQUE INDEX user_rating_stability_idx01 ON haikudepot.user_rating_stability USING btree (code);

CREATE UNIQUE INDEX user_usage_conditions_agreement_idx01 ON haikudepot.user_usage_conditions_agreement USING btree (user_id, user_usage_conditions_id);

CREATE UNIQUE INDEX user_usage_conditions_idx01 ON haikudepot.user_usage_conditions USING btree (code);

ALTER TABLE ONLY haikudepot.permission_user_pkg
    ADD CONSTRAINT permission_user_pkg_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES haikudepot.permission(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.permission_user_pkg
    ADD CONSTRAINT permission_user_pkg_pkg_id_fkey FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.permission_user_pkg
    ADD CONSTRAINT permission_user_pkg_user_id_fkey FOREIGN KEY (user_id) REFERENCES haikudepot."user"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_changelog
    ADD CONSTRAINT pkg_changelog_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_icon_image
    ADD CONSTRAINT pkg_icon_image_pkg_icon_id_fkey FOREIGN KEY (pkg_icon_id) REFERENCES haikudepot.pkg_icon(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_icon
    ADD CONSTRAINT pkg_icon_media_type_id_fkey FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_icon
    ADD CONSTRAINT pkg_icon_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_localization
    ADD CONSTRAINT pkg_localization_natural_language_id_fkey FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language(id);

ALTER TABLE ONLY haikudepot.pkg_localization
    ADD CONSTRAINT pkg_localization_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_pkg_category
    ADD CONSTRAINT pkg_pkg_category_pkg_category_id_fkey FOREIGN KEY (pkg_category_id) REFERENCES haikudepot.pkg_category(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_pkg_category
    ADD CONSTRAINT pkg_pkg_category_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg
    ADD CONSTRAINT pkg_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_prominence
    ADD CONSTRAINT pkg_prominence_pkg_fkey FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_prominence
    ADD CONSTRAINT pkg_prominence_repository_fkey FOREIGN KEY (repository_id) REFERENCES haikudepot.repository(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg
    ADD CONSTRAINT pkg_publisher_id_fkey FOREIGN KEY (publisher_id) REFERENCES haikudepot.publisher(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_screenshot_image
    ADD CONSTRAINT pkg_screenshot_image_media_type_id_fkey FOREIGN KEY (media_type_id) REFERENCES haikudepot.media_type(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_screenshot_image
    ADD CONSTRAINT pkg_screenshot_image_pkg_screenshot_id_fkey FOREIGN KEY (pkg_screenshot_id) REFERENCES haikudepot.pkg_screenshot(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_screenshot
    ADD CONSTRAINT pkg_screenshots_pkg_supplement_id_fkey FOREIGN KEY (pkg_supplement_id) REFERENCES haikudepot.pkg_supplement(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_user_rating_aggregate
    ADD CONSTRAINT pkg_user_rating_aggregate_pkg_fkey FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_user_rating_aggregate
    ADD CONSTRAINT pkg_user_rating_aggregate_repository_fkey FOREIGN KEY (repository_id) REFERENCES haikudepot.repository(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version
    ADD CONSTRAINT pkg_version_architecture_id_fkey FOREIGN KEY (architecture_id) REFERENCES haikudepot.architecture(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_copyright
    ADD CONSTRAINT pkg_version_copyright_pkg_version_id_fkey FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_license
    ADD CONSTRAINT pkg_version_license_pkg_version_id_fkey FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_localization
    ADD CONSTRAINT pkg_version_localization_description_localization_content__fkey FOREIGN KEY (description_localization_content_id) REFERENCES haikudepot.localization_content(id);

ALTER TABLE ONLY haikudepot.pkg_version_localization
    ADD CONSTRAINT pkg_version_localization_pkg_version_id_fkey FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_localization
    ADD CONSTRAINT pkg_version_localization_summary_localization_content_id_fkey FOREIGN KEY (summary_localization_content_id) REFERENCES haikudepot.localization_content(id);

ALTER TABLE ONLY haikudepot.pkg_version_localization
    ADD CONSTRAINT pkg_version_localization_title_localization_content_id_fkey FOREIGN KEY (title_localization_content_id) REFERENCES haikudepot.localization_content(id);

ALTER TABLE ONLY haikudepot.pkg_version
    ADD CONSTRAINT pkg_version_pkg_id_fkey FOREIGN KEY (pkg_id) REFERENCES haikudepot.pkg(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version
    ADD CONSTRAINT pkg_version_repository_source_id_fkey FOREIGN KEY (repository_source_id) REFERENCES haikudepot.repository_source(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_url
    ADD CONSTRAINT pkg_version_url_pkg_url_type_id_fkey FOREIGN KEY (pkg_url_type_id) REFERENCES haikudepot.pkg_url_type(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.pkg_version_url
    ADD CONSTRAINT pkg_version_url_pkg_version_id_fkey FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.repository_source
    ADD CONSTRAINT repository_source_architecture_id_fkey FOREIGN KEY (architecture_id) REFERENCES haikudepot.architecture(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.repository_source_extra_identifier
    ADD CONSTRAINT repository_source_extra_identifier_repository_source_id_fkey FOREIGN KEY (repository_source_id) REFERENCES haikudepot.repository_source(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.repository_source_mirror
    ADD CONSTRAINT repository_source_mirror_country_id_fkey FOREIGN KEY (country_id) REFERENCES haikudepot.country(id);

ALTER TABLE ONLY haikudepot.repository_source_mirror
    ADD CONSTRAINT repository_source_mirror_repository_source_id_fkey FOREIGN KEY (repository_source_id) REFERENCES haikudepot.repository_source(id);

ALTER TABLE ONLY haikudepot.repository_source
    ADD CONSTRAINT repository_source_repository_fkey FOREIGN KEY (repository_id) REFERENCES haikudepot.repository(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot."user"
    ADD CONSTRAINT user_natural_language_id_fkey FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_password_reset_token
    ADD CONSTRAINT user_password_reset_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES haikudepot."user"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_rating
    ADD CONSTRAINT user_rating_natural_language_id_fkey FOREIGN KEY (natural_language_id) REFERENCES haikudepot.natural_language(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_rating
    ADD CONSTRAINT user_rating_pkg_version_id_fkey FOREIGN KEY (pkg_version_id) REFERENCES haikudepot.pkg_version(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_rating
    ADD CONSTRAINT user_rating_user_id_fkey FOREIGN KEY (user_id) REFERENCES haikudepot."user"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_rating
    ADD CONSTRAINT user_rating_user_rating_stability_id_fkey FOREIGN KEY (user_rating_stability_id) REFERENCES haikudepot.user_rating_stability(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_usage_conditions_agreement
    ADD CONSTRAINT user_usage_conditions_agreement_user_id_fkey FOREIGN KEY (user_id) REFERENCES haikudepot."user"(id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE ONLY haikudepot.user_usage_conditions_agreement
    ADD CONSTRAINT user_usage_conditions_agreement_user_usage_conditions_id_fkey FOREIGN KEY (user_usage_conditions_id) REFERENCES haikudepot.user_usage_conditions(id) DEFERRABLE INITIALLY DEFERRED;
