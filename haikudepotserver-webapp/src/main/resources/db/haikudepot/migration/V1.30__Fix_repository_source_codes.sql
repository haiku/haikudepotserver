-- Reformatting the repository source codes

UPDATE haikudepot.repository_source SET code = 'haikuports_x86' WHERE code='haikuportsx86';
UPDATE haikudepot.repository_source SET code = 'haiku_x86' WHERE code='haikux86';
UPDATE haikudepot.repository_source SET code = 'haiku_x86_gcc2' WHERE code='haikux86gcc2';
UPDATE haikudepot.repository_source SET code = 'haiku_x86_64' WHERE code='haikux8664';
UPDATE haikudepot.repository_source SET code = 'haikuports_x86_gcc2' WHERE code='haikuportsx86gcc2';
UPDATE haikudepot.repository_source SET code = 'haikuports_x86_64' WHERE code='haikuportsx8664';
