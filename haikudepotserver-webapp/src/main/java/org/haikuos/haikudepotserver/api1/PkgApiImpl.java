/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.pkg.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * <p>See {@link PkgApi} for details on the methods this API affords.</p>
 */

@Component
public class PkgApiImpl extends AbstractApiImpl implements PkgApi {

    protected static Logger logger = LoggerFactory.getLogger(PkgApiImpl.class);

    @Resource
    ServerRuntime serverRuntime;

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
        result.canEdit = pkgOptional.get().canBeEditedBy(tryObtainAuthenticatedUser(context).orNull());
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

    @Override
    public RemoveIconResult removeIcon(RemoveIconRequest request) throws ObjectNotFoundException {

        Preconditions.checkNotNull(request);
        Preconditions.checkState(!Strings.isNullOrEmpty(request.name));

        final ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkgOptional = Pkg.getByName(context, request.name);

        if(!pkgOptional.isPresent()) {
            throw new ObjectNotFoundException(Pkg.class.getSimpleName(), request.name);
        }

        User user = obtainAuthenticatedUser(context);

        if(!pkgOptional.get().canBeEditedBy(user)) {
            logger.warn("attempt to remove the icon for package {}, but the user {} is not able to",pkgOptional.get().getName(),user.getNickname());
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

        return new RemoveIconResult();
    }

}
