/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.PkgVersionType;
import org.haiku.haikudepotserver.api1.model.pkg.PkgIcon;
import org.haiku.haikudepotserver.api1.model.pkg.PkgVersionUrl;
import org.haiku.haikudepotserver.api1.model.pkg.*;
import org.haiku.haikudepotserver.api1.support.BadPkgIconException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._PkgUserRatingAggregate;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haiku.haikudepotserver.pkg.FixedPkgLocalizationLookupServiceImpl;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/pkg") // TODO - remove old endpoint
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    private final static int PKGPKGCATEGORIES_MAX = 3;

    private final static int SNIPPET_LENGTH = 64;

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final PkgIconService pkgIconService;
    private final PkgScreenshotService pkgScreenshotService;
    private final PkgService pkgService;
    private final PkgLocalizationService pkgLocalizationService;
    private final ClientIdentifierSupplier clientIdentifierSupplier;

    private final Boolean shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient = true;

    /**
     * <p>This cache is used to keep track (in memory) of who has viewed a package so that repeat increments of the
     * viewing of the package counter can be avoided.</p>
     */

    private final Cache<String,Boolean> remoteIdentifierToPkgView = CacheBuilder
            .newBuilder()
            .maximumSize(2048)
            .expireAfterAccess(2, TimeUnit.DAYS)
            .build();

    public PkgApiImpl(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            PkgIconService pkgIconService,
            PkgScreenshotService pkgScreenshotService,
            PkgService pkgService,
            PkgLocalizationService pkgLocalizationService,
            ClientIdentifierSupplier clientIdentifierSupplier) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
        this.pkgScreenshotService = Preconditions.checkNotNull(pkgScreenshotService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
        this.clientIdentifierSupplier = Preconditions.checkNotNull(clientIdentifierSupplier);
    }

    private Pkg getPkg(ObjectContext context, String pkgName) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));

        Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, pkgName);

        if (pkgOptional.isEmpty()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), pkgName);
        }

        return pkgOptional.get();
    }

    private List<Repository> transformCodesToRepositories(ObjectContext context, List<String> codes) {
        Preconditions.checkState(null != codes && !codes.isEmpty(), "the architecture codes must be supplied and at least one architecture is required");
        List<Repository> result = new ArrayList<>();

        for (String code : codes) {
            result.add(getRepository(context, code));
        }

        return result;
    }

    /**
     * <p>This method will convert the supplied codes into a list of architectures.  It requires that at least one
     * architecture is supplied.  If any architecture code is not able to be converted into an architecture, it
     * will throw {@link ObjectNotFoundException}.</p>
     */

    private List<Architecture> transformCodesToArchitectures(ObjectContext context, List<String> codes) {
        Preconditions.checkState(null != codes && !codes.isEmpty(), "the architecture codes must be supplied and at least one architecture is required");
        List<Architecture> result = new ArrayList<>();

        for (String code : codes) {
            result.add(getArchitecture(context, code));
        }

        return result;
    }

    @Override
    public UpdatePkgCategoriesResult updatePkgCategories(UpdatePkgCategoriesRequest updatePkgCategoriesRequest) {
        Preconditions.checkNotNull(updatePkgCategoriesRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgCategoriesRequest.pkgName));
        Preconditions.checkNotNull(updatePkgCategoriesRequest.pkgCategoryCodes);

        if (updatePkgCategoriesRequest.pkgCategoryCodes.size() > PKGPKGCATEGORIES_MAX) {
            throw new IllegalStateException("a package is not able to be configured with more than " + PKGPKGCATEGORIES_MAX + " categories");
        }

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, updatePkgCategoriesRequest.pkgName);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITCATEGORIES)) {
            throw new AccessDeniedException("attempt to configure the categories for package ["
                    + pkg + "], but the user is not able to");
        }

        List<PkgCategory> pkgCategories = new ArrayList<>(PkgCategory.getByCodes(context, updatePkgCategoriesRequest.pkgCategoryCodes));

        if (pkgCategories.size() != updatePkgCategoriesRequest.pkgCategoryCodes.size()) {
            LOGGER.warn(
                    "request for {} categories yielded only {}; must be a code mismatch",
                    updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                    pkgCategories.size());

            throw new ObjectNotFoundException(PkgCategory.class.getSimpleName(), null);
        }

        pkgService.updatePkgCategories(context, pkg, pkgCategories);

        context.commitChanges();

        LOGGER.info(
                "did configure {} categories for pkg {}",
                updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                pkg.getName()
        );

        return new UpdatePkgCategoriesResult();
    }

    @Override
    public SearchPkgsResult searchPkgs(final SearchPkgsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(null != request.architectureCodes && !request.architectureCodes.isEmpty(), "architecture codes must be supplied and at least one is required");
        Preconditions.checkState(null!=request.repositoryCodes && !request.repositoryCodes.isEmpty(),"repository codes must be supplied and at least one is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        if (null == request.sortOrdering) {
            request.sortOrdering = SearchPkgsRequest.SortOrdering.NAME;
        }

        final ObjectContext context = serverRuntime.newContext();

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
        PkgSearchSpecification specification = new PkgSearchSpecification();

        String exp = request.expression;

        if (null != exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if (null != request.pkgCategoryCode) {
            specification.setPkgCategory(PkgCategory.getByCode(context, request.pkgCategoryCode).get());
        }

        if (null != request.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(request.expressionType.name()));
        }

        specification.setNaturalLanguage(getNaturalLanguage(context, request.naturalLanguageCode));
        specification.setDaysSinceLatestVersion(request.daysSinceLatestVersion);
        specification.setSortOrdering(PkgSearchSpecification.SortOrdering.valueOf(request.sortOrdering.name()));
        specification.setArchitectures(transformCodesToArchitectures(context, request.architectureCodes));
        specification.setRepositories(transformCodesToRepositories(context, request.repositoryCodes));
        specification.setLimit(request.limit);
        specification.setOffset(request.offset);

        SearchPkgsResult result = new SearchPkgsResult();

        // if there are more than we asked for then there must be more available.

        result.total = pkgService.total(context, specification);
        result.items = Collections.emptyList();

        if (result.total > 0) {

            List<PkgVersion> searchedPkgVersions = pkgService.search(context, specification);

            // if there is a pattern then it is not possible to use the fixed lookup (which
            // is faster).

            final PkgLocalizationLookupService localPkgLocalizationLookupService =
                    null != specification.getExpressionAsPattern()
                        ? pkgLocalizationService
                            : new FixedPkgLocalizationLookupServiceImpl(context, searchedPkgVersions, naturalLanguage);

            result.items = searchedPkgVersions
                    .stream()
                    .map(spv -> {
                        Optional<PkgUserRatingAggregate> pkgUserRatingAggregateOptional =
                                PkgUserRatingAggregate.getByPkgAndRepository(
                                        context,
                                        spv.getPkg(),
                                        spv.getRepositorySource().getRepository());

                        SearchPkgsResult.Pkg resultPkg = new SearchPkgsResult.Pkg();
                        resultPkg.name = spv.getPkg().getName();
                        resultPkg.modifyTimestamp = spv.getPkg().getModifyTimestamp().getTime();
                        resultPkg.derivedRating = pkgUserRatingAggregateOptional.map(_PkgUserRatingAggregate::getDerivedRating).orElse(null);
                        resultPkg.hasAnyPkgIcons = !PkgIconImage.findForPkg(context, spv.getPkg()).isEmpty();

                        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                                localPkgLocalizationLookupService.resolvePkgVersionLocalization(
                                    context, spv, specification.getExpressionAsPattern(), naturalLanguage);

                        SearchPkgsResult.PkgVersion resultVersion = new SearchPkgsResult.PkgVersion();
                        resultVersion.major = spv.getMajor();
                        resultVersion.minor = spv.getMinor();
                        resultVersion.micro = spv.getMicro();
                        resultVersion.preRelease = spv.getPreRelease();
                        resultVersion.revision = spv.getRevision();
                        resultVersion.createTimestamp = spv.getCreateTimestamp().getTime();
                        resultVersion.viewCounter = spv.getViewCounter();
                        resultVersion.architectureCode = spv.getArchitecture().getCode();
                        resultVersion.payloadLength = spv.getPayloadLength();
                        resultVersion.title = resolvedPkgVersionLocalization.getTitle();
                        resultVersion.summary = resolvedPkgVersionLocalization.getSummary();
                        resultVersion.repositorySourceCode = spv.getRepositorySource().getCode();
                        resultVersion.repositoryCode = spv.getRepositorySource().getRepository().getCode();

                        // only include a snippet from the description if there is no match on the
                        // keyword in the summary.

                        if (
                                null != request.expressionType
                                        && StringUtils.isNotBlank(request.expression)
                                        && Stream.of(
                                            resolvedPkgVersionLocalization.getTitle(),
                                            resolvedPkgVersionLocalization.getSummary())
                                            .noneMatch(s -> StringUtils.containsIgnoreCase(
                                                StringUtils.trimToEmpty(s),
                                                StringUtils.trimToEmpty(request.expression)))
                        ) {
                            resultVersion.descriptionSnippet = StringHelper.tryCreateTextSnippetAroundFoundText(
                                    resolvedPkgVersionLocalization.getDescription(),
                                    request.expression,
                                    SNIPPET_LENGTH)
                                    .orElse(null);
                        }

                        resultPkg.versions = Collections.singletonList(resultVersion);

                        return resultPkg;
                    })
                    .collect(Collectors.toList());
        }

        LOGGER.info("search for pkgs found {} results", result.items.size());

        return result;
    }

    /**
     * <p>Given the persistence model object, this method will construct the DTO to be sent back over the wire.</p>
     */

    private GetPkgResult.PkgVersion createGetPkgResultPkgVersion(ObjectContext context, PkgVersion pkgVersion, NaturalLanguage naturalLanguage) {
        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);

        GetPkgResult.PkgVersion version = new GetPkgResult.PkgVersion();

        version.isLatest = pkgVersion.getIsLatest();

        version.major = pkgVersion.getMajor();
        version.minor = pkgVersion.getMinor();
        version.micro = pkgVersion.getMicro();
        version.revision = pkgVersion.getRevision();
        version.preRelease = pkgVersion.getPreRelease();

        version.hasSource = pkgService.getCorrespondingSourcePkgVersion(context, pkgVersion).isPresent();
        version.createTimestamp = pkgVersion.getCreateTimestamp().getTime();
        version.payloadLength = pkgVersion.getPayloadLength();
        version.repositorySourceCode = pkgVersion.getRepositorySource().getCode();
        version.repositoryCode = pkgVersion.getRepositorySource().getRepository().getCode();
        version.architectureCode = pkgVersion.getArchitecture().getCode();
        version.copyrights = pkgVersion.getPkgVersionCopyrights().stream().map(PkgVersionCopyright::getBody).collect(Collectors.toList());
        version.licenses = pkgVersion.getPkgVersionLicenses().stream().map(PkgVersionLicense::getBody).collect(Collectors.toList());
        version.viewCounter = pkgVersion.getViewCounter();
        version.hpkgDownloadURL = pkgService.createHpkgDownloadUrl(pkgVersion);

        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
        pkgLocalizationService.resolvePkgVersionLocalization(context, pkgVersion, null, naturalLanguage);

        version.title = resolvedPkgVersionLocalization.getTitle();
        version.description = resolvedPkgVersionLocalization.getDescription();
        version.summary = resolvedPkgVersionLocalization.getSummary();

        version.urls =  pkgVersion.getPkgVersionUrls()
                .stream()
                .map(u -> new PkgVersionUrl(u.getPkgUrlType().getCode(), u.getUrl()))
                .collect(Collectors.toList());

        return version;
    }

    /**
     * <p>This method will bump the view counter for the package version.  It will also try to prevent
     * a user from the same client (IP) from doing this more than once within a reasonable stand-down
     * time.  This is prone to optimistic locking failure because lots of people can look at the same
     * package at the same time.  For this reason, it will try to load the data into a different
     * {@link org.apache.cayenne.ObjectContext} to edit.</p>
     */

    private void incrementCounter(PkgVersion pkgVersion) {
        String cacheKey = null;
        String remoteIdentifier = clientIdentifierSupplier.get().orElse("???");
        boolean shouldIncrement;

        if (shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient && !Strings.isNullOrEmpty(remoteIdentifier)) {
            Long pkgVersionId = (Long) pkgVersion.getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN);
            cacheKey = pkgVersionId + "@" + remoteIdentifier;
        }

        if (null==cacheKey) {
            shouldIncrement = true;
        } else {
            Boolean previouslyIncremented = remoteIdentifierToPkgView.getIfPresent(cacheKey);
            shouldIncrement = null == previouslyIncremented;

            if(!shouldIncrement) {
                LOGGER.info(
                        "would have incremented the view counter for '{}', but the client '{}' already did this recently",
                        pkgVersion.getPkg().toString(),
                        remoteIdentifier);
            }
        }

        if (shouldIncrement) {
            pkgService.incrementViewCounter(serverRuntime, pkgVersion.getObjectId());
        }

        if (null!=cacheKey) {
            remoteIdentifierToPkgView.put(cacheKey, Boolean.TRUE);
        }
    }

    @Override
    public GetPkgResult getPkg(GetPkgRequest request) {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.name), "request pkg name is required");
        Preconditions.checkNotNull(request.versionType);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkArgument(
                EnumSet.of(PkgVersionType.NONE, PkgVersionType.ALL).contains(request.versionType)
                        || !Strings.isNullOrEmpty(request.repositoryCode),
                "the repository code should be supplied of the version request is not ALL or NONE");

        final ObjectContext context = serverRuntime.newContext();

        Optional<Architecture> architectureOptional = Optional.empty();

        if (!Strings.isNullOrEmpty(request.architectureCode)) {
            architectureOptional = Architecture.tryGetByCode(context, request.architectureCode);
        }

        Pkg pkg = getPkg(context, request.name);
        Repository repository = null;

        if (!Strings.isNullOrEmpty(request.repositoryCode)) {
            repository = getRepository(context, request.repositoryCode);
        }

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        GetPkgResult result = new GetPkgResult();

        result.name = pkg.getName();
        result.modifyTimestamp = pkg.getModifyTimestamp().getTime();
        result.vanityLinkUrl = pkgService.createVanityLinkUrl(pkg);
        result.hasChangelog = pkg.getPkgSupplement().getPkgChangelog().isPresent();
        result.pkgCategoryCodes = pkg.getPkgSupplement().getPkgPkgCategories()
                .stream()
                .map(ppc -> ppc.getPkgCategory().getCode())
                .collect(Collectors.toList());

        if (null != repository) {
            Optional<PkgUserRatingAggregate> userRatingAggregate = pkg.getPkgUserRatingAggregate(repository);

            if (userRatingAggregate.isPresent()) {
                result.derivedRating = userRatingAggregate.get().getDerivedRating();
                result.derivedRatingSampleSize = userRatingAggregate.get().getDerivedRatingSampleSize();
            }
        }

        if (null != repository) {
            result.prominenceOrdering = pkg.getPkgProminence(repository)
                    .map(pp -> pp.getProminence().getOrdering())
                    .orElse(null);
        }

        switch (request.versionType) {

            // might be used to show a history of the versions.  If an architecture is present then it will
            // only return versions for that architecture.  If no architecture is present then it will return
            // versions for all architectures.

            case ALL: {

                List<PkgVersion> allVersions;

                if(null==repository) {
                    allVersions = PkgVersion.getForPkg(context, pkg, false); // active only
                }
                else {
                    allVersions = PkgVersion.getForPkg(context, pkg, repository, false); // active only
                }

                if(architectureOptional.isPresent()) {
                    final Architecture a = architectureOptional.get();
                    allVersions = allVersions.stream().filter(v -> v.getArchitecture().equals(a)).collect(Collectors.toList());
                }

                // now sort those.

                final VersionCoordinatesComparator vcc = new VersionCoordinatesComparator();

                Collections.sort(allVersions, (pv1, pv2) ->
                        ComparisonChain.start()
                                .compare(pv1.getArchitecture().getCode(), pv2.getArchitecture().getCode())
                                .compare(pv1.toVersionCoordinates(), pv2.toVersionCoordinates(), vcc)
                                .result()
                );

                result.versions = allVersions
                        .stream()
                        .map(v -> createGetPkgResultPkgVersion(context, v, naturalLanguage))
                        .collect(Collectors.toList());
            }
            break;

            case SPECIFIC: {
                if (architectureOptional.isEmpty()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; " + request.architectureCode);
                }

                VersionCoordinates coordinates = new VersionCoordinates(
                        request.major, request.minor, request.micro,
                        request.preRelease, request.revision);

                PkgVersion pkgVersion = PkgVersion.getForPkg(context, pkg, repository, architectureOptional.get(), coordinates)
                        .filter(_PkgVersion::getActive)
                        .orElseThrow(() ->
                            new ObjectNotFoundException(PkgVersion.class.getSimpleName(), "")
                        );

                if (null != request.incrementViewCounter && request.incrementViewCounter) {
                    incrementCounter(pkgVersion);
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
                        context,
                        pkgVersion,
                        naturalLanguage));
            }
            break;

            case LATEST: {
                if (architectureOptional.isEmpty()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; " + request.architectureCode);
                }

                PkgVersion pkgVersion = pkgService.getLatestPkgVersionForPkg(
                        context, pkg, repository,
                        ImmutableList.of(
                                architectureOptional.get(),
                                Architecture.tryGetByCode(context, Architecture.CODE_ANY).get(),
                                Architecture.tryGetByCode(context, Architecture.CODE_SOURCE).get())
                ).orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), request.name));

                if (null != request.incrementViewCounter && request.incrementViewCounter) {
                    incrementCounter(pkgVersion);
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
                        context, pkgVersion, naturalLanguage));
            }
            break;

            case NONE: // no version is actually required.
                break;

            default:
                throw new IllegalStateException("unhandled version type in request");
        }

        return result;
    }

    private boolean contains(
            final List<ConfigurePkgIconRequest.PkgIcon> pkgIconApis,
            final String mediaTypeCode,
            final Integer size) {

        Preconditions.checkNotNull(pkgIconApis);
        Preconditions.checkState(!Strings.isNullOrEmpty(mediaTypeCode));
        Preconditions.checkNotNull(size);

        return pkgIconApis
                .stream()
                .anyMatch(pi -> pi.mediaTypeCode.equals(mediaTypeCode) && (null!=pi.size) && pi.size.equals(size));
    }

    @Override
    public GetPkgIconsResult getPkgIcons(GetPkgIconsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied to get the package's icons");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.pkgName);

        GetPkgIconsResult result = new GetPkgIconsResult();
        result.pkgIcons = pkg.getPkgSupplement().getPkgIcons()
                .stream()
                .map(pi -> new PkgIcon(pi.getMediaType().getCode(), pi.getSize()))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public ConfigurePkgIconResult configurePkgIcon(ConfigurePkgIconRequest request) throws BadPkgIconException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.pkgName);

        User user = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITICON)) {
            throw new AccessDeniedException("attempt to configure the icon for package ["
                    + pkg + "], but the user is not able to");
        }

        // insert or override the icons

        int updated = 0;
        int removed = 0;

        Set<org.haiku.haikudepotserver.dataobjects.PkgIcon> createdOrUpdatedPkgIcons = new HashSet<>();

        if (null != request.pkgIcons && !request.pkgIcons.isEmpty()) {

            // either we have an HVIF icon or we have bitmaps.  If there if an HVIF one then we should
            // not have any other variants.  If there are bitmaps then we need 16, 32 and 64 sizes.

            if (request.pkgIcons
                    .stream()
                    .filter(pi -> pi.mediaTypeCode.equals(MediaType.MEDIATYPE_HAIKUVECTORICONFILE))
                    .collect(SingleCollector.optional()).isPresent()) {
                if(request.pkgIcons.size() > 1) {
                    throw new IllegalStateException("if an hvif icon is supplied then there should be no other variants.");
                }
            }
            else {
                if (!contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 16)
                    || !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 32)
                    || !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 64) ) {
                    throw new IllegalStateException("there should be three bitmap icons supplied in sizes 16, 32 and 64");
                }
            }

            for (ConfigurePkgIconRequest.PkgIcon pkgIconApi : request.pkgIcons) {

                MediaType mediaType = MediaType.tryGetByCode(context, pkgIconApi.mediaTypeCode).orElseThrow(
                        () -> new IllegalStateException("unknown media type; "+pkgIconApi.mediaTypeCode)
                );

                if (Strings.isNullOrEmpty(pkgIconApi.dataBase64)) {
                    throw new IllegalStateException("the base64 data must be supplied with the request to configure a pkg icon");
                }

                if (Strings.isNullOrEmpty(pkgIconApi.mediaTypeCode)) {
                    throw new IllegalStateException("the mediaTypeCode must be supplied to configure a pkg icon");
                }

                try {
                    byte[] data = Base64.getDecoder().decode(pkgIconApi.dataBase64);

                    ByteArrayInputStream dataInputStream = new ByteArrayInputStream(data);

                    createdOrUpdatedPkgIcons.add(pkgIconService.storePkgIconImage(
                            dataInputStream, mediaType, pkgIconApi.size, context, pkg.getPkgSupplement())
                    );

                    updated++;
                }
                catch (IOException ioe) {
                    throw new RuntimeException("a problem has arisen storing the data for an icon",ioe);
                }
                catch (org.haiku.haikudepotserver.pkg.model.BadPkgIconException bpie) {
                    throw new BadPkgIconException(pkgIconApi.mediaTypeCode, pkgIconApi.size, bpie);
                }

            }

        }

        // now we have some icons stored which may not be in the replacement data; we should remove those ones.

        for (org.haiku.haikudepotserver.dataobjects.PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgSupplement().getPkgIcons())) {
            if (!createdOrUpdatedPkgIcons.contains(pkgIcon)) {
                context.deleteObjects(
                        pkgIcon.getPkgIconImage(),
                        pkgIcon);

                removed++;
            }
        }

        pkg.setModifyTimestamp();

        context.commitChanges();

        LOGGER.info(
                "did configure icons for pkg {} (updated {}, removed {})",
                pkg.getName(),
                updated,
                removed
        );

        return new ConfigurePkgIconResult();
    }

    @Override
    public RemovePkgIconResult removePkgIcon(RemovePkgIconRequest request) {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.pkgName);

        User user = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITICON)) {
            throw new AccessDeniedException("attempt to remove the icon for package ["
                    + pkg + "], but the user is not able to");
        }

        pkgIconService.removePkgIcon(context, pkg.getPkgSupplement());

        context.commitChanges();

        LOGGER.info("did remove icons for pkg {}", pkg.getName());

        return new RemovePkgIconResult();
    }

    @Override
    public GetPkgScreenshotResult getPkgScreenshot(GetPkgScreenshotRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.code);

        final ObjectContext context = serverRuntime.newContext();
        Optional<PkgScreenshot> pkgScreenshotOptional = PkgScreenshot.tryGetByCode(context, request.code);

        if (pkgScreenshotOptional.isEmpty()) {
            throw new ObjectNotFoundException(PkgScreenshot.class.getSimpleName(), request.code);
        }

        GetPkgScreenshotResult result = new GetPkgScreenshotResult();
        result.code = pkgScreenshotOptional.get().getCode();
        result.height = pkgScreenshotOptional.get().getHeight();
        result.width = pkgScreenshotOptional.get().getWidth();
        result.length = pkgScreenshotOptional.get().getLength();
        return result;
    }

    private org.haiku.haikudepotserver.api1.model.pkg.PkgScreenshot createPkgScreenshot(PkgScreenshot pkgScreenshot) {
        Preconditions.checkNotNull(pkgScreenshot);
        org.haiku.haikudepotserver.api1.model.pkg.PkgScreenshot rs = new org.haiku.haikudepotserver.api1.model.pkg.PkgScreenshot();
        rs.code = pkgScreenshot.getCode();
        rs.height = pkgScreenshot.getHeight();
        rs.width = pkgScreenshot.getWidth();
        rs.length = pkgScreenshot.getLength();
        return rs;
    }

    @Override
    public GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) {
        Preconditions.checkNotNull(getPkgScreenshotsRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgScreenshotsRequest.pkgName));

        final ObjectContext context = serverRuntime.newContext();
        final Pkg pkg = getPkg(context, getPkgScreenshotsRequest.pkgName);

        GetPkgScreenshotsResult result = new GetPkgScreenshotsResult();
        result.items = pkg.getPkgSupplement().getSortedPkgScreenshots()
                .stream()
                .map(this::createPkgScreenshot)
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public RemovePkgScreenshotResult removePkgScreenshot(RemovePkgScreenshotRequest removePkgScreenshotRequest) {
        Preconditions.checkNotNull(removePkgScreenshotRequest);
        Preconditions.checkNotNull(removePkgScreenshotRequest.code);

        final ObjectContext context = serverRuntime.newContext();
        Optional<PkgScreenshot> screenshotOptional = PkgScreenshot.tryGetByCode(context, removePkgScreenshotRequest.code);

        if (screenshotOptional.isEmpty()) {
            throw new ObjectNotFoundException(PkgScreenshot.class.getSimpleName(), removePkgScreenshotRequest.code);
        }

        // check to see if the user has any permission for any of the associated
        // packages.

        if (screenshotOptional.get().getPkgSupplement().getPkgs()
                .stream()
                .noneMatch(p -> permissionEvaluator.hasPermission(
                        SecurityContextHolder.getContext().getAuthentication(),
                        p, Permission.PKG_EDITSCREENSHOT))) {
            throw new AccessDeniedException("unable to remove the package screenshot for package");
        }

        pkgScreenshotService.deleteScreenshot(context, screenshotOptional.get());
        context.commitChanges();

        LOGGER.info("did remove the screenshot {}", removePkgScreenshotRequest.code);

        return new RemovePkgScreenshotResult();
    }

    @Override
    public ReorderPkgScreenshotsResult reorderPkgScreenshots(ReorderPkgScreenshotsRequest reorderPkgScreenshotsRequest) {
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest);
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest.pkgName);
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest.codes);

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, reorderPkgScreenshotsRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITSCREENSHOT)) {
            throw new AccessDeniedException("unable to reorder the package screenshot");
        }

        pkgScreenshotService.reorderPkgScreenshots(context, pkg.getPkgSupplement(), reorderPkgScreenshotsRequest.codes);
        context.commitChanges();

        LOGGER.info("did reorder the screenshots on package [{}]", pkg.getName());

        return new ReorderPkgScreenshotsResult();
    }

    @Override
    public UpdatePkgLocalizationResult updatePkgLocalization(UpdatePkgLocalizationRequest updatePkgLocalizationRequest) {

        Preconditions.checkArgument(null != updatePkgLocalizationRequest);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(updatePkgLocalizationRequest.pkgName), "the package name must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, updatePkgLocalizationRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg,
                Permission.PKG_EDITLOCALIZATION)) {
            throw new AccessDeniedException("unable to edit the package localization for [" + pkg + "]");
        }

        for (org.haiku.haikudepotserver.api1.model.pkg.PkgLocalization requestPkgVersionLocalization : updatePkgLocalizationRequest.pkgLocalizations) {

            NaturalLanguage naturalLanguage = getNaturalLanguage(context, requestPkgVersionLocalization.naturalLanguageCode);

            pkgLocalizationService.updatePkgLocalization(
                    context,
                    pkg.getPkgSupplement(),
                    naturalLanguage,
                    requestPkgVersionLocalization.title,
                    requestPkgVersionLocalization.summary,
                    requestPkgVersionLocalization.description);
        }

        context.commitChanges();

        LOGGER.info(
                "did update the localization for pkg {} for {} natural languages",
                pkg.getName(),
                updatePkgLocalizationRequest.pkgLocalizations.size()
        );

        return new UpdatePkgLocalizationResult();
    }

    @Override
    public GetPkgLocalizationsResult getPkgLocalizations(GetPkgLocalizationsRequest getPkgLocalizationsRequest) {
        Preconditions.checkArgument(null != getPkgLocalizationsRequest, "a request is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getPkgLocalizationsRequest.pkgName), "a package name is required");
        Preconditions.checkArgument(null != getPkgLocalizationsRequest.naturalLanguageCodes, "the natural language codes must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, getPkgLocalizationsRequest.pkgName);

        GetPkgLocalizationsResult result = new GetPkgLocalizationsResult();
        result.pkgLocalizations = new ArrayList<>();
        List<PkgLocalization> pkgLocalizations = PkgLocalization.findForPkg(context, pkg);

        for (PkgLocalization pkgLocalization : pkgLocalizations) {
            if (getPkgLocalizationsRequest.naturalLanguageCodes.contains(pkgLocalization.getNaturalLanguage().getCode())) {
                org.haiku.haikudepotserver.api1.model.pkg.PkgLocalization resultPkgVersionLocalization = new org.haiku.haikudepotserver.api1.model.pkg.PkgLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = pkgLocalization.getNaturalLanguage().getCode();
                resultPkgVersionLocalization.title = pkgLocalization.getTitle();
                resultPkgVersionLocalization.summary = pkgLocalization.getSummary();
                resultPkgVersionLocalization.description = pkgLocalization.getDescription();
                result.pkgLocalizations.add(resultPkgVersionLocalization);
            }
        }

        return result;
    }

    @Override
    public GetPkgVersionLocalizationsResult getPkgVersionLocalizations(
            GetPkgVersionLocalizationsRequest getPkgVersionLocalizationsRequest) {
        Preconditions.checkNotNull(getPkgVersionLocalizationsRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.architectureCode));
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.pkgName));
        Preconditions.checkNotNull(getPkgVersionLocalizationsRequest.naturalLanguageCodes);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.repositoryCode), "the repository code must be supplied");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, getPkgVersionLocalizationsRequest.pkgName);
        Architecture architecture = getArchitecture(context, getPkgVersionLocalizationsRequest.architectureCode);
        Repository repository = getRepository(context, getPkgVersionLocalizationsRequest.repositoryCode);
        Optional<PkgVersion> pkgVersionOptional;

        if (null==getPkgVersionLocalizationsRequest.major) {
            pkgVersionOptional = pkgService.getLatestPkgVersionForPkg(
                    context, pkg, repository,
                    Collections.singletonList(architecture));
        }
        else {
            pkgVersionOptional = PkgVersion.getForPkg(
                    context, pkg, repository, architecture,
                    new VersionCoordinates(
                            getPkgVersionLocalizationsRequest.major,
                            getPkgVersionLocalizationsRequest.minor,
                            getPkgVersionLocalizationsRequest.micro,
                            getPkgVersionLocalizationsRequest.preRelease,
                            getPkgVersionLocalizationsRequest.revision));
        }

        if (pkgVersionOptional.isEmpty() || !pkgVersionOptional.get().getActive()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        GetPkgVersionLocalizationsResult result = new GetPkgVersionLocalizationsResult();
        result.pkgVersionLocalizations = new ArrayList<>();

        for (String naturalLanguageCode : getPkgVersionLocalizationsRequest.naturalLanguageCodes) {
            Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguageCode);

            if (pkgVersionLocalizationOptional.isPresent()) {
                org.haiku.haikudepotserver.api1.model.pkg.PkgVersionLocalization resultPkgVersionLocalization = new org.haiku.haikudepotserver.api1.model.pkg.PkgVersionLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = naturalLanguageCode;
                resultPkgVersionLocalization.description = pkgVersionLocalizationOptional.get().getDescription().orElse(null);
                resultPkgVersionLocalization.summary = pkgVersionLocalizationOptional.get().getSummary().orElse(null);
                result.pkgVersionLocalizations.add(resultPkgVersionLocalization);
            }
        }

        return result;
    }

    @Override
    public UpdatePkgProminenceResult updatePkgProminence(UpdatePkgProminenceRequest request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied on the request");
        Preconditions.checkArgument(null != request.prominenceOrdering, "the presence ordering must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code is required when updating a package prominence");

        final ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.pkgName);
        Repository repository = getRepository(context, request.repositoryCode);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITPROMINENCE)) {
            throw new AccessDeniedException("unable to edit the package prominence for [" + pkg + "]");
        }

        Optional<Prominence> prominenceOptional = Prominence.getByOrdering(context, request.prominenceOrdering);

        if (prominenceOptional.isEmpty()) {
            throw new ObjectNotFoundException(Prominence.class.getSimpleName(), request.prominenceOrdering);
        }

        PkgProminence pkgProminence = pkgService.ensurePkgProminence(
                context,
                pkg,
                repository);

        pkgProminence.setProminence(prominenceOptional.get());
        context.commitChanges();

        LOGGER.info("the prominence for {} has been set to; {}", pkg.toString(), prominenceOptional.get().toString());

        return new UpdatePkgProminenceResult();
    }


    @Override
    public GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequest request) {
        Preconditions.checkArgument(null!=request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied");

        ObjectContext context = serverRuntime.newContext();
        Optional<PkgChangelog> pkgChangelogOptional = getPkg(context, request.pkgName)
                .getPkgSupplement().getPkgChangelog();
        GetPkgChangelogResult result = new GetPkgChangelogResult();

        pkgChangelogOptional.ifPresent(pkgChangelog -> result.content = pkgChangelog.getContent());

        return result;
    }

    @Override
    public UpdatePkgChangelogResult updatePkgChangelog(UpdatePkgChangelogRequest request) {
        Preconditions.checkArgument(null!=request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied");

        ObjectContext context = serverRuntime.newContext();
        User authUser = obtainAuthenticatedUser(context);
        Pkg pkg = getPkg(context, request.pkgName);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITCHANGELOG)) {
            throw new AccessDeniedException("unable to edit the changelog for [" + pkg + "]");
        }

        String newContent = request.content;

        if (null!=newContent) {
            newContent = Strings.emptyToNull(newContent.trim());
        }

        pkgService.updatePkgChangelog(context, pkg.getPkgSupplement(), newContent);
        context.commitChanges();

        return new UpdatePkgChangelogResult();
    }

    @Override
    public UpdatePkgVersionResult updatePkgVersion(UpdatePkgVersionRequest request) {
        Preconditions.checkArgument(null!=request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.architectureCode), "the architecture code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.major), "the version major must be supplied");

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = getPkg(context, request.pkgName);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                pkg, Permission.PKG_EDITVERSION)) {
            throw new AccessDeniedException("unable to update the package version for package [" + pkg + "]");
        }

        PkgVersion pkgVersion = PkgVersion.getForPkg(
                context, pkg, getRepository(context, request.repositoryCode),
                getArchitecture(context, request.architectureCode),
                new VersionCoordinates(request.major, request.minor, request.micro, request.preRelease, request.revision)
        ).orElseThrow(() -> new ObjectNotFoundException(PkgVersion.class.getSimpleName(), null));

        for(UpdatePkgVersionRequest.Filter filter : request.filter) {
            switch (filter) {
                case ACTIVE:
                    LOGGER.info("will update the package version active flag to {} for {}", request.active, pkgVersion.toString());
                    pkgVersion.setActive(request.active);
                    pkgService.adjustLatest(context, pkgVersion.getPkg(), pkgVersion.getArchitecture());
                    break;
            }
        }

        context.commitChanges();

        return new UpdatePkgVersionResult();
    }

    @Override
    public IncrementViewCounterResult incrementViewCounter(IncrementViewCounterRequest request) {
        Preconditions.checkArgument(null!=request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.name), "the package name must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.architectureCode), "the architecture must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.major), "the version major must be supplied");

        ObjectContext context = serverRuntime.newContext();

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                request.major,
                request.minor,
                request.micro,
                request.preRelease,
                request.revision
        );
        PkgVersion pkgVersion = PkgVersion.getForPkg(
                context,
                Pkg.getByName(context, request.name),
                Repository.getByCode(context, request.repositoryCode),
                Architecture.getByCode(context, request.architectureCode),
                versionCoordinates
        ).orElseThrow(() -> new ObjectNotFoundException(
                PkgVersion.class.getSimpleName(),
                versionCoordinates));

        incrementCounter(pkgVersion);

        return new IncrementViewCounterResult();
    }

}
