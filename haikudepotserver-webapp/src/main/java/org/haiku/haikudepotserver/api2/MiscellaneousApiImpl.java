/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class MiscellaneousApiImpl extends AbstractApiImpl implements MiscellaneousApi {

    private final MiscellaneousApiService miscellaneousApiService;

    public MiscellaneousApiImpl(MiscellaneousApiService miscellaneousApiService) {
        this.miscellaneousApiService = Preconditions.checkNotNull(miscellaneousApiService);
    }

    @Override
    public ResponseEntity<GenerateFeedUrlResponseEnvelope> generateFeedUrl(GenerateFeedUrlRequestEnvelope generateFeedUrlRequestEnvelope) {
        return ResponseEntity.ok(
                new GenerateFeedUrlResponseEnvelope()
                        .result(miscellaneousApiService.generateFeedUrl(generateFeedUrlRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllArchitecturesResponseEnvelope> getAllArchitectures(GetAllArchitecturesRequestEnvelope getAllArchitecturesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllArchitecturesResponseEnvelope()
                        .result(miscellaneousApiService.getAllArchitectures(getAllArchitecturesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllContributorsResponseEnvelope> getAllContributors(Object body) {
        return ResponseEntity.ok(
                new GetAllContributorsResponseEnvelope()
                        .result(miscellaneousApiService.getAllContributors()));
    }

    @Override
    public ResponseEntity<GetAllCountriesResponseEnvelope> getAllCountries(GetAllCountriesRequestEnvelope getAllCountriesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllCountriesResponseEnvelope()
                        .result(miscellaneousApiService.getAllCountries(getAllCountriesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllMessagesResponseEnvelope> getAllMessages(GetAllMessagesRequestEnvelope getAllMessagesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllMessagesResponseEnvelope()
                        .result(miscellaneousApiService.getAllMessages(getAllMessagesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllNaturalLanguagesResponseEnvelope> getAllNaturalLanguages(GetAllNaturalLanguagesRequestEnvelope getAllNaturalLanguagesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllNaturalLanguagesResponseEnvelope()
                        .result(miscellaneousApiService.getAllNaturalLanguages(getAllNaturalLanguagesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllPkgCategoriesResponseEnvelope> getAllPkgCategories(GetAllPkgCategoriesRequestEnvelope getAllPkgCategoriesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllPkgCategoriesResponseEnvelope()
                        .result(miscellaneousApiService.getAllPkgCategories(getAllPkgCategoriesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllProminencesResponseEnvelope> getAllProminences(GetAllProminencesRequestEnvelope getAllProminencesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllProminencesResponseEnvelope()
                        .result(miscellaneousApiService.getAllProminences(getAllProminencesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetAllUserRatingStabilitiesResponseEnvelope> getAllUserRatingStabilities(GetAllUserRatingStabilitiesRequestEnvelope getAllUserRatingStabilitiesRequestEnvelope) {
        return ResponseEntity.ok(
                new GetAllUserRatingStabilitiesResponseEnvelope()
                        .result(miscellaneousApiService.getAllUserRatingStabilities(getAllUserRatingStabilitiesRequestEnvelope)));
    }

    @Override
    public ResponseEntity<GetStorageSummaryResponseEnvelope> getStorageSummary(Object body) {
        return ResponseEntity.ok(
                new GetStorageSummaryResponseEnvelope()
                        .result(miscellaneousApiService.getStorageSummary()));
    }

    @Override
    public ResponseEntity<GetRuntimeInformationResponseEnvelope> getRuntimeInformation(Object body) {
        return ResponseEntity.ok(
                new GetRuntimeInformationResponseEnvelope()
                        .result(miscellaneousApiService.getRuntimeInformation()));
    }

    @Override
    public ResponseEntity<RaiseExceptionResponseEnvelope> raiseException(Object body) {
        miscellaneousApiService.raiseException();
        return ResponseEntity.ok(new RaiseExceptionResponseEnvelope().result(Map.of()));
    }
}
