/*
 * Copyright 2022-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.Prominence;
import org.haiku.haikudepotserver.dataobjects.auto._PkgUserRatingAggregate;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.pkg.FixedPkgLocalizationLookupServiceImpl;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.StringHelper;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.haiku.haikudepotserver.support.exception.BadPkgIconException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component("pkgApiServiceV2")
public class PkgApiService extends AbstractApiService {

    private final static Logger LOGGER = LoggerFactory.getLogger(PkgApiService.class);

    private final static int PKGPKGCATEGORIES_MAX = 3;

    private final static int SNIPPET_LENGTH = 64;

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final PkgIconService pkgIconService;
    private final PkgScreenshotService pkgScreenshotService;
    private final PkgService pkgService;
    private final PkgLocalizationService pkgLocalizationService;

    public PkgApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            PkgIconService pkgIconService,
            PkgScreenshotService pkgScreenshotService,
            PkgService pkgService,
            PkgLocalizationService pkgLocalizationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
        this.pkgScreenshotService = Preconditions.checkNotNull(pkgScreenshotService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
    }

    public void configurePkgIcon(ConfigurePkgIconRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));

        validateIcons(request);

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITICON)) {
            throw new AccessDeniedException("attempt to configure the icon for package ["
                    + pkg + "], but the user is not able to");
        }

        // insert or override the icons

        Set<PkgIcon> createdOrUpdatedPkgIcons = CollectionUtils.emptyIfNull(request.getPkgIcons())
                .stream()
                .map(pi -> storePkgIconImage(context, pkg, pi))
                .collect(Collectors.toSet());

        // now we have some icons stored which may not be in the replacement data; we should remove those ones.

        Collection<Object> unwantedIconObjects = List.copyOf(pkg.getPkgSupplement().getPkgIcons()).stream()
                .filter(pi -> !createdOrUpdatedPkgIcons.contains(pi))
                .flatMap(pi -> Stream.of(pi.getPkgIconImage(), pi))
                .collect(Collectors.toList());

        context.deleteObjects(unwantedIconObjects);

        // now save and finish up.

        pkg.setModifyTimestamp();
        context.commitChanges();
        LOGGER.info("did configure icons for pkg {} (updated {}, removed {})",
                pkg.getName(),
                createdOrUpdatedPkgIcons.size(),
                unwantedIconObjects.size());
    }

    private PkgIcon storePkgIconImage(ObjectContext context, Pkg pkg, ConfigurePkgIconPkgIcon icon) {
        byte[] data = Base64.getDecoder().decode(icon.getDataBase64());
        try (ByteArrayInputStream dataInputStream = new ByteArrayInputStream(data)) {
            return pkgIconService.storePkgIconImage(
                    dataInputStream,
                    MediaType.getByCode(context, icon.getMediaTypeCode()),
                    icon.getSize(),
                    context,
                    new UserPkgSupplementModificationAgent(tryObtainAuthenticatedUser(context).orElse(null)),
                    pkg.getPkgSupplement());
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        catch (org.haiku.haikudepotserver.pkg.model.BadPkgIconException bpie) {
            throw new BadPkgIconException(icon.getMediaTypeCode(), icon.getSize(), bpie);
        }
    }

    private static void validateIcon(ConfigurePkgIconPkgIcon icon) {
        Preconditions.checkArgument(null != icon);

        if (Strings.isNullOrEmpty(icon.getDataBase64())) {
            throw new IllegalStateException("the base64 data must be supplied with the request to configure a pkg icon");
        }

        if (Strings.isNullOrEmpty(icon.getMediaTypeCode())) {
            throw new IllegalStateException("the mediaTypeCode must be supplied to configure a pkg icon");
        }
    }

    private static void validateIcons(ConfigurePkgIconRequestEnvelope request) {
        Collection<ConfigurePkgIconPkgIcon> pkgIcons = CollectionUtils.emptyIfNull(request.getPkgIcons());

        pkgIcons.forEach(PkgApiService::validateIcon);

        boolean hasHvif = pkgIcons.stream().anyMatch(pi -> pi.getMediaTypeCode().equals(MediaType.MEDIATYPE_HAIKUVECTORICONFILE));

        if (hasHvif && pkgIcons.size() > 1) {
            throw new IllegalStateException("if an hvif icon is supplied then there should be no other variants.");
        }

        if (!hasHvif && !pkgIcons.isEmpty()) {
            if (Stream.of(16, 32, 64)
                    .anyMatch(size -> pkgIcons
                            .stream()
                            .filter(pi -> pi.getMediaTypeCode().equals(MediaType.MEDIATYPE_PNG))
                            .filter(pi -> pi.getSize().equals(size))
                            .findAny()
                            .isEmpty())
            ) {
                throw new IllegalStateException("there should be three bitmap icons supplied in sizes 16, 32 and 64");
            }
        }
    }

    public GetPkgResult getPkg(GetPkgRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(StringUtils.isNotBlank(request.getName()), "request pkg name is required");
        Preconditions.checkNotNull(request.getVersionType());
        Preconditions.checkState(StringUtils.isNotBlank(request.getNaturalLanguageCode()));
        Preconditions.checkArgument(
                EnumSet.of(PkgVersionType.NONE, PkgVersionType.ALL).contains(request.getVersionType())
                        || StringUtils.isNotBlank(request.getRepositorySourceCode()),
                "the repository source code should be supplied of the version request is not ALL or NONE");

        final ObjectContext context = serverRuntime.newContext();

        Architecture architecture = Optional.ofNullable(request.getArchitectureCode())
                .map(StringUtils::trimToNull)
                .map(ac -> getArchitecture(context, ac))
                .orElse(null);
        Pkg pkg = getPkg(context, request.getName());
        RepositorySource repositorySource = Optional.ofNullable(request.getRepositorySourceCode())
                .map(StringUtils::trimToNull)
                .map(rsc -> RepositorySource.getByCode(context, rsc))
                .orElse(null);
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.getNaturalLanguageCode());
        List<PkgVersion> pkgVersions = derivePkgVersions(context, request, pkg, architecture, repositorySource);

        GetPkgResult result = new GetPkgResult()
                .name(pkg.getName())
                .isDesktop(pkg.getIsDesktop())
                .isNativeDesktop(pkg.getIsNativeDesktop())
                .modifyTimestamp(pkg.getModifyTimestamp().getTime())
                .vanityLinkUrl(pkgService.createVanityLinkUrl(pkg))
                .hasChangelog(pkg.getPkgSupplement().getPkgChangelog().isPresent())
                .pkgCategoryCodes(pkg.getPkgSupplement().getPkgPkgCategories()
                        .stream()
                        .map(ppc -> ppc.getPkgCategory().getCode())
                        .toList())
                .versions(pkgVersions
                        .stream()
                        .map(pv -> mapToResultPkgVersion(context, pv, naturalLanguage))
                        .toList());

        if (null != repositorySource) {
            Optional<PkgUserRatingAggregate> userRatingAggregate = pkg.getPkgUserRatingAggregate(repositorySource.getRepository());

            if (userRatingAggregate.isPresent()) {
                result = result
                        .derivedRating(BigDecimal.valueOf(userRatingAggregate.get().getDerivedRating()))
                        .derivedRatingSampleSize(userRatingAggregate.get().getDerivedRatingSampleSize());
            }

            result = result.prominenceOrdering(
                    pkg.tryGetPkgProminence(repositorySource.getRepository())
                            .map(pp -> pp.getProminence().getOrdering())
                            .orElse(null));
        }

        if (BooleanUtils.isTrue(request.getIncrementViewCounter()) && 1 == pkgVersions.size()) {
            incrementCounter(IterableUtils.first(pkgVersions));
        }

        return result;
    }

    public void updatePkg(UpdatePkgRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request must be provided");
        Preconditions.checkState(StringUtils.isNotBlank(request.getName()), "request pkg name is required");

        final ObjectContext context = serverRuntime.newContext();

        Pkg pkg = Pkg.getByName(context, request.getName());

        for (UpdatePkgFilter filter : request.getFilter()) {
            switch (filter) {
                case IS_NATIVE_DESKTOP:
                    if (!permissionEvaluator.hasPermission(
                            SecurityContextHolder.getContext().getAuthentication(),
                            pkg,
                            Permission.PKG_EDITNATIVEDESKTOP)) {
                        throw new AccessDeniedException("unable to update the package is native desktop");
                    }
                    pkg.setIsNativeDesktop(BooleanUtils.isTrue(request.getIsNativeDesktop()));
                    break;
                default:
                    throw new IllegalStateException("unhandled filter [" + filter.name() + "]");
            }
        }

        if (context.hasChanges()) {
            context.commitChanges();
            LOGGER.info("did update package [{}]", pkg.getName());
        }
    }

    private GetPkgPkgVersion mapToResultPkgVersion(ObjectContext context, PkgVersion pkgVersion, NaturalLanguage naturalLanguage) {
        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);

        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                pkgLocalizationService.resolvePkgVersionLocalization(context, pkgVersion, null, naturalLanguage);

        return new GetPkgPkgVersion()
                .active(pkgVersion.getActive())
                .isLatest(pkgVersion.getIsLatest())
                .major(pkgVersion.getMajor())
                .minor(pkgVersion.getMinor())
                .micro(pkgVersion.getMicro())
                .revision(pkgVersion.getRevision())
                .preRelease(pkgVersion.getPreRelease())
                .hasSource(pkgService.getCorrespondingSourcePkgVersion(context, pkgVersion).isPresent())
                .createTimestamp(pkgVersion.getCreateTimestamp().getTime())
                .payloadLength(pkgVersion.getPayloadLength())
                .repositorySourceCode(pkgVersion.getRepositorySource().getCode())
                .repositoryCode(pkgVersion.getRepositorySource().getRepository().getCode())
                .architectureCode(pkgVersion.getArchitecture().getCode())
                .copyrights(pkgVersion.getPkgVersionCopyrights().stream().map(PkgVersionCopyright::getBody).toList())
                .licenses(pkgVersion.getPkgVersionLicenses().stream().map(PkgVersionLicense::getBody).collect(Collectors.toList()))
                .viewCounter(pkgVersion.getViewCounter())
                .hpkgDownloadURL(pkgService.createHpkgDownloadUrl(pkgVersion))
                .title(resolvedPkgVersionLocalization.getTitle())
                .description(resolvedPkgVersionLocalization.getDescription())
                .summary(resolvedPkgVersionLocalization.getSummary())
                .urls(pkgVersion.getPkgVersionUrls()
                        .stream()
                        .map(u -> new GetPkgPkgVersionUrl()
                                .urlTypeCode(u.getPkgUrlType().getCode())
                                .url(u.getUrl()))
                        .collect(Collectors.toList()));
    }



    private List<PkgVersion> derivePkgVersions(
            ObjectContext context,
            GetPkgRequestEnvelope request,
            Pkg pkg,
            Architecture architecture,
            RepositorySource repositorySource) {

        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(request);

        switch (request.getVersionType()) {

            // might be used to show a history of the versions.  If an architecture is present then it will
            // only return versions for that architecture.  If no architecture is present then it will return
            // versions for all architectures.

            case ALL: {
                final VersionCoordinatesComparator vcc = new VersionCoordinatesComparator();
                return Optional.ofNullable(repositorySource)
                        .map(rs -> PkgVersion.findForPkg(context, pkg, rs, false))
                        // ^ active only
                        .orElseGet(() -> PkgVersion.findForPkg(context, pkg, false))
                        // ^ active only
                        .stream()
                        .filter(pv -> null == architecture || pv.getArchitecture().equals(architecture))
                        .sorted((pv1, pv2) ->
                                ComparisonChain.start()
                                        .compare(pv1.getArchitecture().getCode(), pv2.getArchitecture().getCode())
                                        .compare(pv1.toVersionCoordinates(), pv2.toVersionCoordinates(), vcc)
                                        .result())
                        .toList();
            }

            case SPECIFIC: {
                if (null == architecture) {
                    throw new IllegalStateException("the architecture was not specified");
                }

                VersionCoordinates coordinates = new VersionCoordinates(
                        request.getMajor(), request.getMinor(), request.getMicro(),
                        request.getPreRelease(), request.getRevision());

                PkgVersion pkgVersion = PkgVersion.tryGetForPkg(context, pkg, repositorySource, architecture, coordinates)
                        .filter(_PkgVersion::getActive)
                        .orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), ""));

                return List.of(pkgVersion);
            }

            case LATEST:
                return List.of(pkgService.getLatestPkgVersionForPkg(context, pkg, repositorySource)
                        .orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), request.getName())));

            case NONE: // no version is actually required.
                return List.of();

            default:
                throw new IllegalStateException("unhandled version type in request");
        }
    }

    public GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "a package name must be supplied");

        ObjectContext context = serverRuntime.newContext();
        return new GetPkgChangelogResult()
                .content(getPkg(context, request.getPkgName())
                        .getPkgSupplement()
                        .getPkgChangelog()
                        .map(PkgChangelog::getContent)
                        .orElse(null));
    }

    public GetPkgIconsResult getPkgIcons(GetPkgIconsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()), "a package name must be supplied to get the package's icons");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        return new GetPkgIconsResult()
                .pkgIcons(pkg.getPkgSupplement().getPkgIcons()
                        .stream()
                        .map(pi -> new GetPkgIconsPkgIcon()
                                .size(pi.getSize())
                                .mediaTypeCode(pi.getMediaType().getCode()))
                        .toList());
    }

    public GetPkgLocalizationsResult getPkgLocalizations(GetPkgLocalizationsRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "a request is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "a package name is required");
        Preconditions.checkArgument(null != request.getNaturalLanguageCodes(), "the natural language codes must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());
        List<PkgLocalization> pkgLocalizations = PkgLocalization.findForPkg(context, pkg);

        return new GetPkgLocalizationsResult()
                .pkgLocalizations(pkgLocalizations.stream()
                        .filter(pl -> request.getNaturalLanguageCodes().contains(pl.getNaturalLanguage().getCode()))
                        .map(pl -> new GetPkgLocalizationsPkgLocalization()
                                .naturalLanguageCode(pl.getNaturalLanguage().getCode())
                                .title(pl.getTitle())
                                .summary(pl.getSummary())
                                .description(pl.getDescription()))
                        .collect(Collectors.toList())
                );
    }

    public GetPkgScreenshotResult getPkgScreenshot(GetPkgScreenshotRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.getCode());

        final ObjectContext context = serverRuntime.newContext();
        PkgScreenshot pkgScreenshot = PkgScreenshot.getByCode(context, request.getCode());

        return new GetPkgScreenshotResult()
                .code(pkgScreenshot.getCode())
                .height(pkgScreenshot.getHeight())
                .width(pkgScreenshot.getWidth())
                .length(pkgScreenshot.getLength());
    }

    public GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));

        final ObjectContext context = serverRuntime.newContext();
        final Pkg pkg = getPkg(context, request.getPkgName());

        return new GetPkgScreenshotsResult()
                .items(pkg.getPkgSupplement().getSortedPkgScreenshots()
                        .stream()
                        .map(ps -> new GetPkgScreenshotsScreenshot()
                                .code(ps.getCode())
                                .height(ps.getHeight())
                                .width(ps.getWidth())
                                .length(ps.getLength()))
                        .collect(Collectors.toList())
                );
    }

    public GetPkgVersionLocalizationsResult getPkgVersionLocalizations(GetPkgVersionLocalizationsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getArchitectureCode()));
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));
        Preconditions.checkNotNull(request.getNaturalLanguageCodes());
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositorySourceCode()), "the repository code must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());
        Architecture architecture = getArchitecture(context, request.getArchitectureCode());
        RepositorySource repositorySource = getRepositorySource(context, request.getRepositorySourceCode());
        PkgVersion pkgVersion = tryGetPkgVersion(context, request, pkg, repositorySource, architecture)
                .filter(_PkgVersion::getActive)
                .orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode()));

        return new GetPkgVersionLocalizationsResult()
                .pkgVersionLocalizations(request.getNaturalLanguageCodes().stream()
                        .map(code -> pkgVersion.getPkgVersionLocalization(NaturalLanguageCoordinates.fromCode(code)))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(pvl -> new GetPkgVersionLocalizationsPkgVersionLocalization()
                                .naturalLanguageCode(pvl.getNaturalLanguage().getCode())
                                .summary(pvl.getSummary().orElse(null))
                                .description(pvl.getDescription().orElse(null)))
                        .collect(Collectors.toList()));
    }

    private Optional<PkgVersion> tryGetPkgVersion(
            ObjectContext context,
            GetPkgVersionLocalizationsRequestEnvelope request,
            Pkg pkg,
            RepositorySource repositorySource,
            Architecture architecture) {
        if (null == request.getMajor()) {
            return pkgService.getLatestPkgVersionForPkg(
                    context, pkg, repositorySource,
                    Collections.singletonList(architecture));
        }
        return PkgVersion.tryGetForPkg(
                    context, pkg, repositorySource, architecture,
                    new VersionCoordinates(
                            request.getMajor(),
                            request.getMinor(),
                            request.getMicro(),
                            request.getPreRelease(),
                            request.getRevision()));
    }

    public void incrementViewCounter(IncrementViewCounterRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getName()), "the package name must be supplied");
        // temporary support the `repositoryCode` for the desktop client.
        Preconditions.checkArgument(
                Stream.of(request.getArchitectureCode(), request.getRepositoryCode())
                        .map(StringUtils::trimToNull)
                        .filter(Objects::nonNull)
                        .count() == 2 || StringUtils.isNotBlank(request.getRepositorySourceCode()),
                "the (repository code and architecture) are required or the repository source code");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositoryCode()), "the repository code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getMajor()), "the version major must be supplied");

        ObjectContext context = serverRuntime.newContext();

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.getMajor(),
                request.getMinor(),
                request.getMicro(),
                request.getPreRelease(),
                request.getRevision()
        );

        // because there are some older HD desktop clients that will still come
        // through with architecture + repository, we can work with this to
        // probably get the repository source.

        Architecture architecture = getArchitecture(context, request.getArchitectureCode());
        RepositorySource repositorySource = null;

        if (StringUtils.isNotBlank(request.getRepositorySourceCode())) {
            repositorySource = getRepositorySource(context, request.getRepositorySourceCode());
        }
        else {
            if (!Set.of(Architecture.CODE_SOURCE, Architecture.CODE_ANY).contains(architecture.getCode())) {
                repositorySource = getRepository(context, request.getRepositoryCode())
                        .tryGetRepositorySourceForArchitecture(architecture)
                        .orElseThrow(() -> new ObjectNotFoundException(RepositorySource.class.getSimpleName(), ""));
            }
            else {
                LOGGER.info("unable to find the repository source from the repository [{}] and architecture [{}]"
                                + " - will not increment the counter", request.getRepositoryCode(), architecture);
            }
        }

        if (null != repositorySource) {
            PkgVersion pkgVersion = PkgVersion.tryGetForPkg(
                    context,
                    getPkg(context, request.getName()),
                    repositorySource,
                    architecture,
                    versionCoordinates
            ).orElseThrow(() -> new ObjectNotFoundException(
                    PkgVersion.class.getSimpleName(),
                    versionCoordinates));

            incrementCounter(pkgVersion);
        }
    }

    public void removePkgIcon(RemovePkgIconRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITICON)) {
            throw new AccessDeniedException("attempt to remove the icon for package ["
                    + pkg + "], but the user is not able to");
        }

        pkgIconService.removePkgIcon(
                context,
                new UserPkgSupplementModificationAgent(tryObtainAuthenticatedUser(context).orElse(null)),
                pkg.getPkgSupplement());

        context.commitChanges();
        LOGGER.info("did remove icons for pkg {}", pkg.getName());
    }

    public void removePkgScreenshot(RemovePkgScreenshotRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.getCode());

        final ObjectContext context = serverRuntime.newContext();
        PkgScreenshot screenshot = PkgScreenshot.getByCode(context, request.getCode());

        // check to see if the user has any permission for any of the associated
        // packages.

        if (screenshot.getPkgSupplement().getPkgs()
                .stream()
                .noneMatch(p -> permissionEvaluator.hasPermission(
                        SecurityContextHolder.getContext().getAuthentication(),
                        p, Permission.PKG_EDITSCREENSHOT))) {
            throw new AccessDeniedException("unable to remove the package screenshot for package");
        }

        pkgScreenshotService.deleteScreenshot(
                context,
                new UserPkgSupplementModificationAgent(obtainAuthenticatedUser(context)),
                screenshot);

        context.commitChanges();

        LOGGER.info("did remove the screenshot {}", request.getCode());
    }

    public void reorderPkgScreenshots(ReorderPkgScreenshotsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.getPkgName());
        Preconditions.checkNotNull(request.getCodes());

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITSCREENSHOT)) {
            throw new AccessDeniedException("unable to reorder the package screenshot");
        }

        pkgScreenshotService.reorderPkgScreenshots(context, pkg.getPkgSupplement(), request.getCodes());
        context.commitChanges();

        LOGGER.info("did reorder the screenshots on package [{}]", pkg.getName());
    }

    public SearchPkgsResult searchPkgs(SearchPkgsRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(StringUtils.isNotBlank(request.getArchitectureCode()), "an architecture code must be supplied");
        Preconditions.checkState(CollectionUtils.isNotEmpty(request.getRepositoryCodes()),"repository codes must be supplied and at least one is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getNaturalLanguageCode()));
        Preconditions.checkNotNull(request.getLimit());
        Preconditions.checkState(request.getLimit() > 0);

        if (null == request.getSortOrdering()) {
            request.setSortOrdering(SearchPkgsSortOrdering.NAME);
        }

        final ObjectContext context = serverRuntime.newContext();

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.getNaturalLanguageCode());

        PkgSearchSpecification specification = new PkgSearchSpecification();
        specification.setExpression(StringUtils.trimToNull(StringUtils.lowerCase(request.getExpression())));
        specification.setPkgCategory(Optional.ofNullable(request.getPkgCategoryCode())
                .map(pcc -> PkgCategory.getByCode(context, pcc))
                .orElse(null));
        specification.setExpressionType(Optional.ofNullable(request.getExpressionType())
                .map(etc -> PkgSearchSpecification.ExpressionType.valueOf(request.getExpressionType().name()))
                .orElse(null));
        specification.setNaturalLanguage(getNaturalLanguage(context, request.getNaturalLanguageCode()));
        specification.setDaysSinceLatestVersion(request.getDaysSinceLatestVersion());
        specification.setSortOrdering(PkgSearchSpecification.SortOrdering.valueOf(
                Optional.ofNullable(request.getSortOrdering()).orElse(SearchPkgsSortOrdering.NAME).name()));
        specification.setArchitecture(getArchitecture(context, request.getArchitectureCode()));
        specification.setRepositories(transformCodesToRepositories(context, request.getRepositoryCodes()));
        specification.setIncludeDevelopment(BooleanUtils.isTrue(request.getIncludeDevelopment()));
        specification.setOnlyNativeDesktop(BooleanUtils.isTrue(request.getOnlyNativeDesktop()));
        specification.setOnlyDesktop(BooleanUtils.isTrue(request.getOnlyDesktop()));
        specification.setLimit(request.getLimit());
        specification.setOffset(request.getOffset());

        int total = (int) pkgService.total(context, specification);
        List<SearchPkgsPkg> items = List.of();

        if (total > 0) {
            List<PkgVersion> searchedPkgVersions = pkgService.search(context, specification);

            // if there is a pattern then it is not possible to use the fixed lookup (which
            // is faster).

            final PkgLocalizationLookupService localPkgLocalizationLookupService =
                    null != specification.getExpressionAsPattern()
                            ? pkgLocalizationService
                            : new FixedPkgLocalizationLookupServiceImpl(context, searchedPkgVersions, naturalLanguage);

            items = searchedPkgVersions.stream()
                    .map(pv -> mapFromPkgVersionToSearchPkgPkg(
                            context, pv, naturalLanguage, specification,
                            localPkgLocalizationLookupService))
                    .toList();
        }

        return new SearchPkgsResult()
                .total(total)
                .items(items);
    }

    private SearchPkgsPkg mapFromPkgVersionToSearchPkgPkg(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            PkgSearchSpecification specification,
            PkgLocalizationLookupService localPkgLocalizationLookupService) {
        Optional<PkgUserRatingAggregate> pkgUserRatingAggregateOptional =
                PkgUserRatingAggregate.getByPkgAndRepository(
                        context,
                        pkgVersion.getPkg(),
                        pkgVersion.getRepositorySource().getRepository());

        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                localPkgLocalizationLookupService.resolvePkgVersionLocalization(
                        context, pkgVersion, specification.getExpressionAsPattern(), naturalLanguage);

        return new SearchPkgsPkg()
                .name(pkgVersion.getPkg().getName())
                .isDesktop(pkgVersion.getPkg().getIsDesktop())
                .isNativeDesktop(pkgVersion.getPkg().getIsNativeDesktop())
                .modifyTimestamp(pkgVersion.getPkg().getModifyTimestamp().getTime())
                .derivedRating(pkgUserRatingAggregateOptional
                        .map(_PkgUserRatingAggregate::getDerivedRating)
                        .map(BigDecimal::new)
                        .orElse(null))
                .hasAnyPkgIcons(CollectionUtils.isNotEmpty(PkgIconImage.findForPkg(context, pkgVersion.getPkg())))
                .versions(List.of(
                    new SearchPkgsPkgVersion()
                            .major(pkgVersion.getMajor())
                            .minor(pkgVersion.getMinor())
                            .micro(pkgVersion.getMicro())
                            .preRelease(pkgVersion.getPreRelease())
                            .revision(pkgVersion.getRevision())
                            .createTimestamp(pkgVersion.getCreateTimestamp().getTime())
                            .viewCounter(pkgVersion.getViewCounter())
                            .architectureCode(pkgVersion.getArchitecture().getCode())
                            .payloadLength(pkgVersion.getPayloadLength())
                            .title(resolvedPkgVersionLocalization.getTitle())
                            .summary(resolvedPkgVersionLocalization.getSummary())
                            .repositorySourceCode(pkgVersion.getRepositorySource().getCode())
                            .repositoryCode(pkgVersion.getRepositorySource().getRepository().getCode())
                            .descriptionSnippet(deriveDescriptionSnippet(specification, resolvedPkgVersionLocalization))
                ));
    }

    // only include a snippet from the description if there is no match on the
    // keyword in the summary.

    private String deriveDescriptionSnippet(
            PkgSearchSpecification specification,
            ResolvedPkgVersionLocalization resolvedPkgVersionLocalization
    ) {
        if (
                null != specification.getExpressionType()
                        && StringUtils.isNotBlank(specification.getExpression())
                        && Stream.of(
                                resolvedPkgVersionLocalization.getTitle(),
                                resolvedPkgVersionLocalization.getSummary())
                        .noneMatch(s -> StringUtils.containsIgnoreCase(
                                StringUtils.trimToEmpty(s),
                                StringUtils.trimToEmpty(specification.getExpression())))
        ) {
            return StringHelper.tryCreateTextSnippetAroundFoundText(
                            resolvedPkgVersionLocalization.getDescription(),
                            specification.getExpression(),
                            SNIPPET_LENGTH)
                    .orElse(null);
        }

        return null;
    }

    private List<Repository> transformCodesToRepositories(ObjectContext context, List<String> codes) {
        Preconditions.checkState(null != codes && !codes.isEmpty(), "the architecture codes must be supplied and at least one architecture is required");
        List<Repository> result = new ArrayList<>();

        for (String code : codes) {
            result.add(getRepository(context, code));
        }

        return result;
    }

    public void updatePkgCategories(UpdatePkgCategoriesRequestEnvelope request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.getPkgName()));
        Preconditions.checkNotNull(request.getPkgCategoryCodes());

        if (request.getPkgCategoryCodes().size() > PKGPKGCATEGORIES_MAX) {
            throw new IllegalStateException("a package is not able to be configured with more than " + PKGPKGCATEGORIES_MAX + " categories");
        }

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITCATEGORIES)) {
            throw new AccessDeniedException("attempt to configure the categories for package ["
                    + pkg + "], but the user is not able to");
        }

        User user = obtainAuthenticatedUser(context);

        List<PkgCategory> pkgCategories = new ArrayList<>(PkgCategory.getByCodes(context, request.getPkgCategoryCodes()));

        if (pkgCategories.size() != request.getPkgCategoryCodes().size()) {
            LOGGER.warn(
                    "request for {} categories yielded only {}; must be a code mismatch",
                    request.getPkgCategoryCodes().size(),
                    pkgCategories.size());

            throw new ObjectNotFoundException(PkgCategory.class.getSimpleName(), null);
        }

        pkgService.updatePkgCategories(context, new UserPkgSupplementModificationAgent(user), pkg, pkgCategories);

        context.commitChanges();

        LOGGER.info(
                "did configure {} categories for pkg {}",
                request.getPkgCategoryCodes().size(),
                pkg.getName()
        );
    }

    public void updatePkgChangelog(UpdatePkgChangelogRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "a package name must be supplied");

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITCHANGELOG)) {
            throw new AccessDeniedException("unable to edit the changelog for [" + pkg + "]");
        }

        User user = obtainAuthenticatedUser(context);

        pkgService.updatePkgChangelog(
                context,
                new UserPkgSupplementModificationAgent(user),
                pkg.getPkgSupplement(),
                StringUtils.trimToNull(request.getContent()));

        context.commitChanges();
    }

    public void updatePkgLocalization(UpdatePkgLocalizationRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "the package name must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        User user = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITLOCALIZATION)) {
            throw new AccessDeniedException("unable to edit the package localization for [" + pkg + "]");
        }

        request.getPkgLocalizations().forEach(l -> pkgLocalizationService.updatePkgLocalization(
                    context,
                    new UserPkgSupplementModificationAgent(user),
                    pkg.getPkgSupplement(),
                    getNaturalLanguage(context, l.getNaturalLanguageCode()),
                    l.getTitle(),
                    l.getSummary(),
                    l.getDescription()));

        context.commitChanges();

        LOGGER.info(
                "did update the localization for pkg {} for {} natural languages",
                pkg.getName(), request.getPkgLocalizations().size()
        );
    }

    public void updatePkgProminence(UpdatePkgProminenceRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "the package name must be supplied on the request");
        Preconditions.checkArgument(null != request.getProminenceOrdering(), "the presence ordering must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositoryCode()), "the repository code is required when updating a package prominence");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());
        Repository repository = getRepository(context, request.getRepositoryCode());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITPROMINENCE)) {
            throw new AccessDeniedException("unable to edit the package prominence for [" + pkg + "]");
        }

        Prominence prominence = Prominence.tryGetByOrdering(context, request.getProminenceOrdering())
                .orElseThrow(() -> new ObjectNotFoundException(Prominence.class.getSimpleName(), request.getProminenceOrdering()));

        PkgProminence pkgProminence = pkgService.ensurePkgProminence(
                context,
                pkg,
                repository);

        pkgProminence.setProminence(prominence);
        context.commitChanges();

        LOGGER.info("the prominence for {} has been set to; {}", pkg, prominence);
    }

    public void updatePkgVersion(UpdatePkgVersionRequestEnvelope request) {
        Preconditions.checkArgument(null != request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getPkgName()), "the package name must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getRepositorySourceCode()), "the repository source code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getArchitectureCode()), "the architecture code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getMajor()), "the version major must be supplied");

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.getPkgName());

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITVERSION)) {
            throw new AccessDeniedException("unable to update the package version for package [" + pkg + "]");
        }

        PkgVersion pkgVersion = PkgVersion.tryGetForPkg(
                context, pkg, getRepositorySource(context, request.getRepositorySourceCode()),
                getArchitecture(context, request.getArchitectureCode()),
                new VersionCoordinates(
                        request.getMajor(), request.getMinor(), request.getMicro(),
                        request.getPreRelease(), request.getRevision())
        ).orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), null));

        request.getFilter().forEach(f -> {
            switch (f) {
                case ACTIVE:
                    LOGGER.info("will update the package version active flag to {} for {}", request.getActive(), pkgVersion.toString());
                    pkgVersion.setActive(request.getActive());
                    pkgService.adjustLatest(context, pkgVersion.getPkg(), pkgVersion.getArchitecture());
                    break;
                default:
                    throw new IllegalStateException("unknown type of change to a pkg version");
            }
        });

        context.commitChanges();
    }

    /**
     * <p>This method will bump the view counter for the package version.</p>
     */

    private void incrementCounter(PkgVersion pkgVersion) {
        pkgService.incrementViewCounter(serverRuntime, pkgVersion.getObjectId());
    }

}
