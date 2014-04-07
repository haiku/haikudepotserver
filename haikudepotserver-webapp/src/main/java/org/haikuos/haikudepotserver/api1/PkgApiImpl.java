/*
 * Copyright 2013-2014, Andrew Lindesay
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
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    public final static int PKGPKGCATEGORIES_MAX = 3;

    protected static Logger logger = LoggerFactory.getLogger(PkgApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    @Resource
    PkgOrchestrationService pkgService;

    @Value("${pkgversion.viewcounter.protectrecurringincrementfromsameclient:true}")
    Boolean shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient;

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

    private Architecture getArchitecture(ObjectContext context, String architectureCode) throws ObjectNotFoundException {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(architectureCode));

        Optional<Architecture> architectureOptional = Architecture.getByCode(context,architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new ObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode);
        }

        return architectureOptional.get();
    }

    private NaturalLanguage getNaturalLanguage(ObjectContext context, String naturalLanguageCode) throws ObjectNotFoundException  {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));

        Optional<NaturalLanguage> naturalLanguageOptional = NaturalLanguage.getByCode(context, naturalLanguageCode);

        if(!naturalLanguageOptional.isPresent()) {
            throw new ObjectNotFoundException(NaturalLanguage.class.getSimpleName(), naturalLanguageCode);
        }

        return naturalLanguageOptional.get();
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
            logger.warn("attempt to configure the categories for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        List<PkgCategory> pkgCategories = Lists.newArrayList(
                PkgCategory.getByCodes(context, updatePkgCategoriesRequest.pkgCategoryCodes));

        if(pkgCategories.size() != updatePkgCategoriesRequest.pkgCategoryCodes.size()) {
            logger.warn(
                    "request for {} categories yielded only {}; must be a code mismatch",
                    updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                    pkgCategories.size());

            throw new ObjectNotFoundException(PkgCategory.class.getSimpleName(), null);
        }

        // now go through and delete any of those pkg relationships to packages that are already present
        // and which are no longer required.  Also remove those that we already have from the list.

        for(PkgPkgCategory pkgPkgCategory : ImmutableList.copyOf(pkg.getPkgPkgCategories())) {
            if(!pkgCategories.contains(pkgPkgCategory.getPkgCategory())) {
                pkg.removeToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
                context.deleteObjects(pkgPkgCategory);
            }
            else {
                pkgCategories.remove(pkgPkgCategory.getPkgCategory());
            }
        }

        // now any remaining in the pkgCategories will need to be added to the pkg.

        for(PkgCategory pkgCategory : pkgCategories) {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            pkgPkgCategory.setPkgCategory(pkgCategory);
            pkg.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
        }

        // now save and finish.

        pkg.setModifyTimestamp();

        context.commitChanges();

        logger.info(
                "did configure {} categories for pkg {}",
                new Object[] {
                        updatePkgCategoriesRequest.pkgCategoryCodes.size(),
                        pkg.getName(),
                }
        );

        return new UpdatePkgCategoriesResult();
    }

    @Override
    public SearchPkgsResult searchPkgs(SearchPkgsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.architectureCode));

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

        specification.setDaysSinceLatestVersion(request.daysSinceLatestVersion);
        specification.setSortOrdering(PkgSearchSpecification.SortOrdering.valueOf(request.sortOrdering.name()));

        final Optional<Architecture> architectureOptional = Architecture.getByCode(context,request.architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new IllegalStateException("the architecture specified is not able to be found; "+request.architectureCode);
        }

        specification.setArchitecture(architectureOptional.get());

        specification.setLimit(request.limit+1); // get +1 to see if there are any more.
        specification.setOffset(request.offset);

        SearchPkgsResult result = new SearchPkgsResult();
        List<PkgVersion> searchedPkgVersions = pkgService.search(context,specification);

        // if there are more than we asked for then there must be more available.

        result.hasMore = searchedPkgVersions.size() > request.limit;

        if(result.hasMore) {
            searchedPkgVersions = searchedPkgVersions.subList(0,request.limit);
        }

        result.items = Lists.newArrayList(Iterables.transform(
                searchedPkgVersions,
                new Function<PkgVersion, SearchPkgsResult.Pkg>() {
                    @Override
                    public SearchPkgsResult.Pkg apply(org.haikuos.haikudepotserver.dataobjects.PkgVersion input) {

                        SearchPkgsResult.Pkg resultPkg = new SearchPkgsResult.Pkg();
                        resultPkg.name = input.getPkg().getName();
                        resultPkg.modifyTimestamp = input.getPkg().getModifyTimestamp().getTime();

                        SearchPkgsResult.Version resultVersion = new SearchPkgsResult.Version();
                        resultVersion.major = input.getMajor();
                        resultVersion.minor = input.getMinor();
                        resultVersion.micro = input.getMicro();
                        resultVersion.preRelease = input.getPreRelease();
                        resultVersion.revision = input.getRevision();
                        resultVersion.createTimestamp = input.getCreateTimestamp().getTime();
                        resultVersion.viewCounter = input.getViewCounter();

                        resultPkg.version = resultVersion;

                        return resultPkg;
                    }
                }
        ));

        logger.info("search for pkgs found {} results", result.items.size());

        return result;
    }

    /**
     * <p>Given the persistence model object, this method will construct the DTO to be sent back over the wire.</p>
     */

    private GetPkgResult.Version createVersion(PkgVersion pkgVersion, NaturalLanguage naturalLanguage) {
        GetPkgResult.Version version = new GetPkgResult.Version();

        version.major = pkgVersion.getMajor();
        version.minor = pkgVersion.getMinor();
        version.micro = pkgVersion.getMicro();
        version.revision = pkgVersion.getRevision();
        version.preRelease = pkgVersion.getPreRelease();

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

        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersion.getPkgVersionLocalization(naturalLanguage);

        if(!pkgVersionLocalizationOptional.isPresent()) {
            if(!naturalLanguage.getCode().equals(NaturalLanguage.CODE_ENGLISH)) {
                pkgVersionLocalizationOptional = pkgVersion.getPkgVersionLocalization(NaturalLanguage.CODE_ENGLISH);
            }
        }

        if(pkgVersionLocalizationOptional.isPresent()) {
            version.description = pkgVersionLocalizationOptional.get().getDescription();
            version.summary = pkgVersionLocalizationOptional.get().getSummary();
            version.naturalLanguageCode = pkgVersionLocalizationOptional.get().getNaturalLanguage().getCode();
        }

        version.urls = Lists.transform(
                pkgVersion.getPkgVersionUrls(),
                new Function<PkgVersionUrl, GetPkgResult.Url>() {
                    @Override
                    public GetPkgResult.Url apply(org.haikuos.haikudepotserver.dataobjects.PkgVersionUrl input) {
                        GetPkgResult.Url url = new GetPkgResult.Url();
                        url.url = input.getUrl();
                        url.urlTypeCode = input.getPkgUrlType().getCode();
                        return url;
                    }
                });

        return version;
    }

    @Override
    public GetPkgResult getPkg(GetPkgRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.name));
        Preconditions.checkNotNull(request.versionType);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.naturalLanguageCode));

        final ObjectContext context = serverRuntime.getContext();

        Optional<Architecture> architectureOptional = Optional.absent();

        if(!Strings.isNullOrEmpty(request.architectureCode)) {
            architectureOptional = Architecture.getByCode(context, request.architectureCode);
        }

        NaturalLanguage naturalLanguage = getNaturalLanguage(context, request.naturalLanguageCode);

        GetPkgResult result = new GetPkgResult();
        Pkg pkg = getPkg(context, request.name);

        result.name = pkg.getName();
        result.modifyTimestamp = pkg.getModifyTimestamp().getTime();
        result.pkgCategoryCodes = Lists.transform(pkg.getPkgPkgCategories(), new Function<PkgPkgCategory, String>() {
            @Override
            public String apply(PkgPkgCategory input) {
                return input.getPkgCategory().getCode();
            }
        });

        switch(request.versionType) {
            case LATEST:
                if(!architectureOptional.isPresent()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; "+request.architectureCode);
                }

                Optional<PkgVersion> pkgVersionOptional = PkgVersion.getLatestForPkg(
                        context,
                        pkg,
                        ImmutableList.of(
                                architectureOptional.get(),
                                Architecture.getByCode(context, Architecture.CODE_ANY).get(),
                                Architecture.getByCode(context, Architecture.CODE_SOURCE).get())
                );

                if(!pkgVersionOptional.isPresent()) {
                    throw new ObjectNotFoundException(
                            PkgVersion.class.getSimpleName(),
                            request.name);
                }

                if(null!=request.incrementViewCounter && request.incrementViewCounter) {

                    String cacheKey = null;
                    String remoteIdentifier = getRemoteIdentifier();
                    boolean shouldIncrement;

                    if(shouldProtectPkgVersionViewCounterFromRecurringIncrementFromSameClient && !Strings.isNullOrEmpty(remoteIdentifier)) {
                        Long pkgVersionId = (Long) pkgVersionOptional.get().getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN);
                        cacheKey = Long.toString(pkgVersionId) + "@" + remoteIdentifier;
                    }

                    if(null==cacheKey) {
                        shouldIncrement = true;
                    }
                    else {
                        Boolean previouslyIncremented = remoteIdentifierToPkgView.getIfPresent(cacheKey);
                        shouldIncrement = null==previouslyIncremented;

                        if(!shouldIncrement) {
                            logger.info("would have incremented the view counter for '{}', but the client '{}' already did this recently", pkg.toString(), remoteIdentifier);
                        }
                    }

                    if(shouldIncrement) {
                        pkgVersionOptional.get().incrementViewCounter();
                        context.commitChanges();
                        logger.info("did increment the view counter for '{}'",pkg.toString());
                    }

                    if(null!=cacheKey) {
                        remoteIdentifierToPkgView.put(cacheKey, Boolean.TRUE);
                    }

                }

                result.versions = Collections.singletonList(createVersion(
                        pkgVersionOptional.get(),
                        naturalLanguage));

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
                return input.mediaTypeCode.equals(mediaTypeCode) && (null!=input.size) && (input.size == size);
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
                new Function<PkgIcon, GetPkgIconsResult.PkgIcon>() {
                    @Override
                    public GetPkgIconsResult.PkgIcon apply(PkgIcon input) {
                        GetPkgIconsResult.PkgIcon apiPkgIcon = new GetPkgIconsResult.PkgIcon();
                        apiPkgIcon.size = input.getSize();
                        apiPkgIcon.mediaTypeCode = input.getMediaType().getCode();
                        return apiPkgIcon;
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
            logger.warn("attempt to configure the icon for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        // insert or override the icons

        int updated = 0;
        int removed = 0;

        Set<PkgIcon> createdOrUpdatedPkgIcons = Sets.newHashSet();

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

        for(PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
            if(!createdOrUpdatedPkgIcons.contains(pkgIcon)) {
                context.deleteObjects(
                        pkgIcon.getPkgIconImage().get(),
                        pkgIcon);

                removed++;
            }
        }

        pkg.setModifyTimestamp();

        context.commitChanges();

        logger.info(
                "did configure icons for pkg {} (updated {}, removed {})",
                new Object[] {
                        pkg.getName(),
                        updated,
                        removed
                }
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
            logger.warn("attempt to remove the icon for package {}, but the user {} is not able to", pkg.getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        for(PkgIcon pkgIcon : ImmutableList.copyOf(pkg.getPkgIcons())) {
            context.deleteObjects(
                    pkgIcon.getPkgIconImage().get(),
                    pkgIcon);
        }

        pkg.setModifyTimestamp();

        context.commitChanges();

        logger.info("did remove icons for pkg {}",pkg.getName());

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

    @Override
    public GetPkgScreenshotsResult getPkgScreenshots(GetPkgScreenshotsRequest getPkgScreenshotsRequest) throws ObjectNotFoundException {
        Preconditions.checkNotNull(getPkgScreenshotsRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getPkgScreenshotsRequest.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, getPkgScreenshotsRequest.pkgName);

        GetPkgScreenshotsResult result = new GetPkgScreenshotsResult();
        result.items = Lists.transform(
                pkg.getSortedPkgScreenshots(),
                new Function<PkgScreenshot, GetPkgScreenshotsResult.PkgScreenshot>() {
                    @Override
                    public GetPkgScreenshotsResult.PkgScreenshot apply(PkgScreenshot pkgScreenshot) {
                        GetPkgScreenshotsResult.PkgScreenshot rs = new GetPkgScreenshotsResult.PkgScreenshot();
                        rs.code = pkgScreenshot.getCode();
                        rs.height = pkgScreenshot.getHeight();
                        rs.width = pkgScreenshot.getWidth();
                        rs.length = pkgScreenshot.getLength();
                        return rs;
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

        logger.info("did remove the screenshot {} on package {}", removePkgScreenshotRequest.code, pkg.getName());

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

        logger.info("did reorder the screenshots on package {}", pkg.getName());

        return new ReorderPkgScreenshotsResult();
    }

    @Override
    public UpdatePkgVersionLocalizationResult updatePkgVersionLocalization(
            UpdatePkgVersionLocalizationRequest updatePkgVersionLocalizationRequest) throws ObjectNotFoundException {

        Preconditions.checkNotNull(updatePkgVersionLocalizationRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgVersionLocalizationRequest.description));
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgVersionLocalizationRequest.naturalLanguageCode));
        Preconditions.checkState(!updatePkgVersionLocalizationRequest.naturalLanguageCode.equals(NaturalLanguage.CODE_ENGLISH));
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgVersionLocalizationRequest.pkgName));
        Preconditions.checkState(!Strings.isNullOrEmpty(updatePkgVersionLocalizationRequest.summary));
        Preconditions.checkNotNull(updatePkgVersionLocalizationRequest.replicateToOtherArchitecturesWithSameEnglishContent);

        final ObjectContext context = serverRuntime.getContext();
        Pkg pkg = getPkg(context, updatePkgVersionLocalizationRequest.pkgName);

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkg, Permission.PKG_EDITLOCALIZATION)) {
            throw new AuthorizationFailureException();
        }

        Architecture architecture = getArchitecture(context, updatePkgVersionLocalizationRequest.architectureCode);
        NaturalLanguage naturalLanguage = getNaturalLanguage(context, updatePkgVersionLocalizationRequest.naturalLanguageCode);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getLatestForPkg(context, pkg, Collections.singletonList(architecture));

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        pkgService.updatePkgVersionLocalization(
                context,
                pkgVersionOptional.get(),
                naturalLanguage,
                updatePkgVersionLocalizationRequest.summary,
                updatePkgVersionLocalizationRequest.description);

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

        context.commitChanges();

        logger.info("did update the localization for pkg {} in architecture {} for natural language {}",new Object[] {
                pkg.getName(),
                architecture.getCode(),
                naturalLanguage.getCode()
        });

        return new UpdatePkgVersionLocalizationResult();
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

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getLatestForPkg(context, pkg, Collections.singletonList(architecture));

        if(!pkgVersionOptional.isPresent()) {
            throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), pkg.getName() + "/" + architecture.getCode());
        }

        GetPkgVersionLocalizationsResult result = new GetPkgVersionLocalizationsResult();
        result.pkgVersionLocalizations = Lists.newArrayList();

        for(String naturalLanguageCode : getPkgVersionLocalizationsRequest.naturalLanguageCodes) {
            Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguageCode);

            if(pkgVersionLocalizationOptional.isPresent()) {
                GetPkgVersionLocalizationsResult.PkgVersionLocalization resultPkgVersionLocalization = new GetPkgVersionLocalizationsResult.PkgVersionLocalization();
                resultPkgVersionLocalization.naturalLanguageCode = naturalLanguageCode;
                resultPkgVersionLocalization.description = pkgVersionLocalizationOptional.get().getDescription();
                resultPkgVersionLocalization.summary = pkgVersionLocalizationOptional.get().getSummary();
                result.pkgVersionLocalizations.add(resultPkgVersionLocalization);
            }
        }

        return result;
    }

}
