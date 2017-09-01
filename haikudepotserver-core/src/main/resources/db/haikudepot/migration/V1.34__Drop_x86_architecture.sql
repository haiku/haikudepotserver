-- See GitHub issue 136

DELETE FROM haikudepot.pkg_version_copyright pvc WHERE pvc.pkg_version_id IN (
  SELECT pv1.id FROM
    haikudepot.pkg_version pv1
    JOIN haikudepot.architecture a1 ON a1.id = pv1.architecture_id
    JOIN haikudepot.repository_source rs1 ON rs1.id = pv1.repository_source_id
  WHERE a1.code = 'x86' OR rs1.code = 'haikuports_x86'
);

DELETE FROM haikudepot.pkg_version_license pvl WHERE pvl.pkg_version_id IN (
  SELECT pv1.id FROM
    haikudepot.pkg_version pv1
    JOIN haikudepot.architecture a1 ON a1.id = pv1.architecture_id
    JOIN haikudepot.repository_source rs1 ON rs1.id = pv1.repository_source_id
  WHERE a1.code = 'x86' OR rs1.code = 'haikuports_x86'
);

DELETE FROM haikudepot.pkg_version_url pvu WHERE pvu.pkg_version_id IN (
  SELECT pv1.id FROM
    haikudepot.pkg_version pv1
    JOIN haikudepot.architecture a1 ON a1.id = pv1.architecture_id
    JOIN haikudepot.repository_source rs1 ON rs1.id = pv1.repository_source_id
  WHERE a1.code = 'x86' OR rs1.code = 'haikuports_x86'
);

-- may be impacting around 6 user ratings

DELETE FROM haikudepot.user_rating ur WHERE ur.pkg_version_id IN (
  SELECT pv1.id FROM
    haikudepot.pkg_version pv1
    JOIN haikudepot.architecture a1 ON a1.id = pv1.architecture_id
    JOIN haikudepot.repository_source rs1 ON rs1.id = pv1.repository_source_id
  WHERE a1.code = 'x86' OR rs1.code = 'haikuports_x86'
);

DELETE FROM haikudepot.pkg_version_localization pvl WHERE pvl.pkg_version_id IN (
  SELECT pv1.id FROM
    haikudepot.pkg_version pv1
    JOIN haikudepot.architecture a1 ON a1.id = pv1.architecture_id
    JOIN haikudepot.repository_source rs1 ON rs1.id = pv1.repository_source_id
  WHERE a1.code = 'x86' OR rs1.code = 'haikuports_x86'
);

DELETE FROM haikudepot.pkg_version pv WHERE pv.architecture_id = (
  SELECT a1.id FROM haikudepot.architecture a1 WHERE a1.code = 'x86'
)
OR pv.repository_source_id = (
    SELECT rs1.id FROM haikudepot.repository_source rs1 WHERE rs1.code = 'haikuports_x86'
);

DELETE FROM haikudepot.architecture a WHERE a.code = 'x86';

DELETE FROM haikudepot.repository_source rs WHERE rs.code = 'haikuports_x86';

