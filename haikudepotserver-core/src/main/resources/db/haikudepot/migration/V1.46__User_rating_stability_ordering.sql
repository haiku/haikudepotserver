ALTER TABLE haikudepot.user_rating_stability ADD COLUMN ordering INTEGER;

UPDATE haikudepot.user_rating_stability SET ordering = 100 WHERE code = 'nostart';
UPDATE haikudepot.user_rating_stability SET ordering = 200 WHERE code = 'veryunstable';
UPDATE haikudepot.user_rating_stability SET ordering = 300 WHERE code = 'unstablebutusable';
UPDATE haikudepot.user_rating_stability SET ordering = 400 WHERE code = 'mostlystable';
UPDATE haikudepot.user_rating_stability SET ordering = 500 WHERE code = 'stable';

ALTER TABLE haikudepot.user_rating_stability ALTER COLUMN ordering SET NOT NULL;
