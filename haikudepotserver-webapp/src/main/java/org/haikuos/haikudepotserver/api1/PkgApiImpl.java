/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.googlecode.jsonrpc4j.Base64;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.map.Entity;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.haikuos.haikudepotserver.api1.model.AbstractQueueJobResult;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.LimitExceededException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.dataobjects.PkgLocalization;
import org.haikuos.haikudepotserver.dataobjects.PkgScreenshot;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.PkgVersionUrl;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobData;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.*;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.VersionCoordinatesComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
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

    /**
     * <p>This method will convert the supplied codes into a list of architectures.  It requires that at least one
     * architecture is supplied.  If any architecture code is not able to be converted into an architecture, it
     * will throw {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     */

    private List<Architecture> transformCodesToArchitectures(final ObjectContext context, List<String> codes) throws ObjectNotFoundException {
        Preconditions.checkState(null != codes && !codes.isEmpty(), "the architecture codes must be supplied and at least one architecture is required");

        List<Architecture> result = Lists.newArrayList();

        //noinspection ConstantConditions
        for (String code : codes) {
            Optional<Architecture> architectureOptional = Architecture.getByCode(context, code);

            if (!architectureOptional.isPresent()) {
                throw new ObjectNotFoundException(Architecture.class.getSimpleName(), code);
            }

            result.add(architectureOptional.get());
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

        List<PkgCategory> pkgCategories = Lists.newArrayList(
                PkgCategory.getByCodes(context, updatePkgCategoriesRequest.pkgCategoryCodes));

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
        Preconditions.checkState(null!=request.architectureCodes && !request.architectureCodes.isEmpty(),"architecture codes must be supplied and at least one is required");
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));
        Preconditions.checkNotNull(request.limit);
        Preconditions.checkState(request.limit > 0);

        if(null==request.sortOrdering) {
            request.sortOrdering = SearchPkgsRequest.SortOrdering.NAME;
        }

        final ObjectContext context = serverRuntime.getContext();

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
        specification.setLimit(request.limit);
        specification.setOffset(request.offset);

        SearchPkgsResult result = new SearchPkgsResult();

        List<PkgVersion> searchedPkgVersions = pkgService.search(context,specification,null);

        // if there are more than we asked for then there must be more available.

        result.total = pkgService.total(context, specification);
        result.items = Lists.newArrayList(Iterables.transform(
                searchedPkgVersions,
                new Function<PkgVersion, SearchPkgsResult.Pkg>() {
                    @Override
                    public SearchPkgsResult.Pkg apply(org.haikuos.haikudepotserver.dataobjects.PkgVersion input) {

                        SearchPkgsResult.Pkg resultPkg = new SearchPkgsResult.Pkg();
                        resultPkg.name = input.getPkg().getName();
                        resultPkg.modifyTimestamp = input.getPkg().getModifyTimestamp().getTime();
                        resultPkg.derivedRating = input.getPkg().getDerivedRating();
                        resultPkg.hasAnyPkgIcons = !input.getPkg().getPkgIcons().isEmpty();

                        Optional<PkgLocalization> pkgLocalizationOptional = PkgLocalization.getForPkgAndNaturalLanguageCode(
                                context,
                                input.getPkg(),
                                request.naturalLanguageCode);

                        if(!pkgLocalizationOptional.isPresent()) {
                            pkgLocalizationOptional = PkgLocalization.getForPkgAndNaturalLanguageCode(
                                    context,
                                    input.getPkg(),
                                    NaturalLanguage.CODE_ENGLISH);
                        }

                        resultPkg.title = pkgLocalizationOptional.isPresent() ? pkgLocalizationOptional.get().getTitle() : null;

                        SearchPkgsResult.PkgVersion resultVersion = new SearchPkgsResult.PkgVersion();
                        resultVersion.major = input.getMajor();
                        resultVersion.minor = input.getMinor();
                        resultVersion.micro = input.getMicro();
                        resultVersion.preRelease = input.getPreRelease();
                        resultVersion.revision = input.getRevision();
                        resultVersion.createTimestamp = input.getCreateTimestamp().getTime();
                        resultVersion.viewCounter = input.getViewCounter();
                        resultVersion.architectureCode = input.getArchitecture().getCode();
                        resultVersion.payloadLength = input.getPayloadLength();

                        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = input.getPkgVersionLocalization(request.naturalLanguageCode);

                        if(!pkgVersionLocalizationOptional.isPresent()) {
                            pkgVersionLocalizationOptional = input.getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);
                        }

                        if(pkgVersionLocalizationOptional.isPresent()) {
                            resultVersion.summary = pkgVersionLocalizationOptional.get().getSummary();
                        }

                        resultPkg.versions = Collections.singletonList(resultVersion);

                        return resultPkg;
                    }
                }
        ));

        LOGGER.info("search for pkgs found {} results", result.items.size());

        return result;
    }

    /**
     * <p>Given the persistence model object, this method will construct the DTO to be sent back over the wire.</p>
     */

    private GetPkgResult.PkgVersion createGetPkgResultPkgVersion(PkgVersion pkgVersion, NaturalLanguage naturalLanguage) {
        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);

        GetPkgResult.PkgVersion version = new GetPkgResult.PkgVersion();

        version.isLatest = pkgVersion.getIsLatest();

        version.major = pkgVersion.getMajor();
        version.minor = pkgVersion.getMinor();
        version.micro = pkgVersion.getMicro();
        version.revision = pkgVersion.getRevision();
        version.preRelease = pkgVersion.getPreRelease();

        version.payloadLength = pkgVersion.getPayloadLength();
        version.repositoryCode = pkgVersion.getRepository().getCode();
        version.architectureCode = pkgVersion.getArchitecture().getCode();
        version.copyrights = Lists.transform(
                pkgVersion.getPkgVersionCopyrights(),
                new Function<PkgVersionCopyright, String>() {
                    @Override
                    public String apply(org.haikuos.haikudepotserver.dataobjects.PkgVersionCopyright input) {
                        return input.getBody();
                    }
                });

        version.licenses = Lists.transform(
                pkgVersion.getPkgVersionLicenses(),
                new Function<PkgVersionLicense, String>() {
                    @Override
                    public String apply(org.haikuos.haikudepotserver.dataobjects.PkgVersionLicense input) {
                        return input.getBody();
                    }
                });

        version.viewCounter = pkgVersion.getViewCounter();

        org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization pkgVersionLocalization = pkgVersion.getPkgVersionLocalizationOrFallback(naturalLanguage);

        version.description = pkgVersionLocalization.getDescription();
        version.summary = pkgVersionLocalization.getSummary();
        version.naturalLanguageCode = pkgVersionLocalization.getNaturalLanguage().getCode();

        version.urls = Lists.transform(
                pkgVersion.getPkgVersionUrls(),
                new Function<PkgVersionUrl, org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionUrl>() {
                    @Override
                    public org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionUrl apply(PkgVersionUrl input) {
                        org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionUrl url = new org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionUrl();
                        url.url = input.getUrl();
                        url.urlTypeCode = input.getPkgUrlType().getCode();
                        return url;
                    }
                });

        return version;
    }

    /**
     * <p>This method will bump the view counter for the package version.  It will also try to prevent
     * a user from the same client (IP) from doing this more than once within a reasonable stand-down
     * time.  This is prone to optimistic locking failure because lots of people can look at the same
     * package at the same time.  For this reason, it will try to load the data into a different
     * {@link org.apache.cayenne.ObjectContext} to edit.</p>
     */

    private void incrementCounter(final ObjectContext context, PkgVersion pkgVersion) {
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

        final ObjectContext context = serverRuntime.getContext();

        Optional<Architecture> architectureOptional = Optional.absent();

        if(!Strings.isNullOrEmpty(request.architectureCode)) {
            architectureOptional = Architecture.getByCode(context, request.architectureCode);
        }

        final NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        GetPkgResult result = new GetPkgResult();
        Pkg pkg = getPkg(context, request.name);

        result.name = pkg.getName();
        result.modifyTimestamp = pkg.getModifyTimestamp().getTime();
        result.derivedRating = pkg.getDerivedRating();
        result.derivedRatingSampleSize = pkg.getDerivedRatingSampleSize();
        result.prominenceOrdering = pkg.getProminence().getOrdering();
        result.pkgCategoryCodes = Lists.transform(pkg.getPkgPkgCategories(), new Function<PkgPkgCategory, String>() {
            @Override
            public String apply(PkgPkgCategory input) {
                return input.getPkgCategory().getCode();
            }
        });

        Optional<PkgLocalization> pkgLocalizationOptional = PkgLocalization.getForPkgAndNaturalLanguageCode(context, pkg, request.naturalLanguageCode);

        if(!pkgLocalizationOptional.isPresent()) {
            pkgLocalizationOptional = PkgLocalization.getForPkgAndNaturalLanguageCode(context, pkg, NaturalLanguage.CODE_ENGLISH);
        }

        result.title = pkgLocalizationOptional.isPresent() ? pkgLocalizationOptional.get().getTitle() : null;

        switch(request.versionType) {

            // might be used to show a history of the versions.  If an architecture is present then it will
            // only return versions for that architecture.  If no architecture is present then it will return
            // versions for all architectures.

            case ALL: {

                List<PkgVersion> allVersions = PkgVersion.getForPkg(context, pkg);

                if(architectureOptional.isPresent()) {
                    final Architecture a = architectureOptional.get();

                    allVersions = Lists.newArrayList(Iterables.filter(
                            allVersions,
                            new Predicate<PkgVersion>() {
                                @Override
                                public boolean apply(PkgVersion pkgVersion) {
                                    return pkgVersion.getArchitecture().equals(a);
                                }
                            }
                    ));
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

                result.versions = Lists.transform(allVersions, new Function<PkgVersion, GetPkgResult.PkgVersion>() {
                    @Override
                    public GetPkgResult.PkgVersion apply(PkgVersion pkgVersion) {
                        return createGetPkgResultPkgVersion(pkgVersion,naturalLanguage);
                    }
                });

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
                        context,
                        pkg,
                        architectureOptional.get(),
                        coordinates);

                if (!pkgVersionOptional.isPresent()) {
                    throw new ObjectNotFoundException(
                            PkgVersion.class.getSimpleName(),
                            "");
                }

                if (null != request.incrementViewCounter && request.incrementViewCounter) {
                    incrementCounter(context, pkgVersionOptional.get());
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
                        pkgVersionOptional.get(),
                        naturalLanguage));
            }
            break;

            case LATEST: {
                if (!architectureOptional.isPresent()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; " + request.architectureCode);
                }

                Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                        context,
                        pkg,
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
                    incrementCounter(context, pkgVersionOptional.get());
                }

                result.versions = Collections.singletonList(createGetPkgResultPkgVersion(
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

        return Iterables.tryFind(pkgIconApis, new Predicate<ConfigurePkgIconRequest.PkgIcon>() {
            @Override
            public boolean apply(ConfigurePkgIconRequest.PkgIcon input) {
                return input.mediaTypeCode.equals(mediaTypeCode) && (null!=input.size) && (input.size.equals(size));
            }
        }).isPresent();

    }

    @Override
    public GetPkgIconsResult getPkgIcons(GetPkgIconsRequest request) throws ObjectNotFoundException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);

        GetPkgIconsResult result = new GetPkgIconsResult();
        result.pkgIcons = Lists.transform(
                pkg.getPkgIcons(),
                new Function<org.haikuos.haikudepotserver.dataobjects.PkgIcon, org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon>() {
                    @Override
                    public org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon apply(org.haikuos.haikudepotserver.dataobjects.PkgIcon input) {
                        return new org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon(
                                input.getMediaType().getCode(),
                                input.getSize());
                    }
                }
        );

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

        Set<org.haikuos.haikudepotserver.dataobjects.PkgIcon> createdOrUpdatedPkgIcons = Sets.newHashSet();

        if(null!=request.pkgIcons && !request.pkgIcons.isEmpty()) {

            if(
                    !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 16)
                            || !contains(request.pkgIcons, com.google.common.net.MediaType.PNG.toString(), 32)) {
                throw new IllegalStateException("pkg icons must contain a 16x16px and 32x32px png icon variant");
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
                    byte[] data = Base64.decode(pkgIconApi.dataBase64);

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
                catch(org.haikuos.haikudepotserver.pkg.model.BadPkgIconException bpie) {
                    throw new BadPkgIconException(pkgIconApi.mediaTypeCode, pkgIconApi.size, bpie);
                }

            }

        }

        // now we have some icons stored which may not be in the replacement data; we should remove those ones.

        for(org.haikuos.haikudepotserver.dataobjects.PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
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

        for(org.haikuos.haikudepotserver.dataobjects.PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
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

    private org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot createPkgScreenshot(PkgScreenshot pkgScreenshot) {
        Preconditions.checkNotNull(pkgScreenshot);
        org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot rs = new org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot();
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
        result.items = Lists.transform(
                pkg.getSortedPkgScreenshots(),
                new Function<PkgScreenshot, org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot>() {
                    @Override
                    public org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot apply(PkgScreenshot pkgScreenshot) {
                        return createPkgScreenshot(pkgScreenshot);
                    }
                }
        );

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

        Preconditions.checkArgument(null!=updatePkgLocalizationRequest);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(updatePkgLocalizationRequest.pkgName), "the package name must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, updatePkgLocalizationRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITLOCALIZATION)) {
            throw new AuthorizationFailureException();
        }

        for(org.haikuos.haikudepotserver.api1.model.pkg.PkgLocalization requestPkgVersionLocalization : updatePkgLocalizationRequest.pkgLocalizations) {

            NaturalLanguage naturalLanguage = getNaturalLanguage(context, requestPkgVersionLocalization.naturalLanguageCode);

            pkgService.updatePkgLocalization(
                    context,
                    pkg,
                    naturalLanguage,
                    requestPkgVersionLocalization.title);
        }

        context.commitChanges();

        LOGGER.info(
                "did update the localization for pkg {} for {} natural languages",
                pkg.getName(),
                updatePkgLocalizationRequest.pkgLocalizations.size()
        );

        // the pkg localization relies on a query caches; this will remove those caches by
        // cache group.

        serverRuntime.getDataDomain().getQueryCache().removeGroup(HaikuDepot.CacheGroup.PKG_LOCALIZATION.name());

        return new UpdatePkgLocalizationResult();
    }

    @Override
    public UpdatePkgVersionLocalizationsResult updatePkgVersionLocalization(
            UpdatePkgVersionLocalizationsRequest updatePkgVersionLocalizationRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(updatePkgVersionLocalizationRequest);
        Preconditions.checkNotNull(updatePkgVersionLocalizationRequest.pkgVersionLocalizations);
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgVersionLocalizationRequest.pkgName));
        Preconditions.checkNotNull(updatePkgVersionLocalizationRequest.replicateToOtherArchitecturesWithSameEnglishContent);

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, updatePkgVersionLocalizationRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITVERSIONLOCALIZATION)) {
            throw new AuthorizationFailureException();
        }

        Architecture architecture = getArchitecture(context, updatePkgVersionLocalizationRequest.architectureCode);

        Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                context,
                pkg,
                Collections.singletonList(architecture));

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        for(org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionLocalization requestPkgVersionLocalization : updatePkgVersionLocalizationRequest.pkgVersionLocalizations) {
            if(requestPkgVersionLocalization.naturalLanguageCode.equals(NaturalLanguage.CODE_ENGLISH)) {
                throw new IllegalStateException("the natural language to update is not allowed to be english because this is captured from the hpkr file.");
            }
        }

        for(org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionLocalization requestPkgVersionLocalization : updatePkgVersionLocalizationRequest.pkgVersionLocalizations) {

            NaturalLanguage naturalLanguage = getNaturalLanguage(context, requestPkgVersionLocalization.naturalLanguageCode);

            pkgService.updatePkgVersionLocalization(
                    context,
                    pkgVersionOptional.get(),
                    naturalLanguage,
                    requestPkgVersionLocalization.summary,
                    requestPkgVersionLocalization.description);

            if(updatePkgVersionLocalizationRequest.replicateToOtherArchitecturesWithSameEnglishContent) {

                for(Architecture architectureToCopyTo : Architecture.getAll(context)) {

                    if(!architectureToCopyTo.equals(architecture)) { // don't copy the source to the destination.

                        Optional<PkgVersion> pkgVersionOptionalToCopyTo = PkgVersion.getForPkg(
                                context,
                                pkg,
                                architectureToCopyTo,
                                pkgVersionOptional.get().toVersionCoordinates());

                        if(pkgVersionOptionalToCopyTo.isPresent()) {
                            pkgService.replicateLocalizationIfEnglishMatches(
                                    context,
                                    pkgVersionOptional.get(),
                                    pkgVersionOptionalToCopyTo.get(),
                                    Collections.singletonList(naturalLanguage),
                                    true // override any destination localization already present
                            );
                        }
                    }
                }
            }
        }

        context.commitChanges();

        LOGGER.info(
                "did update the localization for pkg {} in architecture {} for {} natural languages",
                pkg.getName(),
                architecture.getCode(),
                updatePkgVersionLocalizationRequest.pkgVersionLocalizations.size()
        );

        return new UpdatePkgVersionLocalizationsResult();
    }

    @Override
    public GetPkgLocalizationsResult getPkgLocalizations(GetPkgLocalizationsRequest getPkgLocalizationsRequest) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=getPkgLocalizationsRequest, "a request is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(getPkgLocalizationsRequest.pkgName), "a package name is required");
        Preconditions.checkArgument(null!=getPkgLocalizationsRequest.naturalLanguageCodes, "the natural language codes must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, getPkgLocalizationsRequest.pkgName);

        GetPkgLocalizationsResult result = new GetPkgLocalizationsResult();
        result.pkgLocalizations = Lists.newArrayList();
        List<PkgLocalization> pkgLocalizations = PkgLocalization.findForPkg(context, pkg);

        for(PkgLocalization pkgLocalization : pkgLocalizations) {
            if(getPkgLocalizationsRequest.naturalLanguageCodes.contains(pkgLocalization.getNaturalLanguage().getCode())) {
                org.haikuos.haikudepotserver.api1.model.pkg.PkgLocalization resultPkgVersionLocalization = new org.haikuos.haikudepotserver.api1.model.pkg.PkgLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = pkgLocalization.getNaturalLanguage().getCode();
                resultPkgVersionLocalization.title = pkgLocalization.getTitle();
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

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, getPkgVersionLocalizationsRequest.pkgName);
        Architecture architecture = getArchitecture(context, getPkgVersionLocalizationsRequest.architectureCode);
        Optional<PkgVersion> pkgVersionOptional;

        if(null==getPkgVersionLocalizationsRequest.major) {
            pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                    context,
                    pkg,
                    Collections.singletonList(architecture));
        }
        else {
            pkgVersionOptional = PkgVersion.getForPkg(
                    context,
                    pkg,
                    architecture,
                    new VersionCoordinates(
                            getPkgVersionLocalizationsRequest.major,
                            getPkgVersionLocalizationsRequest.minor,
                            getPkgVersionLocalizationsRequest.micro,
                            getPkgVersionLocalizationsRequest.preRelease,
                            getPkgVersionLocalizationsRequest.revision));
        }

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        GetPkgVersionLocalizationsResult result = new GetPkgVersionLocalizationsResult();
        result.pkgVersionLocalizations = Lists.newArrayList();

        for(String naturalLanguageCode : getPkgVersionLocalizationsRequest.naturalLanguageCodes) {
            Optional<org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguageCode);

            if(pkgVersionLocalizationOptional.isPresent()) {
                org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionLocalization resultPkgVersionLocalization = new org.haikuos.haikudepotserver.api1.model.pkg.PkgVersionLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = naturalLanguageCode;
                resultPkgVersionLocalization.description = pkgVersionLocalizationOptional.get().getDescription();
                resultPkgVersionLocalization.summary = pkgVersionLocalizationOptional.get().getSummary();
                result.pkgVersionLocalizations.add(resultPkgVersionLocalization);
            }
        }

        return result;
    }

    private GetBulkPkgResult.PkgVersion createGetBulkPkgResultPkgVersion(
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            boolean includeDescription) {

        Preconditions.checkNotNull(pkgVersion);
        Preconditions.checkNotNull(naturalLanguage);

        GetBulkPkgResult.PkgVersion version = new GetBulkPkgResult.PkgVersion();

        version.major = pkgVersion.getMajor();
        version.minor = pkgVersion.getMinor();
        version.micro = pkgVersion.getMicro();
        version.revision = pkgVersion.getRevision();
        version.preRelease = pkgVersion.getPreRelease();
        version.architectureCode = pkgVersion.getArchitecture().getCode();
        version.payloadLength = pkgVersion.getPayloadLength();

        org.haikuos.haikudepotserver.dataobjects.PkgVersionLocalization pkgVersionLocalization = pkgVersion.getPkgVersionLocalizationOrFallback(naturalLanguage);

        if(includeDescription) {
            version.description = pkgVersionLocalization.getDescription();
        }

        version.summary = pkgVersionLocalization.getSummary();
        version.naturalLanguageCode = pkgVersionLocalization.getNaturalLanguage().getCode();

        return version;

    }

    @Override
    public GetBulkPkgResult getBulkPkg(final GetBulkPkgRequest getBulkPkgRequest) throws LimitExceededException, ObjectNotFoundException {
        Preconditions.checkNotNull(getBulkPkgRequest);
        Preconditions.checkState(null != getBulkPkgRequest.architectureCodes, "architecture codes must be non-null");
        Preconditions.checkNotNull(getBulkPkgRequest.pkgNames);

        if(getBulkPkgRequest.pkgNames.size() > GETBULKPKG_LIMIT) {
            throw new LimitExceededException();
        }

        final GetBulkPkgResult result = new GetBulkPkgResult();

        if(getBulkPkgRequest.pkgNames.isEmpty() || getBulkPkgRequest.architectureCodes.isEmpty()) {
            result.pkgs = Collections.emptyList();
        }
        else {

            final ObjectContext context = serverRuntime.getContext();
            final NaturalLanguage naturalLanguage = getNaturalLanguage(context, getBulkPkgRequest.naturalLanguageCode);

            if(null==getBulkPkgRequest.filter) {
                getBulkPkgRequest.filter = Collections.emptyList();
            }

            // use a pre-fetch tree in order to optimize the haul of data back into the application server from
            // the database depending on what is being asked for.

            PrefetchTreeNode prefetchTreeNode = new PrefetchTreeNode();

            prefetchTreeNode.addPath(PkgVersion.PKG_VERSION_LOCALIZATIONS_PROPERTY);

            for(GetBulkPkgRequest.Filter filter : getBulkPkgRequest.filter) {
                switch(filter) {
                    case PKGSCREENSHOTS:
                        prefetchTreeNode.addPath(Joiner.on(Entity.PATH_SEPARATOR).join(
                                ImmutableList.of(PkgVersion.PKG_PROPERTY, Pkg.PKG_SCREENSHOTS_PROPERTY)));
                        break;

                    case PKGCATEGORIES:
                        prefetchTreeNode.addPath(Joiner.on(Entity.PATH_SEPARATOR).join(
                                ImmutableList.of(PkgVersion.PKG_PROPERTY, Pkg.PKG_PKG_CATEGORIES_PROPERTY)));
                        break;

                    case PKGICONS:
                        prefetchTreeNode.addPath(Joiner.on(Entity.PATH_SEPARATOR).join(
                                ImmutableList.of(PkgVersion.PKG_PROPERTY, Pkg.PKG_ICONS_PROPERTY)));
                        break;
                }
            }

            // now search the data.
            PkgSearchSpecification searchSpecification = new PkgSearchSpecification();
            searchSpecification.setArchitectures(transformCodesToArchitectures(context, getBulkPkgRequest.architectureCodes));
            searchSpecification.setPkgNames(getBulkPkgRequest.pkgNames);
            searchSpecification.setNaturalLanguage(getNaturalLanguage(context, getBulkPkgRequest.naturalLanguageCode));
            searchSpecification.setLimit(0);
            searchSpecification.setLimit(Integer.MAX_VALUE);

            long preFetchMs = System.currentTimeMillis();
            final List<PkgVersion> pkgVersions = pkgService.search(context, searchSpecification, prefetchTreeNode);
            final List<PkgLocalization> pkgLocalizations = PkgLocalization.findForPkgs(context, Lists.transform(
                    pkgVersions,
                    new Function<PkgVersion, Pkg>() {
                        @Override
                        public Pkg apply(PkgVersion input) {
                            return input.getPkg();
                        }
                    }
            ));
            long postFetchMs = System.currentTimeMillis();

            // now return the data as necessary.
            result.pkgs = Lists.transform(
                    pkgVersions,
                    new Function<PkgVersion, GetBulkPkgResult.Pkg>() {
                        @Override
                        public GetBulkPkgResult.Pkg apply(final PkgVersion input) {

                            GetBulkPkgResult.Pkg resultPkg = new GetBulkPkgResult.Pkg();
                            resultPkg.modifyTimestamp = input.getPkg().getModifyTimestamp().getTime();
                            resultPkg.name = input.getPkg().getName();
                            resultPkg.prominenceOrdering = input.getPkg().getProminence().getOrdering();
                            resultPkg.derivedRating = input.getPkg().getDerivedRating();

                            Optional<PkgLocalization> pkgLocalizationOptional = Iterables.tryFind(
                                    pkgLocalizations,
                                    new Predicate<PkgLocalization>() {
                                        @Override
                                        public boolean apply(PkgLocalization plInput) {
                                            return plInput.getNaturalLanguage().getCode().equals(getBulkPkgRequest.naturalLanguageCode) && plInput.getPkg().equals(input.getPkg());
                                        }
                                    }
                            );

                            if(!pkgLocalizationOptional.isPresent()) {
                                pkgLocalizationOptional = Iterables.tryFind(
                                        pkgLocalizations,
                                        new Predicate<PkgLocalization>() {
                                            @Override
                                            public boolean apply(PkgLocalization plInput) {
                                                return plInput.getNaturalLanguage().getCode().equals(NaturalLanguage.CODE_ENGLISH) && plInput.getPkg().equals(input.getPkg());
                                            }
                                        }
                                );
                            }

                            resultPkg.title = pkgLocalizationOptional.isPresent() ? pkgLocalizationOptional.get().getTitle() : null;

                            if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGICONS)) {
                                resultPkg.pkgIcons = Lists.transform(
                                        input.getPkg().getPkgIcons(),
                                        new Function<org.haikuos.haikudepotserver.dataobjects.PkgIcon, org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon>() {
                                            @Override
                                            public org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon apply(org.haikuos.haikudepotserver.dataobjects.PkgIcon pkgIcon) {
                                                return new org.haikuos.haikudepotserver.api1.model.pkg.PkgIcon(
                                                        pkgIcon.getMediaType().getCode(),
                                                        pkgIcon.getSize());
                                            }
                                        }
                                );
                            }

                            if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGSCREENSHOTS)) {
                                resultPkg.pkgScreenshots = Lists.transform(
                                        input.getPkg().getSortedPkgScreenshots(),
                                        new Function<PkgScreenshot, org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot>() {
                                            @Override
                                            public org.haikuos.haikudepotserver.api1.model.pkg.PkgScreenshot apply(PkgScreenshot pkgScreenshot) {
                                                return createPkgScreenshot(pkgScreenshot);
                                            }
                                        }
                                );
                            }

                            if(getBulkPkgRequest.filter.contains(GetBulkPkgRequest.Filter.PKGCATEGORIES)) {
                                resultPkg.pkgCategoryCodes = Lists.transform(
                                        input.getPkg().getPkgPkgCategories(),
                                        new Function<PkgPkgCategory, String>() {
                                            @Override
                                            public String apply(PkgPkgCategory input) {
                                                return input.getPkgCategory().getCode();
                                            }
                                        }
                                );
                            }

                            resultPkg.derivedRating = input.getPkg().getDerivedRating();

                            switch(getBulkPkgRequest.versionType) {
                                case LATEST:
                                {
                                    GetBulkPkgResult.PkgVersion resultPkgVersion = createGetBulkPkgResultPkgVersion(
                                            input,
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
                    }
            );

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
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName),"the package name must be supplied on the request");
        Preconditions.checkState(null!=request.prominenceOrdering,"the presence ordering must be supplied");

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, request.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITPROMINENCE)) {
            throw new AuthorizationFailureException();
        }

        Optional<Prominence> prominenceOptional = Prominence.getByOrdering(context, request.prominenceOrdering);

        if(!prominenceOptional.isPresent()) {
            throw new ObjectNotFoundException(Prominence.class.getSimpleName(), request.prominenceOrdering);
        }

        pkg.setProminence(prominenceOptional.get());
        context.commitChanges();

        LOGGER.info("the prominence for {} has been set to; {}", pkg.toString(), prominenceOptional.get().toString());

        return new UpdatePkgProminenceResult();
    }

    @Override
    public QueuePkgCategoryCoverageExportSpreadsheetJobResult queuePkgCategoryCoverageExportSpreadsheetJob(QueuePkgCategoryCoverageExportSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request);
        return queueSimplePkgJob(
                QueuePkgCategoryCoverageExportSpreadsheetJobResult.class,
                PkgCategoryCoverageExportSpreadsheetJobSpecification.class,
                Permission.BULK_PKGCATEGORYCOVERAGEEXPORTSPREADSHEET);
    }

    @Override
    public QueuePkgCategoryCoverageImportSpreadsheetJobResult queuePkgCategoryCoverageImportSpreadsheetJob(
            QueuePkgCategoryCoverageImportSpreadsheetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request,"the request must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.inputDataGuid), "the input data must be identified by guid");

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(
                context,
                user.orNull(),
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
                jobOrchestrationService.submit(spec, JobOrchestrationService.CoalesceMode.NONE).orNull());
    }

    @Override
    public QueuePkgIconSpreadsheetJobResult queuePkgIconSpreadsheetJob(QueuePkgIconSpreadsheetJobRequest request) {
        Preconditions.checkArgument(null!=request);
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

        result.guid = jobOrchestrationService.submit(spec,JobOrchestrationService.CoalesceMode.QUEUEDANDSTARTED).orNull();
        return result;
    }


}
