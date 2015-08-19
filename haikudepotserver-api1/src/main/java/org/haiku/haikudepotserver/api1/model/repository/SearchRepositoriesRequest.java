/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

public class SearchRepositoriesRequest extends AbstractSearchRequest {

    public Boolean includeInactive;

}
