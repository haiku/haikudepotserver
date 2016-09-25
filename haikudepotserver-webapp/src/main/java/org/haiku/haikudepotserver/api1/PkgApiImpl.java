/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.AbstractQueueJobResult;
import org.haiku.haikudepotserver.api1.model.PkgVersionType;
import org.haiku.haikudepotserver.api1.model.pkg.*;
import org.haiku.haikudepotserver.api1.model.pkg.PkgIcon;
import org.haiku.haikudepotserver.api1.model.pkg.PkgVersionUrl;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.BadPkgIconException;
import org.haiku.haikudepotserver.api1.support.LimitExceededException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.pkg.controller.PkgDownloadController;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.repository.RepositoryOrchestrationService;
import org.haiku.haikudepotserver.security.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/pkg") // TODO - remove old endpoint
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    public final static int PKGPKGCATEGORIES_MAX = 3;

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Resource
    private PkgOrchestrationService pkgService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Value("${pkgversion.viewcounter.protectrecurringincrementfromsameclient:true}")
    private Boolean shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient;

    /**
     * <p>This cache is used to keep track (in memory) of who has viewed a package so that repeat increments of the
     * viewing of the package counter can be avoided.</p>
     */

    private Cache<String,Boolean> remoteIdentifierToPkgView = CacheBuilder
            .newBuilder()
            .maximumSize(2048)
            .expireAfterAccess(2, TimeUnit.DAYS)
            .build();

    private Pkg getPkg(ObjectContext context, String pkgName) throws ObjectNotFoundException {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgName));

        Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), pkgName);
        }

        return pkgOptional.get();
    }

    private List<Repository> transformCodesToRepositories(ObjectContext context, List<String> codes) throws ObjectNotFoundException {
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

    private List<Architecture> transformCodesToArchitectures(ObjectContext context, List<String> codes) throws ObjectNotFoundException {
        Preconditions.checkState(null != codes && !codes.isEmpty(), "the architecture codes must be supplied and at least one architecture is required");
        List<Architecture> result = new ArrayList<>();

        for (String code : codes) {
            result.add(getArchitecture(context, code));
        }

        return result;
    }

    @Override
    public UpdatePkgCategoriesResult updatePkgCategories(UpdatePkgCategoriesRequest updatePkgCategoriesRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(updatePkgCategoriesRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgCategoriesRequest.pkgName));
        Preconditions.checkNotNull(updatePkgCategoriesRequest.pkgCategoryCodes);

        if(updatePkgCategoriesRequest.pkgCategoryCodes.size() > PKGPKGCATEGORIES_MAX) {
            throw new IllegalStateException("a package is not able to be configured with more than " + PKGPKGCATEGORIES_MAX + " categories");
        }

        final ObjectContext context = serverRuntime.getContext();

        Pkg pkg = getPkg(context, updatePkgCategoriesRequest.pkgName);

        User user = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user, pkg, Permission.PKG_EDITCATEGORIES)) {
            LOGGER.warn("attempt to configure the categories for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        List<PkgCategory> pkgCategories = new ArrayList<>(PkgCategory.getByCodes(context, updatePkgCategoriesRequest.pkgCategoryCodes));

        if(pkgCategories.size() != updatePkgCategoriesRequest.pkgCategoryCodes.size()) {
            LOGGER.warn(
                    "request for {} categories yielded only {}; must be a code mismatch",
                    updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                    pkgCategories.size());

            throw new ObjectNotFoundException(PkgCategory.class.getSimpleName(), null);
        }

        pkgOrchestrationService.updatePkgCategories(context, pkg, pkgCategories);

        context.commitChanges();

        LOGGER.info(
                "did configure {} categories for pkg {}",
                new Object[]{
                        updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                        pkg.getName(),
                }
        );

        return new UpdatePkgCategoriesResult();
    }

    @Override
    public SearchPkgsResult searchPkgs(final SearchPkgsRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(null != request.architectureCodes && !request.architectureCodes.isEmpty(), "architecture codes must be supplied and at least one is required");
        Preconditions.checkState(null!=request.repositoryCodes && !request.repositoryCodes.isEmpty(),"repository codes must be supplied and at least one is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        if(null==request.sortOrdering) {
            request.sortOrdering = SearchPkgsRequest.SortOrdering.NAME;
        }

        final ObjectContext context = serverRuntime.getContext();

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);
        PkgSearchSpecification specification = new PkgSearchSpecification();

        String exp = request.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!=request.pkgCategoryCode) {
            specification.setPkgCategory(PkgCategory.getByCode(context, request.pkgCategoryCode).get());
        }

        if(null!=request.expressionType) {
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

        if(result.total > 0) {

            List<PkgVersion> searchedPkgVersions = pkgService.search(context, specification);

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
                        resultPkg.derivedRating = pkgUserRatingAggregateOptional.isPresent() ? pkgUserRatingAggregateOptional.get().getDerivedRating() : null;
                        resultPkg.hasAnyPkgIcons = !PkgIconImage.findForPkg(context, spv.getPkg()).isEmpty();

                        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization = pkgOrchestrationService.resolvePkgVersionLocalization(
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

                        resultPkg.versions = Collections.singletonList(resultVersion);

                        return resultPkg;
                    })
                    .collect(Collectors.toList());
        }

        LOGGER.info("search for pkgs found {} results", result.items.size());

        return result;
    }

    private String createHpkgDownloadUrl(PkgVersion pkgVersion) {
        URL packagesBaseUrl = pkgVersion.getRepositorySource().getPackagesBaseURL();

        if(ImmutableSet.of("http","https").contains(packagesBaseUrl.getProtocol())) {
            return pkgVersion.getHpkgURL().toString();
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(PkgDownloadController.SEGMENT_PKGDOWNLOAD);
        pkgVersion.appendPathSegments(builder);
        builder.path("package.hpkg");
        return builder.build().toUriString();
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

        version.hasSource = pkgOrchestrationService.getCorrespondingSourcePkgVersion(context, pkgVersion).isPresent();
        version.createTimestamp = pkgVersion.getCreateTimestamp().getTime();
        version.payloadLength = pkgVersion.getPayloadLength();
        version.repositorySourceCode = pkgVersion.getRepositorySource().getCode();
        version.repositoryCode = pkgVersion.getRepositorySource().getRepository().getCode();
        version.architectureCode = pkgVersion.getArchitecture().getCode();
        version.copyrights = pkgVersion.getPkgVersionCopyrights().stream().map(PkgVersionCopyright::getBody).collect(Collectors.toList());
        version.licenses = pkgVersion.getPkgVersionLicenses().stream().map(PkgVersionLicense::getBody).collect(Collectors.toList());
        version.viewCounter = pkgVersion.getViewCounter();
        version.hpkgDownloadURL = createHpkgDownloadUrl(pkgVersion);

        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                pkgOrchestrationService.resolvePkgVersionLocalization(context, pkgVersion, null, naturalLanguage);

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
        String remoteIdentifier = getRemoteIdentifier();
        boolean shouldIncrement;

        if(shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient && !Strings.isNullOrEmpty(remoteIdentifier)) {
            Long pkgVersionId = (Long) pkgVersion.getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN);
            cacheKey = Long.toString(pkgVersionId) + "@" + remoteIdentifier;
        }

        if(null==cacheKey) {
            shouldIncrement = true;
        }
        else {
            Boolean previouslyIncremented = remoteIdentifierToPkgView.getIfPresent(cacheKey);
            shouldIncrement = null==previouslyIncremented;

            if(!shouldIncrement) {
                LOGGER.info(
                        "would have incremented the view counter for '{}', but the client '{}' already did this recently",
                        pkgVersion.getPkg().toString(),
                        remoteIdentifier);
            }
        }

        if(shouldIncrement) {
            pkgOrchestrationService.incrementViewCounter(serverRuntime, pkgVersion.getObjectId());
        }

        if(null!=cacheKey) {
            remoteIdentifierToPkgView.put(cacheKey, Boolean.TRUE);
        }
    }

    @Override
    public GetPkgResult getPkg(GetPkgRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.name), "request pkg name is required");
        Preconditions.checkNotNull(request.versionType);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkArgument(
                EnumSet.of(PkgVersionType.NONE, PkgVersionType.ALL).contains(request.versionType)
                        || !Strings.isNullOrEmpty(request.repositoryCode),
                "the repository code should be supplied of the version request is not ALL or NONE");

        final ObjectContext context = serverRuntime.getContext();

        Optional<Architecture> architectureOptional = Optional.empty();

        if(!Strings.isNullOrEmpty(request.architectureCode)) {
            architectureOptional = Architecture.getByCode(context, request.architectureCode);
        }

        Pkg pkg = getPkg(context, request.name);
        Repository repository = null;

        if(!Strings.isNullOrEmpty(request.repositoryCode)) {
            repository = getRepository(context, request.repositoryCode);
        }

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        GetPkgResult result = new GetPkgResult();

        result.name = pkg.getName();
        result.modifyTimestamp = pkg.getModifyTimestamp().getTime();
        result.pkgCategoryCodes = pkg.getPkgPkgCategories()
                .stream()
                .map(ppc -> ppc.getPkgCategory().getCode())
                .collect(Collectors.toList());

        if(null != repository) {
            Optional<PkgUserRatingAggregate> userRatingAggregate = pkg.getPkgUserRatingAggregate(repository);

            if (userRatingAggregate.isPresent()) {
                result.derivedRating = userRatingAggregate.get().getDerivedRating();
                result.derivedRatingSampleSize = userRatingAggregate.get().getDerivedRatingSampleSize();
            }
        }

        if(null != repository) {
            result.prominenceOrdering = pkg.getPkgProminence(repository)
                    .map(pp -> pp.getProminence().getOrdering())
                    .orElse(null);
        }

        switch(request.versionType) {

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

                Collections.sort(allVersions, new Comparator<PkgVersion>() {
                    @Override
                    public int compare(PkgVersion o1, PkgVersion o2) {
                        int result = o1.getArchitecture().getCode().compareTo(o2.getArchitecture().getCode());

                        if(0==result) {
                            result = vcc.compare(o1.toVersionCoordinates(),o2.toVersionCoordinates());
                        }

                        return result;
                    }
                });

                result.versions = allVersions
                        .stream()
                        .map(v -> createGetPkgResultPkgVersion(context, v, naturalLanguage))
                        .collect(Collectors.toList());
            }
            break;

            case SPECIFIC: {
                if (!architectureOptional.isPresent()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; " + request.architectureCode);
                }

                VersionCoordinates coordinates = new VersionCoordinates(
                        request.major, request.minor, request.micro,
                        request.preRelease, request.revision);

                Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                        context, pkg, repository,
                        architectureOptional.get(),
                        coordinates);

                if (!pkgVersionOptional.isPresent() || !pkgVersionOptional.get().getActive()) {
                    throw new ObjectNotFoundException(
                            PkgVersion.class.getSimpleName(),
                            "");
                }

                if (null != request.incrementViewCounter && request.incrementViewCounter) {
                    incrementCounter(pkgVersionOptional.get());
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
                        context,
                        pkgVersionOptional.get(),
                        naturalLanguage));
            }
            break;

            case LATEST: {
                if (!architectureOptional.isPresent()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; " + request.architectureCode);
                }

                Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                        context, pkg, repository,
                        ImmutableList.of(
                                architectureOptional.get(),
                                Architecture.getByCode(context, Architecture.CODE_ANY).get(),
                                Architecture.getByCode(context, Architecture.CODE_SOURCE).get())
                );

                if (!pkgVersionOptional.isPresent()) {
                    throw new ObjectNotFoundException(
                            PkgVersion.class.getSimpleName(),
                            request.name);
                }

                if (null != request.incrementViewCounter && request.incrementViewCounter) {
                    incrementCounter(pkgVersionOptional.get());
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
                        context,
                        pkgVersionOptional.get(),
                        naturalLanguage));
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
                .filter(pi -> pi.mediaTypeCode.equals(mediaTypeCode) && (null!=pi.size) && pi.size.equals(size))
                .findFirst()
                .isPresent();
    }

    @Override
    public GetPkgIconsResult getPkgIcons(GetPkgIconsRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied to get the package's icons");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);

        GetPkgIconsResult result = new GetPkgIconsResult();
        result.pkgIcons = pkg.getPkgIcons()
                .stream()
                .map(pi -> new PkgIcon(pi.getMediaType().getCode(), pi.getSize()))
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public ConfigurePkgIconResult configurePkgIcon(ConfigurePkgIconRequest request) throws ObjectNotFoundException, BadPkgIconException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);

        User user = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user, pkg, Permission.PKG_EDITICON)) {
            LOGGER.warn("attempt to configure the icon for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        // insert or override the icons

        int updated = 0;
        int removed = 0;

        Set<org.haiku.haikudepotserver.dataobjects.PkgIcon> createdOrUpdatedPkgIcons = new HashSet<>();

        if(null!=request.pkgIcons && !request.pkgIcons.isEmpty()) {

            // either we have an HVIF icon or we have bitmaps.  If there if an HVIF one then we should
            // not have any other variants.  If there are bitmaps then we need 16, 32 and 64 sizes.

            if(request.pkgIcons
                    .stream()
                    .filter(pi -> pi.mediaTypeCode.equals(MediaType.MEDIATYPE_HAIKUVECTORICONFILE))
                    .collect(SingleCollector.optional()).isPresent()) {
                if(request.pkgIcons.size() > 1) {
                    throw new IllegalStateException("if an hvif icon is supplied then there should be no other variants.");
                }
            }
            else {
                if(!contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 16)
                    || !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 32)
                    || !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 64) ) {
                    throw new IllegalStateException("there should be three bitmap icons supplied in sizes 16, 32 and 64");
                }
            }

            for(ConfigurePkgIconRequest.PkgIcon pkgIconApi : request.pkgIcons) {

                Optional<MediaType> mediaTypeOptional = MediaType.getByCode(context, pkgIconApi.mediaTypeCode);

                if(!mediaTypeOptional.isPresent()) {
                    throw new IllegalStateException("unknown media type; "+pkgIconApi.mediaTypeCode);
                }

                if(Strings.isNullOrEmpty(pkgIconApi.dataBase64)) {
                    throw new IllegalStateException("the base64 data must be supplied with the request to configure a pkg icon");
                }

                if(Strings.isNullOrEmpty(pkgIconApi.mediaTypeCode)) {
                    throw new IllegalStateException("the mediaTypeCode must be supplied to configure a pkg icon");
                }

                try {
                    byte[] data = Base64.getDecoder().decode(pkgIconApi.dataBase64);

                    ByteArrayInputStream dataInputStream = new ByteArrayInputStream(data);

                    createdOrUpdatedPkgIcons.add(
                            pkgService.storePkgIconImage(
                                    dataInputStream,
                                    mediaTypeOptional.get(),
                                    pkgIconApi.size,
                                    context,
                                    pkg
                            )
                    );

                    updated++;
                }
                catch(IOException ioe) {
                    throw new RuntimeException("a problem has arisen storing the data for an icon",ioe);
                }
                catch(org.haiku.haikudepotserver.pkg.model.BadPkgIconException bpie) {
                    throw new BadPkgIconException(pkgIconApi.mediaTypeCode, pkgIconApi.size, bpie);
                }

            }

        }

        // now we have some icons stored which may not be in the replacement data; we should remove those ones.

        for(org.haiku.haikudepotserver.dataobjects.PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
            if(!createdOrUpdatedPkgIcons.contains(pkgIcon)) {
                context.deleteObjects(
                        pkgIcon.getPkgIconImage().get(),
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
    public RemovePkgIconResult removePkgIcon(RemovePkgIconRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);

        User user = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user, pkg, Permission.PKG_EDITICON)) {
            LOGGER.warn("attempt to remove the icon for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        for(org.haiku.haikudepotserver.dataobjects.PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
            context.deleteObjects(
                    pkgIcon.getPkgIconImage().get(),
                    pkgIcon);
        }

        pkg.setModifyTimestamp();

        context.commitChanges();

        LOGGER.info("did remove icons for pkg {}", pkg.getName());

        return new RemovePkgIconResult();
    }

    @Override
    public GetPkgScreenshotResult getPkgScreenshot(GetPkgScreenshotRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(request.code);

        final ObjectContext context = serverRuntime.getContext();
        Optional<PkgScreenshot> pkgScreenshotOptional = PkgScreenshot.getByCode(context, request.code);

        if(!pkgScreenshotOptional.isPresent()) {
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
    public GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getPkgScreenshotsRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgScreenshotsRequest.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        final Pkg pkg = getPkg(context, getPkgScreenshotsRequest.pkgName);

        GetPkgScreenshotsResult result = new GetPkgScreenshotsResult();
        result.items = pkg.getSortedPkgScreenshots()
                .stream()
                .map(this::createPkgScreenshot)
                .collect(Collectors.toList());

        return result;
    }

    @Override
    public RemovePkgScreenshotResult removePkgScreenshot(RemovePkgScreenshotRequest removePkgScreenshotRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(removePkgScreenshotRequest);
        Preconditions.checkNotNull(removePkgScreenshotRequest.code);

        final ObjectContext context = serverRuntime.getContext();
        Optional<PkgScreenshot> screenshotOptional = PkgScreenshot.getByCode(context, removePkgScreenshotRequest.code);

        if(!screenshotOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgScreenshot.class.getSimpleName(), removePkgScreenshotRequest.code);
        }

        User authUser = obtainAuthenticatedUser(context);
        Pkg pkg = screenshotOptional.get().getPkg();

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITSCREENSHOT)) {
            throw new AuthorizationFailureException();
        }

        pkg.removeToManyTarget(Pkg.PKG_SCREENSHOTS_PROPERTY, screenshotOptional.get(), true);

        Optional<PkgScreenshotImage> image = screenshotOptional.get().getPkgScreenshotImage();

        if(image.isPresent()) {
            context.deleteObjects(image.get());
        }

        context.deleteObjects(screenshotOptional.get());
        context.commitChanges();

        LOGGER.info("did remove the screenshot {} on package {}", removePkgScreenshotRequest.code, pkg.getName());

        return new RemovePkgScreenshotResult();
    }

    @Override
    public ReorderPkgScreenshotsResult reorderPkgScreenshots(ReorderPkgScreenshotsRequest reorderPkgScreenshotsRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest);
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest.pkgName);
        Preconditions.checkNotNull(reorderPkgScreenshotsRequest.codes);

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, reorderPkgScreenshotsRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITSCREENSHOT)) {
            throw new AuthorizationFailureException();
        }

        pkg.reorderPkgScreenshots(reorderPkgScreenshotsRequest.codes);
        context.commitChanges();

        LOGGER.info("did reorder the screenshots on package {}", pkg.getName());

        return new ReorderPkgScreenshotsResult();
    }

    @Override
    public UpdatePkgLocalizationResult updatePkgLocalization(UpdatePkgLocalizationRequest updatePkgLocalizationRequest) throws ObjectNotFoundException {

        Preconditions.checkArgument(null != updatePkgLocalizationRequest);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(updatePkgLocalizationRequest.pkgName), "the package name must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, updatePkgLocalizationRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITLOCALIZATION)) {
            throw new AuthorizationFailureException();
        }

        for(org.haiku.haikudepotserver.api1.model.pkg.PkgLocalization requestPkgVersionLocalization : updatePkgLocalizationRequest.pkgLocalizations) {

            NaturalLanguage naturalLanguage = getNaturalLanguage(context, requestPkgVersionLocalization.naturalLanguageCode);

            pkgService.updatePkgLocalization(
                    context,
                    pkg,
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
    public GetPkgLocalizationsResult getPkgLocalizations(GetPkgLocalizationsRequest getPkgLocalizationsRequest) throws ObjectNotFoundException {
        Preconditions.checkArgument(null != getPkgLocalizationsRequest, "a request is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getPkgLocalizationsRequest.pkgName), "a package name is required");
        Preconditions.checkArgument(null != getPkgLocalizationsRequest.naturalLanguageCodes, "the natural language codes must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, getPkgLocalizationsRequest.pkgName);

        GetPkgLocalizationsResult result = new GetPkgLocalizationsResult();
        result.pkgLocalizations = new ArrayList<>();
        List<PkgLocalization> pkgLocalizations = PkgLocalization.findForPkg(context, pkg);

        for(PkgLocalization pkgLocalization : pkgLocalizations) {
            if(getPkgLocalizationsRequest.naturalLanguageCodes.contains(pkgLocalization.getNaturalLanguage().getCode())) {
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
    public GetPkgVersionLocalizationsResult getPkgVersionLocalizations(GetPkgVersionLocalizationsRequest getPkgVersionLocalizationsRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getPkgVersionLocalizationsRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.architectureCode));
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.pkgName));
        Preconditions.checkNotNull(getPkgVersionLocalizationsRequest.naturalLanguageCodes);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getPkgVersionLocalizationsRequest.repositoryCode), "the repository code must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, getPkgVersionLocalizationsRequest.pkgName);
        Architecture architecture = getArchitecture(context, getPkgVersionLocalizationsRequest.architectureCode);
        Repository repository = getRepository(context, getPkgVersionLocalizationsRequest.repositoryCode);
        Optional<PkgVersion> pkgVersionOptional;

        if(null==getPkgVersionLocalizationsRequest.major) {
            pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
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

        if(!pkgVersionOptional.isPresent() || !pkgVersionOptional.get().getActive()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        GetPkgVersionLocalizationsResult result = new GetPkgVersionLocalizationsResult();
        result.pkgVersionLocalizations = new ArrayList<>();

        for(String naturalLanguageCode : getPkgVersionLocalizationsRequest.naturalLanguageCodes) {
            Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguageCode);

            if(pkgVersionLocalizationOptional.isPresent()) {
                org.haiku.haikudepotserver.api1.model.pkg.PkgVersionLocalization resultPkgVersionLocalization = new org.haiku.haikudepotserver.api1.model.pkg.PkgVersionLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = naturalLanguageCode;
                resultPkgVersionLocalization.description = pkgVersionLocalizationOptional.get().getDescription().orElse(null);
                resultPkgVersionLocalization.summary = pkgVersionLocalizationOptional.get().getSummary().orElse(null);
                result.pkgVersionLocalizations.add(resultPkgVersionLocalization);
            }
        }

        return result;
    }

    private GetBulkPkgResult.PkgVersion createGetBulkPkgResultPkgVersion(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            boolean includeDescription) {

        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);

        GetBulkPkgResult.PkgVersion version = new GetBulkPkgResult.PkgVersion();

        version.repositorySourceCode = pkgVersion.getRepositorySource().getCode();
        version.repositoryCode = pkgVersion.getRepositorySource().getRepository().getCode();
        version.major = pkgVersion.getMajor();
        version.minor = pkgVersion.getMinor();
        version.micro = pkgVersion.getMicro();
        version.revision = pkgVersion.getRevision();
        version.preRelease = pkgVersion.getPreRelease();
        version.architectureCode = pkgVersion.getArchitecture().getCode();
        version.payloadLength = pkgVersion.getPayloadLength();

        ResolvedPkgVersionLocalization resolvedPkgVersionLocalization =
                pkgOrchestrationService.resolvePkgVersionLocalization(context, pkgVersion, null, naturalLanguage);

        if(includeDescription) {
            version.description = resolvedPkgVersionLocalization.getDescription();
        }

        version.summary = resolvedPkgVersionLocalization.getSummary();
        version.title = resolvedPkgVersionLocalization.getTitle();

        return version;

    }

    private GetBulkPkgResult.Pkg createBulkPkgResultPkg(
            ObjectContext context,
            NaturalLanguage naturalLanguage,
            GetBulkPkgRequest getBulkPkgRequest,
            PkgVersion pkgVersion) {

        GetBulkPkgResult.Pkg resultPkg = new GetBulkPkgResult.Pkg();
        resultPkg.modifyTimestamp = pkgVersion.getPkg().getModifyTimestamp().getTime();
        resultPkg.name = pkgVersion.getPkg().getName();

        Pkg pkg = pkgVersion.getPkg();

        resultPkg.prominenceOrdering = pkg
                .getPkgProminence(pkgVersion.getRepositorySource().getRepository())
                .map(pp -> pp.getProminence().getOrdering())
                .orElse(null);

        resultPkg.derivedRating =
                PkgUserRatingAggregate.getByPkgAndRepository(
                        context,
                        pkg, pkgVersion.getRepositorySource().getRepository())
                .map(pkgUserRatingAggregate -> pkgUserRatingAggregate.getDerivedRating())
                .orElse(null);

        if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGICONS)) {
            resultPkg.pkgIcons = PkgIconImage.findForPkg(context, pkg)
                    .stream()
                    .map(pii -> new PkgIcon(
                            pii.getPkgIcon().getMediaType().getCode(),
                            pii.getPkgIcon().getSize()))
                    .collect(Collectors.toList());
        }

        if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGSCREENSHOTS)) {
            resultPkg.pkgScreenshots = pkgVersion.getPkg().getSortedPkgScreenshots()
                    .stream()
                    .map(this::createPkgScreenshot)
                    .collect(Collectors.toList());
        }

        if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGCATEGORIES)) {
            resultPkg.pkgCategoryCodes = pkgVersion.getPkg().getPkgPkgCategories()
                    .stream()
                    .map(pcc -> pcc.getPkgCategory().getCode())
                    .collect(Collectors.toList());
        }

        if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGCHANGELOG)) {
            Optional<PkgChangelog> pkgChangelogOptional = pkg.getPkgChangelog();

            if (pkgChangelogOptional.isPresent()) {
                resultPkg.pkgChangelogContent = Strings.emptyToNull(pkgChangelogOptional.get().getContent());
            }
        }

        switch(getBulkPkgRequest.versionType) {
            case LATEST:
            {
                GetBulkPkgResult.PkgVersion resultPkgVersion = createGetBulkPkgResultPkgVersion(
                        context,
                        pkgVersion,
                        naturalLanguage,
                        getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGVERSIONLOCALIZATIONDESCRIPTIONS)
                );

                resultPkg.versions = Collections.singletonList(resultPkgVersion);
            }
            break;

            case NONE: // no package version data available.
                break;

            default:
                throw new IllegalStateException("unsupported version type; "+getBulkPkgRequest.versionType.name());
        }

        return resultPkg;
    }

    @Override
    public GetBulkPkgResult getBulkPkg(final GetBulkPkgRequest getBulkPkgRequest) throws LimitExceededException, ObjectNotFoundException {
        Preconditions.checkNotNull(getBulkPkgRequest);
        Preconditions.checkState(null != getBulkPkgRequest.architectureCodes, "architecture codes must be non-null");
        Preconditions.checkArgument(null == getBulkPkgRequest.repositoryCodes || !getBulkPkgRequest.repositoryCodes.isEmpty(),
                "the repositories' codes must not be empty");
        Preconditions.checkNotNull(getBulkPkgRequest.pkgNames);

        if(getBulkPkgRequest.pkgNames.size() > GETBULKPKG_LIMIT) {
            throw new LimitExceededException();
        }

        final ObjectContext context = serverRuntime.getContext();
        final GetBulkPkgResult result = new GetBulkPkgResult();

        result.pkgs = Collections.emptyList();

        Set<Repository> repositories = new HashSet<>();

        if(null != getBulkPkgRequest.repositoryCodes) {
            repositories.addAll(transformCodesToRepositories(context,getBulkPkgRequest.repositoryCodes));
        }

        if(!getBulkPkgRequest.pkgNames.isEmpty() &&
                !getBulkPkgRequest.architectureCodes.isEmpty() &&
                !repositories.isEmpty()) {

            final NaturalLanguage naturalLanguage = getNaturalLanguage(context, getBulkPkgRequest.naturalLanguageCode);

            if(null==getBulkPkgRequest.filter) {
                getBulkPkgRequest.filter = Collections.emptyList();
            }

            // now search the data.
            PkgSearchSpecification searchSpecification = new PkgSearchSpecification();
            searchSpecification.setArchitectures(transformCodesToArchitectures(context, getBulkPkgRequest.architectureCodes));
            searchSpecification.setPkgNames(getBulkPkgRequest.pkgNames);
            searchSpecification.setNaturalLanguage(getNaturalLanguage(context, getBulkPkgRequest.naturalLanguageCode));
            searchSpecification.setRepositories(repositories);

            searchSpecification.setLimit(0);
            searchSpecification.setLimit(Integer.MAX_VALUE);

            long preFetchMs = System.currentTimeMillis();

            // TODO; cause the sub-data such as the package and the other subordinate data to be pre-fetched to avoid excessive small faults.

            final List<PkgVersion> pkgVersions = pkgService.search(context, searchSpecification);

            long postFetchMs = System.currentTimeMillis();

            // now return the data as necessary.
            result.pkgs = pkgVersions
                    .stream()
                    .map(pv -> createBulkPkgResultPkg(
                            context,
                            naturalLanguage,
                            getBulkPkgRequest,
                            pv))
                    .collect(Collectors.toList());

            LOGGER.debug(
                    "did search and find {} pkg versions for get bulk pkg; fetch in {}ms, marshall in {}ms",
                    pkgVersions.size(),
                    postFetchMs - preFetchMs,
                    System.currentTimeMillis() - postFetchMs);

        }

        return result;
    }

    @Override
    public UpdatePkgProminenceResult updatePkgProminence(UpdatePkgProminenceRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied on the request");
        Preconditions.checkArgument(null != request.prominenceOrdering, "the presence ordering must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code is required when updating a package prominence");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);
        Repository repository = getRepository(context, request.repositoryCode);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITPROMINENCE)) {
            throw new AuthorizationFailureException();
        }

        Optional<Prominence> prominenceOptional = Prominence.getByOrdering(context, request.prominenceOrdering);

        if(!prominenceOptional.isPresent()) {
            throw new ObjectNotFoundException(Prominence.class.getSimpleName(), request.prominenceOrdering);
        }

        PkgProminence pkgProminence = pkgOrchestrationService.ensurePkgProminence(
                context,
                pkg,
                repository);

        pkgProminence.setProminence(prominenceOptional.get());
        context.commitChanges();

        LOGGER.info("the prominence for {} has been set to; {}", pkg.toString(), prominenceOptional.get().toString());

        return new UpdatePkgProminenceResult();
    }

    @Override
    public QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null != request);
        return queueSimplePkgJob(
                QueuePkgCategoryCoverageExportSpreadsheetJobResult.class,
                PkgCategoryCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET);
    }

    @Override
    public QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(
            QueuePkgCategoryCoverageImportSpreadsheetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null != request, "the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.inputDataGuid), "the input data must be identified by guid");

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(
                context,
                user.orElse(null),
                null,
                Permission.BULK_PKGCATEGORYCOVERAGEIMPORTSPREADSHEET)) {
            LOGGER.warn("attempt to import package categories, but was not authorized");
            throw new AuthorizationFailureException();
        }

        // now check that the data is present.

        Optional<JobData> dataOptional = jobOrchestrationService.tryGetData(request.inputDataGuid);

        if(!dataOptional.isPresent()) {
            throw new ObjectNotFoundException(JobData.class.getSimpleName(), request.inputDataGuid);
        }

        // setup and go

        PkgCategoryCoverageImportSpreadsheetJobSpecification spec = new PkgCategoryCoverageImportSpreadsheetJobSpecification();
        spec.setOwnerUserNickname(user.get().getNickname());
        spec.setInputDataGuid(request.inputDataGuid);

        return new QueuePkgCategoryCoverageImportSpreadsheetJobResult(
                jobOrchestrationService.submit(spec, JobOrchestrationService.CoalesceMode.NONE).orElse(null));
    }

    @Override
    public QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null != request);
        return queueSimplePkgJob(
                QueuePkgIconSpreadsheetJobResult.class,
                PkgIconSpreadsheetJobSpecification.class,
                Permission.BULK_PKGICONSPREADSHEETREPORT);
    }

    @Override
    public QueuePkgProminenceAndUserRatingSpreadsheetJobResult queuePkgProminenceAndUserRatingSpreadsheetJob(QueuePkgProminenceAndUserRatingSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request);
        return queueSimplePkgJob(
                QueuePkgProminenceAndUserRatingSpreadsheetJobResult.class,
                PkgProminenceAndUserRatingSpreadsheetJobSpecification.class,
                Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT);
    }

    @Override
    public QueuePkgIconExportArchiveJobResult queuePkgIconExportArchiveJob(QueuePkgIconExportArchiveJobRequest request) {
        Preconditions.checkArgument(null!=request);
        return queueSimplePkgJob(
                QueuePkgIconExportArchiveJobResult.class,
                PkgIconExportArchiveJobSpecification.class,
                Permission.BULK_PKGICONEXPORTARCHIVE);
    }

    @Override
    public QueuePkgVersionPayloadLengthPopulationJobResult queuePkgVersionPayloadLengthPopulationJob(QueuePkgVersionPayloadLengthPopulationJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgVersionPayloadLengthPopulationJobResult.class,
                PkgVersionPayloadLengthPopulationJobSpecification.class,
                Permission.BULK_PKGVERSIONPAYLOADLENGTHPOPULATION);
    }

    @Override
    public QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult queuePkgVersionLocalizationCoverageExportSpreadsheetJob(QueuePkgVersionLocalizationCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgVersionLocalizationCoverageExportSpreadsheetJobResult.class,
                PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGVERSIONLOCALIZATIONCOVERAGEEXPORTSPREADSHEET);
    }

    @Override
    public QueuePkgLocalizationCoverageExportSpreadsheetJobResult queuePkgLocalizationCoverageExportSpreadsheetJob(QueuePkgLocalizationCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");
        return queueSimplePkgJob(
                QueuePkgLocalizationCoverageExportSpreadsheetJobResult.class,
                PkgLocalizationCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGLOCALIZATIONCOVERAGEEXPORTSPREADSHEET);
    }

    private <R extends AbstractQueueJobResult> R queueSimplePkgJob(
            Class<R> resultClass,
            Class<? extends AbstractJobSpecification> jobSpecificationClass,
            Permission permission) {

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if (!user.isPresent()) {
            LOGGER.warn("attempt to queue {} without a user", jobSpecificationClass.getSimpleName());
            throw new AuthorizationFailureException();
        }

        if (!authorizationService.check(context, user.get(), null, permission)) {
            LOGGER.warn("attempt to queue {} without sufficient authorization", jobSpecificationClass.getSimpleName());
            throw new AuthorizationFailureException();
        }

        AbstractJobSpecification spec;

        try {
            spec = jobSpecificationClass.newInstance();
        }
        catch(InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("unable to create the job specification for class; " + jobSpecificationClass.getSimpleName(), e);
        }

        spec.setOwnerUserNickname(user.get().getNickname());

        R result;

        try {
            result = resultClass.newInstance();
        }
        catch(InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("unable to create the result; " + resultClass.getSimpleName(), e);
        }

        result.guid = jobOrchestrationService.submit(spec,JobOrchestrationService.CoalesceMode.QUEUEDANDSTARTED).orElse(null);
        return result;
    }

    @Override
    public GetPkgChangelogResult getPkgChangelog(GetPkgChangelogRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied");



        ObjectContext context = serverRuntime.getContext();
        Optional<PkgChangelog> pkgChangelogOptional = getPkg(context, request.pkgName).getPkgChangelog();
        GetPkgChangelogResult result = new GetPkgChangelogResult();

        if(pkgChangelogOptional.isPresent()) {
            result.content = pkgChangelogOptional.get().getContent();
        }

        return result;
    }

    @Override
    public UpdatePkgChangelogResult updatePkgChangelog(UpdatePkgChangelogRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request, "a request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "a package name must be supplied");

        ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);
        Pkg pkg = getPkg(context, request.pkgName);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITCHANGELOG)) {
            throw new AuthorizationFailureException();
        }

        String newContent = request.content;

        if(null!=newContent) {
            newContent = Strings.emptyToNull(newContent.trim());
        }

        pkgOrchestrationService.updatePkgChangelog(context, pkg, newContent);
        context.commitChanges();

        return new UpdatePkgChangelogResult();
    }

    @Override
    public UpdatePkgVersionResult updatePkgVersion(UpdatePkgVersionRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request, "the request object must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.pkgName), "the package name must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.repositoryCode), "the repository code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.architectureCode), "the architecture code must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.major), "the version major must be supplied");

        ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);
        Pkg pkg = getPkg(context, request.pkgName);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITVERSION)) {
            throw new AuthorizationFailureException();
        }

        Optional<Repository> repositoryOptional = Repository.getByCode(context, request.repositoryCode);

        if(!repositoryOptional.isPresent()) {
            throw new ObjectNotFoundException(Repository.class.getSimpleName(), request.repositoryCode);
        }

        Optional<Architecture> architectureOptional = Architecture.getByCode(context, request.architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new ObjectNotFoundException(Architecture.class.getSimpleName(), request.architectureCode);
        }

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context, pkg, repositoryOptional.get(), architectureOptional.get(),
                new VersionCoordinates(request.major, request.minor, request.micro, request.preRelease, request.revision)
        );

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), null);
        }

        PkgVersion pkgVersion = pkgVersionOptional.get();

        for(UpdatePkgVersionRequest.Filter filter : request.filter) {

            switch(filter) {

                case ACTIVE:
                    LOGGER.info("will update the package version active flag to {} for {}", request.active, pkgVersion.toString());
                    pkgVersion.setActive(request.active);
                    pkgOrchestrationService.adjustLatest(context, pkgVersion.getPkg(), pkgVersion.getArchitecture());
                    break;

            }

        }

        context.commitChanges();

        return new UpdatePkgVersionResult();
    }


}
