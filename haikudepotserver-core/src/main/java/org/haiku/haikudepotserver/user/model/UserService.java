/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.User;

import java.util.List;

/**
 * <p>This service undertakes non-trivial operations on users.</p>
 */

public interface UserService {

    List<User> search(
            ObjectContext context,
            UserSearchSpecification searchSpecification);

    /**
     * <p>Find out the total number of results that would be yielded from
     * a search if the search were not constrained.</p>
     */

    long total(
            ObjectContext context,
            UserSearchSpecification searchSpecification);

}
