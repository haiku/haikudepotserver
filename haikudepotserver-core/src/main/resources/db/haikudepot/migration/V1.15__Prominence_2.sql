ALTER TABLE haikudepot.pkg ALTER COLUMN prominence_id SET NOT NULL;

ALTER TABLE haikudepot.pkg ADD FOREIGN KEY (prominence_id) REFERENCES haikudepot.prominence (id);
