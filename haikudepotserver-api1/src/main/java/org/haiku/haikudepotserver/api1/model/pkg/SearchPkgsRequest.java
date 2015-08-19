/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.List;

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

    public List<String> architectureCodes;

    /**
     * <p>This field specifies the repositories that the search will look for packages in.</p>
     * @since 2015-05-30
     */

    public List<String> repositoryCodes;

    public String pkgCategoryCode;

    public SortOrdering sortOrdering;

    public String naturalLanguageCode;

    /**
     * <p>This will only return data where the latest version is newer than the number of days indicated.</p>
     */

    public Number daysSinceLatestVersion;

}
