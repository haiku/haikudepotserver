-- Where a package has an HVIF icon present, it is no longer necessary to keep the
-- bitmap images as well.

DELETE FROM haikudepot.pkg_icon_image WHERE pkg_icon_id IN (
    SELECT pi.id FROM
      haikudepot.pkg_icon pi
      JOIN haikudepot.media_type mt ON mt.id = pi.media_type_id
    WHERE
      mt.code = 'image/png'
      AND EXISTS(
          SELECT
            pi2.id
          FROM
            haikudepot.pkg_icon pi2
            JOIN haikudepot.media_type mt2 ON mt2.id = pi2.media_type_id
          WHERE
            mt2.code = 'application/x-vnd.haiku-icon'
            AND pi2.pkg_id = pi.pkg_id
      )
);

DELETE FROM haikudepot.pkg_icon WHERE id IN (
  SELECT pi.id FROM
    haikudepot.pkg_icon pi
    JOIN haikudepot.media_type mt ON mt.id = pi.media_type_id
  WHERE
    mt.code = 'image/png'
    AND EXISTS(
        SELECT
          pi2.id
        FROM
          haikudepot.pkg_icon pi2
          JOIN haikudepot.media_type mt2 ON mt2.id = pi2.media_type_id
        WHERE
          mt2.code = 'application/x-vnd.haiku-icon'
          AND pi2.pkg_id = pi.pkg_id
    )
);