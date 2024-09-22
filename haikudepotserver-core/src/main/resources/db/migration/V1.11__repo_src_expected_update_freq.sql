-- Added attributes to the Repository Source to indicate that the Repository Source
-- expects to see updates within a certain time-frame.

ALTER TABLE haikudepot.repository_source
    ADD COLUMN expected_update_frequency_hours INT;

UPDATE haikudepot.repository_source
SET expected_update_frequency_hours = 48
WHERE code IN ('haikuports_x86_64', 'haikuports_x86_gcc2');