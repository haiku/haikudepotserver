/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

public class SearchUsersRequest extends AbstractSearchRequest {

    public Boolean includeInactive;

}
