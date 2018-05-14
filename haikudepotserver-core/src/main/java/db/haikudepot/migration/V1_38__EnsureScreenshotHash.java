/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package db.haikudepot.migration;

import db.haikudepot.support.EnsureScreenshotHash;

public class V1_38__EnsureScreenshotHash extends EnsureScreenshotHash {

    private static final String STATEMENT_COUNT = "SELECT COUNT(*) FROM haikudepot.pkg_screenshot";

    private static final String STATEMENT_FETCH = "SELECT ps.id AS pkg_screenshot_id, "
            + "psi.data AS pkg_screenshot_image_data FROM haikudepot.pkg_screenshot ps "
            + "JOIN haikudepot.pkg_screenshot_image psi ON psi.pkg_screenshot_id = ps.id";

    public String createScreenshotCountStatement() {
        return STATEMENT_COUNT;
    }

    public String createScreenshotIdStatement() {
        return STATEMENT_FETCH;
    }

}
