/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.List;

/**
 * <p>A field <code>repositorySourceSearchUrls</code> was removed 2020-06-15.</p>
 */

public class SearchRepositoriesRequest extends AbstractSearchRequest {

    public Boolean includeInactive;

}
