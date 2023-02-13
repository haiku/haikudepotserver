/*
 * Copyright 2021-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api2.model.ConfigurePkgIconRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.ConfigurePkgIconResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgChangelogRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgChangelogResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgIconsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgIconsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgLocalizationsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgLocalizationsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgScreenshotRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgScreenshotResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgScreenshotsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgScreenshotsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgVersionLocalizationsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgVersionLocalizationsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.IncrementViewCounterRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.IncrementViewCounterResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RemovePkgIconRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemovePkgIconResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RemovePkgScreenshotRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemovePkgScreenshotResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.ReorderPkgScreenshotsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.ReorderPkgScreenshotsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchPkgsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchPkgsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgCategoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgCategoriesResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgChangelogRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgChangelogResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgLocalizationRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgLocalizationResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgProminenceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgProminenceResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgVersionRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdatePkgVersionResponseEnvelope;
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
