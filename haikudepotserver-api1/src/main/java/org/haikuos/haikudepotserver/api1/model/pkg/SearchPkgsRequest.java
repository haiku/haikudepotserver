/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchRequest;

/**
 * <p>This is the model object that is used to define the request to search for packages in the system.</p>
 */

public class SearchPkgsRequest extends AbstractSearchRequest {

    public enum SortOrdering {
        NAME,
        PROMINENCE,
        VERSIONCREATETIMESTAMP,
        VERSIONVIEWCOUNTER
    }

    public String architectureCode;

    public String pkgCategoryCode;

    public SortOrdering sortOrdering;

    public String naturalLanguageCode;

    /**
     * <p>This will only return data where the latest version is newer than the number of days indicated.</p>
     */

    public Number daysSinceLatestVersion;

}
