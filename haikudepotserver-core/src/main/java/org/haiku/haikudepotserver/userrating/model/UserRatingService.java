/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.support.StoppableConsumer;

import java.util.List;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

public interface UserRatingService {

    int each(
            ObjectContext context,
            UserRatingSearchSpecification search,
            StoppableConsumer<UserRating> c);

    List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search);

    long total(ObjectContext context, UserRatingSearchSpecification search);

    /**
     * <p>If a user has their active / inactive state swapped, it is possible that it may have some bearing
     * on user ratings because user rating values from inactive users are not included.  The impact may be
     * small, but may also be significant in cases where the package has few ratings anyway.  This method
     * will return a list of package names for those packages that may be accordingly effected by a change
     * in a user's active flag.</p>
     */

    List<String> pkgNamesEffectedByUserActiveStateChange(ObjectContext context, User user);

    /**
     * <p>This method will go through all of the relevant packages and will derive their user ratings.</p>
     */

    void updateUserRatingDerivationsForAllPkgs();

    /**
     * <p>This method will update the user rating aggregate across all appropriate
     * repositories.</p>
     */

    void updateUserRatingDerivation(String pkgName);

}
