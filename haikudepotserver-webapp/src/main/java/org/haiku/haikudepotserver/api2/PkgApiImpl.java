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
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    private final PkgApiService pkgApiService;

    public PkgApiImpl(PkgApiService pkgApiService) {
        this.pkgApiService = pkgApiService;
    }

    @Override
    public ResponseEntity<IncrementViewCounterResponseEnvelope> incrementViewCounter(@Valid IncrementViewCounterRequestEnvelope request) {
        pkgApiService.incrementViewCounter(request);
        return ResponseEntity.ok(new IncrementViewCounterResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<RemovePkgIconResponseEnvelope> removePkgIcon(RemovePkgIconRequestEnvelope request) {
        pkgApiService.removePkgIcon(request);
        return ResponseEntity.ok(new RemovePkgIconResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<RemovePkgScreenshotResponseEnvelope> removePkgScreenshot(RemovePkgScreenshotRequestEnvelope request) {
        pkgApiService.removePkgScreenshot(request);
        return ResponseEntity.ok(new RemovePkgScreenshotResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<ReorderPkgScreenshotsResponseEnvelope> reorderPkgScreenshots(ReorderPkgScreenshotsRequestEnvelope request) {
        pkgApiService.reorderPkgScreenshots(request);
        return ResponseEntity.ok(new ReorderPkgScreenshotsResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<SearchPkgsResponseEnvelope> searchPkgs(SearchPkgsRequestEnvelope request) {
        return ResponseEntity.ok(
                new SearchPkgsResponseEnvelope()
                        .result(pkgApiService.searchPkgs(request)));
    }

    @Override
    public ResponseEntity<UpdatePkgCategoriesResponseEnvelope> updatePkgCategories(UpdatePkgCategoriesRequestEnvelope request) {
        pkgApiService.updatePkgCategories(request);
        return ResponseEntity.ok(new UpdatePkgCategoriesResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdatePkgChangelogResponseEnvelope> updatePkgChangelog(UpdatePkgChangelogRequestEnvelope request) {
        pkgApiService.updatePkgChangelog(request);
        return ResponseEntity.ok(new UpdatePkgChangelogResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdatePkgLocalizationResponseEnvelope> updatePkgLocalization(UpdatePkgLocalizationRequestEnvelope request) {
        pkgApiService.updatePkgLocalization(request);
        return ResponseEntity.ok(new UpdatePkgLocalizationResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdatePkgProminenceResponseEnvelope> updatePkgProminence(UpdatePkgProminenceRequestEnvelope request) {
        pkgApiService.updatePkgProminence(request);
        return ResponseEntity.ok(new UpdatePkgProminenceResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<UpdatePkgVersionResponseEnvelope> updatePkgVersion(UpdatePkgVersionRequestEnvelope request) {
        pkgApiService.updatePkgVersion(request);
        return ResponseEntity.ok(new UpdatePkgVersionResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<ConfigurePkgIconResponseEnvelope> configurePkgIcon(ConfigurePkgIconRequestEnvelope request) {
        pkgApiService.configurePkgIcon(request);
        return ResponseEntity.ok(new ConfigurePkgIconResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetPkgResponseEnvelope> getPkg(GetPkgRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgResponseEnvelope()
                        .result(pkgApiService.getPkg(request)));
    }

    @Override
    public ResponseEntity<UpdatePkgResponseEnvelope> updatePkg(UpdatePkgRequestEnvelope updatePkgRequestEnvelope) {
        pkgApiService.updatePkg(updatePkgRequestEnvelope);
        return ResponseEntity.ok(new UpdatePkgResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetPkgChangelogResponseEnvelope> getPkgChangelog(GetPkgChangelogRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgChangelogResponseEnvelope()
                        .result(pkgApiService.getPkgChangelog(request)));
    }

    @Override
    public ResponseEntity<GetPkgIconsResponseEnvelope> getPkgIcons(GetPkgIconsRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgIconsResponseEnvelope()
                        .result(pkgApiService.getPkgIcons(request)));
    }

    @Override
    public ResponseEntity<GetPkgLocalizationsResponseEnvelope> getPkgLocalizations(GetPkgLocalizationsRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgLocalizationsResponseEnvelope()
                        .result(pkgApiService.getPkgLocalizations(request)));
    }

    @Override
    public ResponseEntity<GetPkgScreenshotResponseEnvelope> getPkgScreenshot(GetPkgScreenshotRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgScreenshotResponseEnvelope()
                        .result(pkgApiService.getPkgScreenshot(request)));
    }

    @Override
    public ResponseEntity<GetPkgScreenshotsResponseEnvelope> getPkgScreenshots(GetPkgScreenshotsRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgScreenshotsResponseEnvelope()
                        .result(pkgApiService.getPkgScreenshots(request)));
    }

    @Override
    public ResponseEntity<GetPkgVersionLocalizationsResponseEnvelope> getPkgVersionLocalizations(GetPkgVersionLocalizationsRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetPkgVersionLocalizationsResponseEnvelope()
                        .result(pkgApiService.getPkgVersionLocalizations(request)));
    }

}
