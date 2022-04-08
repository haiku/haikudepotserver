--
-- Copyright 2022, Andrew Lindesay
-- Distributed under the terms of the MIT License.
--
-- -------------
--
-- This script is designed to be run as the root user on a new PG database
-- in order to prepare the database to be used for integration testing.
-- This script is only used in the case that the database is started as a
-- child process of the test operating system process; something that might
-- happen in a docker container.

CREATE USER "haikudepotserver_integrationtest" WITH PASSWORD 'haikudepotserver_integrationtest';
CREATE DATABASE "haikudepotserver_integrationtest" OWNER "haikudepotserver_integrationtest";
