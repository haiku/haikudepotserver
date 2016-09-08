/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.List;

public class SearchRepositoriesRequest extends AbstractSearchRequest {

    /**
     * <p>If supplied, only those repositories that have a source with the appropriate base URL will be returned.</p>
     * @since 2016-08-22
     */

    public List<String> repositorySourceSearchUrls;

    public Boolean includeInactive;

}
