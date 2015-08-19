/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haiku.haikudepotserver.api1.model.userrating.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>This API interface covers all aspects of user ratings of packages.</p>
 */

@JsonRpcService("/api/v1/userrating")
public interface UserRatingApi {

    /**
     * <p>This method will re-calculate and store the user rating for the nominated packaging.</p>
     */

    DeriveAndStoreUserRatingForPkgResult deriveAndStoreUserRatingForPkg(DeriveAndStoreUserRatingForPkgRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will trigger the re-calculation of user ratings for all of the packages in the system.</p>
     */

    DeriveAndStoreUserRatingsForAllPkgsResult deriveAndStoreUserRatingsForAllPkgs(DeriveAndStoreUserRatingsForAllPkgsResult request);

    /**
     * <p>This will find the user rating identified by the supplied code and will return data pertaining to that
     * or if the user rating was not able to be found for the code supplied then it will throw an instance of
     * {@link ObjectNotFoundException}.  Note that this invocation
     * has no authorization on it; it is effectively public.</p>
     */

    GetUserRatingResult getUserRating(GetUserRatingRequest request) throws ObjectNotFoundException;

    /**
     * <p>This will find the user rating identified by the user and the package version.  If not such user rating
     * exists then this method will throws an instance of
     * {@link ObjectNotFoundException}.  Note that there is no
     * authorization on it; it is effectively public.</p>
     */

    GetUserRatingByUserAndPkgVersionResult getUserRatingByUserAndPkgVersion(GetUserRatingByUserAndPkgVersionRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will create a user rating based on the data provided.  In the result is a code that
     * identifies this rating.</p>
     */

    CreateUserRatingResult createUserRating(CreateUserRatingRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will update the user rating.  The user rating is identified by the supplied code and the
     * supplied filter describes those properties of the user rating that should be updated.</p>
     */

    UpdateUserRatingResult updateUserRating(UpdateUserRatingRequest request) throws ObjectNotFoundException;

    /**
     * <p>This method will return user rating search results based on the criteria supplied in the request.</p>
     */

    SearchUserRatingsResult searchUserRatings(SearchUserRatingsRequest request) throws ObjectNotFoundException;

    /**
     * <p>Enqueues a request to run a report about user ratings such that they are output as a spreadsheet.  The
     * result contains a GUID that is a handle to the job.  The job is able to be managed by the
     * {@link JobApi}.
     */

    QueueUserRatingSpreadsheetJobResult queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequest request) throws ObjectNotFoundException;

}
