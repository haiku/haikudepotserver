/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.userrating.job;

public class QueueUserRatingSpreadsheetJobRequest {

    public String userNickname;

    public String pkgName;

    /**
     * @since 2015-06-19
     */

    public String repositoryCode;

}
