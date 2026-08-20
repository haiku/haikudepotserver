/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.RepositoryApiService;
import org.haiku.haikudepotserver.api2.UserRatingApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.*;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.multipage.model.Architecture;
import org.haiku.haikudepotserver.multipage.model.PkgCategory;
import org.haiku.haikudepotserver.pkg.controller.PkgScreenshotController;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.data.DataQuantity;
import org.haiku.haikudepotserver.support.data.DataUnitHelper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * <p>Page for showing a version of a package.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/pkg")
public class PkgViewController {

    private final static int USER_RATING_COMMENT_LENGTH_MAX = 140;

    private final static int USER_RATING_LIMIT = 16;

    public final static int SCREENSHOT_THUMBNAIL_SIDE_LIMIT = 640;

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final PkgApiService pkgApiService;
    private final UserRatingApiService userRatingApiService;
    private final RepositoryApiService repositoryApiService;
    private final ReferenceDataRepository referenceDataRepository;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;

    public final static String KEY_REPOSITORYSOURCECODE = "reposrc";
    public final static String KEY_ARCHITECTURECODE = "arch";
    public final static String KEY_SCREENSHOTINDEX = "sshidx";
    public final static String KEY_USERRATINGOFFSET = "ratoff";

    public PkgViewController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            PkgApiService pkgApiService,
            UserRatingApiService userRatingApiService,
            RepositoryApiService repositoryApiService,
            ReferenceDataRepository referenceDataRepository,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.pkgApiService = Preconditions.checkNotNull(pkgApiService);
        this.userRatingApiService = Preconditions.checkNotNull(userRatingApiService);
        this.repositoryApiService = Preconditions.checkNotNull(repositoryApiService);
        this.referenceDataRepository = Preconditions.checkNotNull(referenceDataRepository);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
    }

    @RequestMapping(value = "{pkgName}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView viewPkg(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String pkgName,
            @RequestParam(value = KEY_REPOSITORYSOURCECODE, required = false) String repositorySourceCode,
            @RequestParam(value = KEY_ARCHITECTURECODE, required = false) String architectureCode,
            @RequestParam(value = KEY_SCREENSHOTINDEX, required = false, defaultValue = "0") Integer screenshotIndex,
            @RequestParam(value = KEY_USERRATINGOFFSET, required = false, defaultValue = "0") Integer userRatingOffset,
            @RequestParam(value = MultipageConstants.KEY_VERSION_MAJOR, required = false) String major,
            @RequestParam(value = MultipageConstants.KEY_VERSION_MINOR, required = false) String minor,
            @RequestParam(value = MultipageConstants.KEY_VERSION_MICRO, required = false) String micro,
            @RequestParam(value = MultipageConstants.KEY_VERSION_PRERELEASE, required = false) String preRelease,
            @RequestParam(value = MultipageConstants.KEY_VERSION_REVISION, required = false) Integer revision) throws MultipageObjectNotFoundException {

        VersionCoordinates versionCoordinates = null;

        if (StringUtils.isNotBlank(major)) {
            versionCoordinates = new VersionCoordinates(
                    StringUtils.trimToNull(major),
                    StringUtils.trimToNull(minor),
                    StringUtils.trimToNull(micro),
                    StringUtils.trimToNull(preRelease),
                    revision);
        }

        return new ModelAndView(
                NavigationDestination.PKG_VIEW.template(),
                Map.of(
                        MultipageConstants.KEY_DATA, createData(
                                httpServletRequest,
                                locale,
                                pkgName,
                                repositorySourceCode,
                                architectureCode,
                                versionCoordinates,
                                screenshotIndex,
                                userRatingOffset
                        ),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private PkgViewData createData(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String pkgName,
            String repositorySourceCode,
            String architectureCode,
            @Nullable VersionCoordinates versionCoordinates,
            @Nullable Integer screenshotIndex,
            @Nullable Integer userRatingOffset) {
        ReferenceData referenceData = referenceDataRepository.getReferenceData(locale.toLanguageTag());

        if (StringUtils.isEmpty(repositorySourceCode)) {
            repositorySourceCode = referenceDataRepository.getDefaults().defaultRepositorySourceCode();
        }

        GetPkgRequestEnvelope getPkgRequest = new GetPkgRequestEnvelope();
        getPkgRequest.setName(StringUtils.trimToNull(pkgName));
        getPkgRequest.setNaturalLanguageCode(locale.toLanguageTag());
        getPkgRequest.setRepositorySourceCode(repositorySourceCode);

        if (StringUtils.isNotBlank(architectureCode)) {
            getPkgRequest.setArchitectureCode(architectureCode);
        }

        if (null != versionCoordinates) {
            getPkgRequest.setMajor(versionCoordinates.getMajor());
            getPkgRequest.setMinor(versionCoordinates.getMinor());
            getPkgRequest.setMicro(versionCoordinates.getMicro());
            getPkgRequest.setPreRelease(versionCoordinates.getPreRelease());
            getPkgRequest.setRevision(versionCoordinates.getRevision());
            getPkgRequest.setVersionType(PkgVersionType.SPECIFIC);
        } else {
            getPkgRequest.setVersionType(PkgVersionType.LATEST);
        }

        GetPkgResult getPkgResponse = pkgApiService.getPkg(getPkgRequest);
        GetPkgPkgVersion pkgVersion0 = getPkgResponse.getVersions().getFirst();
        List<Screenshot> screenshots = getScreenshots(getPkgResponse.getName());
        VersionCoordinates viewedVersionCoordinates = new VersionCoordinates(
                pkgVersion0.getMajor(),
                pkgVersion0.getMinor(),
                pkgVersion0.getMicro(),
                pkgVersion0.getPreRelease(),
                pkgVersion0.getRevision()
        );

        return new PkgViewData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                navigationService.deriveMenuGroups(httpServletRequest),
                getPkgResponse.getName(),
                new LocalizedText(
                    pkgVersion0.getTitle(),
                    pkgVersion0.getSummary(),
                    pkgVersion0.getDescription()
                ),
                pkgVersion0.getCopyrights(),
                pkgVersion0.getLicenses(),
                pkgVersion0.getHpkgDownloadURL(),
                CollectionUtils.emptyIfNull(pkgVersion0.getUrls())
                        .stream()
                        .sorted(Comparator.comparing(GetPkgPkgVersionUrl::getUrlTypeCode).thenComparing(GetPkgPkgVersionUrl::getUrl))
                        .map(u -> new Url(
                                u.getUrl(),
                                "mp.pkg_view.url.%s.description".formatted(u.getUrlTypeCode())
                        ))
                        .toList(),
                getPkgResponse.getHasChangelog()
                    ? navigationService.pkgChangelogUri(httpServletRequest, pkgName).build()
                        : null,
                viewedVersionCoordinates,
                Instant.ofEpochMilli(pkgVersion0.getCreateTimestamp()),
                referenceData.tryArchitectureForCode(pkgVersion0.getArchitectureCode()).orElse(null),
                getRepositorySource(referenceData, pkgVersion0.getRepositorySourceCode()),
                referenceData.pkgCategoriesForCodes(getPkgResponse.getPkgCategoryCodes()),
                getUserRatingInfo(
                        referenceData,
                        getPkgResponse,
                        viewedVersionCoordinates,
                        Optional.ofNullable(userRatingOffset).orElse(0)),
                new ScreenshotInfo(
                        screenshots.isEmpty() ? null :
                                new Pagination(
                                        Math.clamp(Optional.ofNullable(screenshotIndex).orElse(0), 0, screenshots.size() - 1),
                                        screenshots.size(),
                                        1),
                        screenshots
                ),
                getPkgResponse.getIsNativeDesktop(),
                BooleanUtils.isTrue(pkgVersion0.getHasSource()),
                Optional.ofNullable(pkgVersion0.getPayloadLength())
                        .map(DataUnitHelper::scaleBytesToSensibleUnit)
                        .orElse(null)
        );
    }

    /**
     * @param userRatingOffset is the index into the set of user ratings to show.
     */

    private UserRatingInfo getUserRatingInfo(
            ReferenceData referenceData,
            GetPkgResult getPkgResponse,
            VersionCoordinates viewedVersionCoordinates,
            int userRatingOffset) {

        SearchUserRatingsRequestEnvelope request = new SearchUserRatingsRequestEnvelope();
        request.setPkgName(getPkgResponse.getName());
        request.setRepositorySourceCode(getPkgResponse.getVersions().getFirst().getRepositorySourceCode());
        request.setOffset(userRatingOffset);
        request.setLimit(USER_RATING_LIMIT);

        SearchUserRatingsResult result = userRatingApiService.searchUserRatings(request);

        return new UserRatingInfo(
                new Pagination(
                        userRatingOffset,
                        result.getTotal().intValue(),
                        USER_RATING_LIMIT
                ),
                result.getItems().stream()
                        .map(ur -> mapToModelUserRating(referenceData, viewedVersionCoordinates, ur))
                        .toList(),
                new UserRatingSummary(
                        getPkgResponse.getDerivedRating(),
                        Optional.ofNullable(getPkgResponse.getDerivedRatingSampleSize()).orElse(0)
                )
        );

    }

    private UserRating mapToModelUserRating(
            ReferenceData referenceData,
            VersionCoordinates viewedVersionCoordinates,
            SearchUserRatingsResultItemsInner userRating) {

        SearchUserRatingsPkgVersion pkgVersion = userRating.getPkgVersion();
        VersionCoordinates userRatingVersionCoordinates = new VersionCoordinates(
                pkgVersion.getMajor(),
                pkgVersion.getMinor(),
                pkgVersion.getMicro(),
                pkgVersion.getPreRelease(),
                pkgVersion.getRevision()
        );

        return new UserRating(
                userRating.getCode(),
                StringUtils.abbreviate(userRating.getComment(), USER_RATING_COMMENT_LENGTH_MAX),
                Instant.ofEpochMilli(userRating.getCreateTimestamp()),
                userRating.getRating(),
                userRating.getUser().getNickname(),
                new VersionCoordinates(
                        pkgVersion.getMajor(),
                        pkgVersion.getMinor(),
                        pkgVersion.getMicro(),
                        pkgVersion.getPreRelease(),
                        pkgVersion.getRevision()
                ),
                !viewedVersionCoordinates.equals(userRatingVersionCoordinates),
                referenceData.tryArchitectureForCode(pkgVersion.getArchitectureCode()).orElse(null)
        );
    }

    private List<Screenshot> getScreenshots(String pkgName) {
        GetPkgScreenshotsRequestEnvelope request = new GetPkgScreenshotsRequestEnvelope();
        request.setPkgName(pkgName);
        GetPkgScreenshotsResult result = pkgApiService.getPkgScreenshots(request);
        return result.getItems().stream()
                .map(this::mapToModelScreenshot)
                .toList();
    }

    /**
     * <p>Maps from the API model to the web page rendering model.</p>
     */
    private Screenshot mapToModelScreenshot(GetPkgScreenshotsScreenshot dto) {
        UriComponents baseViewUri = UriComponentsBuilder.newInstance()
                .pathSegment(
                        PkgScreenshotController.SEGMENT_SCREENSHOT,
                        "%s.png".formatted(dto.getCode()))
                .build();

        UriComponents thumbnailUri = UriComponentsBuilder.fromUri(baseViewUri.toUri())
                .queryParam(PkgScreenshotController.KEY_TARGETWIDTH, SCREENSHOT_THUMBNAIL_SIDE_LIMIT)
                .queryParam(PkgScreenshotController.KEY_TARGETHEIGHT, SCREENSHOT_THUMBNAIL_SIDE_LIMIT)
                .build();

        UriComponents viewUri = UriComponentsBuilder.fromUri(baseViewUri.toUri())
                .queryParam(PkgScreenshotController.KEY_TARGETWIDTH, PkgScreenshotController.SCREENSHOT_SIDE_LIMIT)
                .queryParam(PkgScreenshotController.KEY_TARGETHEIGHT, PkgScreenshotController.SCREENSHOT_SIDE_LIMIT)
                .build();

        return new Screenshot(
                dto.getCode(),
                dto.getLength(),
                dto.getWidth(),
                dto.getHeight(),
                thumbnailUri,
                viewUri
        );
    }

    private RepositorySource getRepositorySource(ReferenceData referenceData, String repositorySourceCode) {
        GetRepositorySourceRequestEnvelope request = new GetRepositorySourceRequestEnvelope();
        request.setCode(repositorySourceCode);
        GetRepositorySourceResult result = repositoryApiService.getRepositorySource(request);
        return new RepositorySource(
                referenceData.architectureForCode(result.getArchitectureCode()),
                referenceData.repositoryForCode(result.getRepositoryCode()));
    }

    /**
     * @param architecture if the architecture is real; some packages don't have an architecture if they are a package
     *                     that is not dependent on a specific target architecture.
     */
    public record UserRating(
            String code,
            String comment,
            Instant createTimestamp,
            Integer rating,
            String userNickname,
            VersionCoordinates pkgVersion,
            boolean pkgVersionDiffersFromViewed,
            @Nullable Architecture architecture
    ) {
    }

    public record UserRatingSummary(
            BigDecimal derivedRating,
            Integer derivedRatingSampleSize
    ) {
    }

    public record UserRatingInfo(
            Pagination pagination,
            List<UserRating> userRatings,
            UserRatingSummary summary
    ) {
    }

    public record Screenshot(
            String code,
            Integer length,
            Integer width,
            Integer height,
            UriComponents thumbnailUriComponents,
            UriComponents viewUriComponents
    ) {
    }

    public record ScreenshotInfo(
            Pagination screenshotsPagination,
            List<Screenshot> screenshots
    ) {

        public boolean hasMoreThanOne() {
            return screenshots().size() > 1;
        }

        public Screenshot currentScreenshot() {
            return screenshots().get(screenshotsPagination.offset());
        }

    }

    public record RepositorySource(
            Architecture architecture,
            Repository repository
    ) {
    }

    public record Url(
            String url,
            String descriptionKey
    ) {
    }

    public record LocalizedText(
            String title,
            String summary,
            String description
    ) {
    }

    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public record PkgViewData(

            UriComponents uriComponents,
            List<MenuGroup> menuGroups,

            String pkgName,

            LocalizedText localizedText,

            List<String> copyrights,
            List<String> licenses,
            String hpkrDownloadURL,
            List<Url> urls,
            @Nullable UriComponents viewChangelogUriComponents,

            VersionCoordinates version,
            Instant versionCreateTimestamp,

            Architecture architecture,
            RepositorySource repositorySource,
            List<PkgCategory> pkgCategories,
            UserRatingInfo userRatingInfo,
            ScreenshotInfo screenshotInfo,

            boolean isNativeDesktop,
            boolean isSourceAvailable,

            @Nullable DataQuantity payloadLength

    ) {
    }

}
