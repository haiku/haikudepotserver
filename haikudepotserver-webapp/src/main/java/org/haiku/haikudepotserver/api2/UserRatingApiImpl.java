/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api2.model.CreateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRatingResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.DeriveAndStoreUserRatingForPkgRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.DeriveAndStoreUserRatingForPkgResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.DeriveAndStoreUserRatingsForAllPkgsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingByUserAndPkgVersionResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRatingResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveUserRatingResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUserRatingsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateUserRatingResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;
import java.util.Map;

@Controller
public class UserRatingApiImpl extends AbstractApiImpl implements UserRatingApi {

    private final UserRatingApiService userRatingApiService;

    public UserRatingApiImpl(UserRatingApiService userRatingApiService) {
        this.userRatingApiService = userRatingApiService;
    }

    @Override
    public ResponseEntity<UpdateUserRatingResponseEnvelope> updateUserRating(@Valid UpdateUserRatingRequestEnvelope request) {
        userRatingApiService.updateUserRating(request);
        return ResponseEntity.ok(new UpdateUserRatingResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<CreateUserRatingResponseEnvelope> createUserRating(@Valid CreateUserRatingRequestEnvelope request) {
        return ResponseEntity.ok(
                new CreateUserRatingResponseEnvelope()
                        .result(userRatingApiService.createUserRating(request)));
    }

    @Override
    public ResponseEntity<GetUserRatingByUserAndPkgVersionResponseEnvelope> getUserRatingByUserAndPkgVersion(@Valid GetUserRatingByUserAndPkgVersionRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetUserRatingByUserAndPkgVersionResponseEnvelope()
                        .result(userRatingApiService.getUserRatingByUserAndPkgVersion(request)));
    }

    @Override
    public ResponseEntity<SearchUserRatingsResponseEnvelope> searchUserRatings(@Valid SearchUserRatingsRequestEnvelope request) {
        return ResponseEntity.ok(
                new SearchUserRatingsResponseEnvelope()
                    .result(userRatingApiService.searchUserRatings(request)));
    }

    @Override
    public ResponseEntity<DeriveAndStoreUserRatingForPkgResponseEnvelope> deriveAndStoreUserRatingForPkg(DeriveAndStoreUserRatingForPkgRequestEnvelope deriveAndStoreUserRatingForPkgRequestEnvelope) {
        userRatingApiService.deriveAndStoreUserRatingForPkg(deriveAndStoreUserRatingForPkgRequestEnvelope);
        return ResponseEntity.ok(new DeriveAndStoreUserRatingForPkgResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<DeriveAndStoreUserRatingsForAllPkgsResponseEnvelope> deriveAndStoreUserRatingsForAllPkgs(Object body) {
        userRatingApiService.deriveAndStoreUserRatingsForAllPkgs();
        return ResponseEntity.ok(new DeriveAndStoreUserRatingsForAllPkgsResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetUserRatingResponseEnvelope> getUserRating(GetUserRatingRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetUserRatingResponseEnvelope()
                    .result(userRatingApiService.getUserRating(request)));
    }

    @Override
    public ResponseEntity<RemoveUserRatingResponseEnvelope> removeUserRating(RemoveUserRatingRequestEnvelope removeUserRatingRequestEnvelope) {
        userRatingApiService.removeUserRating(removeUserRatingRequestEnvelope);
        return ResponseEntity.ok(new RemoveUserRatingResponseEnvelope().result(Map.of()));
    }
}
