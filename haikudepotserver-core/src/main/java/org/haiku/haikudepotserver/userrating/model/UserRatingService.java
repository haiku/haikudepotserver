/*
 * Copyright 2016-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.support.StoppableConsumer;

import java.util.List;
import java.util.Map;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

public interface UserRatingService {

    int each(
            ObjectContext context,
            UserRatingSearchSpecification search,
            StoppableConsumer<UserRating> c);

    List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search);

    Map<Short, Long> totalsByRating(ObjectContext context, UserRatingSearchSpecification search);

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
     * repositories for the nominated package.</p>
     */

    void updateUserRatingDerivationsForPkg(String pkgName);

    /**
     * <p>This method will update the user rating aggregates for the nominated user.</p>
     */
    void updateUserRatingDerivationsForUser(String userNickname);

    /**
     * <p>This method will delete the {@link UserRating}.</p>
     */

    void removeUserRatingAtomically(String userRatingCode);

}
