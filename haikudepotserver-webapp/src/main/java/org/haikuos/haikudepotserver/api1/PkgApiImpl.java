/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.*;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.BadPkgIconException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.MediaType;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import com.googlecode.jsonrpc4j.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    protected static Logger logger = LoggerFactory.getLogger(PkgApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationService authorizationService;

    @Resource
    PkgService pkgService;

    @Override
    public SearchPkgsResult searchPkgs(SearchPkgsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.architectureCode));

        final ObjectContext context = serverRuntime.getContext();

        PkgSearchSpecification specification = new PkgSearchSpecification();

        String exp = request.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!=request.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(request.expressionType.name()));
        }

        final Optional<Architecture> architectureOptional = Architecture.getByCode(context,request.architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new IllegalStateException("the architecture specified is not able to be found; "+request.architectureCode);
        }

        specification.setArchitectures(Sets.newHashSet(
                architectureOptional.get()
                ,
                Architecture.getByCode(context,Architecture.CODE_ANY).get()
//                ,
//                Architecture.getByCode(context,Architecture.CODE_SOURCE).get()
        ));

        specification.setLimit(request.limit+1); // get +1 to see if there are any more.
        specification.setOffset(request.offset);

        SearchPkgsResult result = new SearchPkgsResult();
        List<Pkg> searchedPkgs = pkgService.search(context,specification);

        // if there are more than we asked for then there must be more available.

        result.hasMore = new Boolean(searchedPkgs.size() > request.limit);

        if(result.hasMore) {
            searchedPkgs = searchedPkgs.subList(0,request.limit);
        }

        result.items = Lists.newArrayList(Iterables.transform(
                searchedPkgs,
                new Function<Pkg, SearchPkgsResult.Pkg>() {
                    @Override
                    public SearchPkgsResult.Pkg apply(org.haikuos.haikudepotserver.dataobjects.Pkg input) {
                        SearchPkgsResult.Pkg resultPkg = new SearchPkgsResult.Pkg();
                        resultPkg.name = input.getName();
                        resultPkg.modifyTimestamp = input.getModifyTimestamp().getTime();

                        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getLatestForPkg(
                                context,
                                input,
                                Lists.newArrayList(
                                        architectureOptional.get(),
                                        Architecture.getByCode(context, Architecture.CODE_ANY).get(),
                                        Architecture.getByCode(context, Architecture.CODE_SOURCE).get()));

                        if(pkgVersionOptional.isPresent()) {
                            SearchPkgsResult.Version resultVersion = new SearchPkgsResult.Version();
                            resultVersion.major = pkgVersionOptional.get().getMajor();
                            resultVersion.minor = pkgVersionOptional.get().getMinor();
                            resultVersion.micro = pkgVersionOptional.get().getMicro();
                            resultVersion.preRelease = pkgVersionOptional.get().getPreRelease();
                            resultVersion.revision = pkgVersionOptional.get().getRevision();
                            resultPkg.version = resultVersion;
                        }

                        return resultPkg;
                    }
                }
        ));

        return result;
    }

    /**
     * <p>Given the persistence model object, this method will construct the DTO to be sent back over the wire.</p>
     */

    private GetPkgResult.Version createVersion(PkgVersion pkgVersion) {
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

        // TODO - languages
        if(!pkgVersion.getPkgVersionLocalizations().isEmpty()) {
            PkgVersionLocalization pkgVersionLocalization = pkgVersion.getPkgVersionLocalizations().get(0);
            version.description = pkgVersionLocalization.getDescription();
            version.summary = pkgVersionLocalization.getSummary();
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

        final ObjectContext context = serverRuntime.getContext();

        Optional<Architecture> architectureOptional = Optional.absent();

        if(!Strings.isNullOrEmpty(request.architectureCode)) {
            architectureOptional = Architecture.getByCode(context, request.architectureCode);
        }

        GetPkgResult result = new GetPkgResult();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.name);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.name);
        }

        result.name = pkgOptional.get().getName();
        result.modifyTimestamp = pkgOptional.get().getModifyTimestamp().getTime();
        result.hasIcon = !pkgOptional.get().getPkgIcons().isEmpty();

        switch(request.versionType) {
            case LATEST:
                if(!architectureOptional.isPresent()) {
                    throw new IllegalStateException("the specified architecture was not able to be found; "+request.architectureCode);
                }

                Optional<PkgVersion> pkgVersionOptional = PkgVersion.getLatestForPkg(
                        context,
                        pkgOptional.get(),
                        Lists.newArrayList(
                                architectureOptional.get(),
                                Architecture.getByCode(context, Architecture.CODE_ANY).get(),
                                Architecture.getByCode(context, Architecture.CODE_SOURCE).get()));

                if(!pkgVersionOptional.isPresent()) {
                    throw new ObjectNotFoundException(PkgVersion.class.getSimpleName(), request.name);
                }

                result.versions = Collections.singletonList(createVersion(pkgVersionOptional.get()));
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
    public ConfigurePkgIconResult configurePkgIcon(ConfigurePkgIconRequest request) throws ObjectNotFoundException, BadPkgIconException {
        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.pkgName));

        final ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        User user = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user, pkgOptional.get(), Permission.PKG_EDITICON)) {
            logger.warn("attempt to configure the icon for package {}, but the user {} is not able to", pkgOptional.get().getName(), user.getNickname());
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
                                    pkgOptional.get()
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

        for(PkgIcon pkgIcon : ImmutableList.copyOf(pkgOptional.get().getPkgIcons())) {
            if(!createdOrUpdatedPkgIcons.contains(pkgIcon)) {
                context.deleteObjects(
                        pkgIcon.getPkgIconImage().get(),
                        pkgIcon);

                removed++;
            }
        }

        pkgOptional.get().setModifyTimestamp();

        context.commitChanges();

        logger.info(
                "did configure icons for pkg {} (updated {}, removed {})",
                new Object[] {
                        pkgOptional.get().getName(),
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
        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.pkgName);
        }

        User user = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user, pkgOptional.get(), Permission.PKG_EDITICON)) {
            logger.warn("attempt to remove the icon for package {}, but the user {} is not able to", pkgOptional.get().getName(), user.getNickname());
            throw new AuthorizationFailureException();
        }

        for(PkgIcon pkgIcon : ImmutableList.copyOf(pkgOptional.get().getPkgIcons())) {
            context.deleteObjects(
                    pkgIcon.getPkgIconImage().get(),
                    pkgIcon);
        }

        pkgOptional.get().setModifyTimestamp();

        context.commitChanges();

        logger.info("did remove icons for pkg {}",pkgOptional.get().getName());

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
        Optional<Pkg> pkgOptional = Pkg.getByName(context, getPkgScreenshotsRequest.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), getPkgScreenshotsRequest.pkgName);
        }

        GetPkgScreenshotsResult result = new GetPkgScreenshotsResult();
        result.items = Lists.transform(
                pkgOptional.get().getSortedPkgScreenshots(),
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
        Optional<Pkg> pkgOptional = Pkg.getByName(context, reorderPkgScreenshotsRequest.pkgName);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), reorderPkgScreenshotsRequest.pkgName);
        }

        User authUser = obtainAuthenticatedUser(context);

        if(!authorizationService.check(context, authUser, pkgOptional.get(), Permission.PKG_EDITSCREENSHOT)) {
            throw new AuthorizationFailureException();
        }

        pkgOptional.get().reorderPkgScreenshots(reorderPkgScreenshotsRequest.codes);
        context.commitChanges();

        logger.info("did reorder the screenshots on package {}", pkgOptional.get().getName());

        return null;
    }
}
