/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.security.AbstractUserAuthenticationAware;
import org.haiku.haikudepotserver.support.DateTimeHelper;

import javax.servlet.http.HttpServletResponse;
import java.time.Instant;

public class AbstractController extends AbstractUserAuthenticationAware {

    /**
     * <P>When producing a CSV download, this method will add the necessary headers to ensure that
     * the response is not cached and will also set a filename into the response.</P>
     * @param filenameIdentifier is used to produce a filename for the download.
     */

    protected void setHeadersForCsvDownload(HttpServletResponse response, String filenameIdentifier) {
        String filename = String.format(
                "hds_%s_%s.csv",
                filenameIdentifier,
                DateTimeHelper.create14DigitDateTimeFormat().format(Instant.now()));

        response.setContentType(MediaType.CSV_UTF_8.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+filename);
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

    }

}
