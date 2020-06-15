-- here the 'url' from the repo info is renamed to 'identifier'.

ALTER TABLE haikudepot.repository_source RENAME COLUMN url TO identifier;