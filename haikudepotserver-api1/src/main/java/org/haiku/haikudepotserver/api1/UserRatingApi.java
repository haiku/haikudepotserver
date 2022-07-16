/*
* Copyright 2014-2021, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.userrating.*;

/**
 * <p>This API interface covers all aspects of user ratings of packages.</p>
 */
@Deprecated
@JsonRpcService("/__api/v1/userrating")
public interface UserRatingApi {

    /**
     * <p>This will find the user rating identified by the user and the package version.  If not such user rating
     * exists then this method will throws an instance of
     * ObjectNotFoundException.  Note that there is no
     * authorization on it; it is effectively public.</p>
     */
    @Deprecated
    GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request);

    /**
     * <p>This method will create a user rating based on the data provided.  In the result is a code that
     * identifies this rating.</p>
     */
    @Deprecated
    CreateUserRatingResult createUserRating(CreateUserRatingRequest request);

    /**
     * <p>This method will update the user rating.  The user rating is identified by the supplied code and the
     * supplied filter describes those properties of the user rating that should be updated.</p>
     */
    @Deprecated
    UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request);

    /**
     * <p>This method will return user rating search results based on the criteria supplied in the request.</p>
     */
    @Deprecated
    SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequest request);

}
