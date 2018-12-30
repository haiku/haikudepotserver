-- This patch means that the user's last authentication with the system is
-- recorded as a timestamp.

ALTER TABLE haikudepot.user ADD COLUMN last_authentication_timestamp TIMESTAMP;