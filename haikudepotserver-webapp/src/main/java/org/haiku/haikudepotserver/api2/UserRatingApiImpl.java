/*
 * Copyright 2021-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import jakarta.validation.Valid;
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

    @Override
    public ResponseEntity<GetSummaryByPkgResponseEnvelope> getSummaryByPkg(GetSummaryByPkgRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetSummaryByPkgResponseEnvelope()
                        .result(userRatingApiService.getSummaryByPkg(request)));
    }
}
