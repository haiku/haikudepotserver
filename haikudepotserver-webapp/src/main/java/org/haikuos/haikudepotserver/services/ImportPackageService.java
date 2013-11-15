/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.services;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.model.*;
import org.haikuos.pkg.model.Pkg;
import org.haikuos.pkg.model.PkgVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>This class will import a package into the system's database; merging the data, or disabling entries as
 * necessary.</p>
 */

@Service
public class ImportPackageService {

    protected static Logger logger = LoggerFactory.getLogger(ImportPackageService.class);

    private Expression toExpression(PkgVersion version) {
        return ExpressionFactory.matchExp(
                org.haikuos.haikudepotserver.model.PkgVersion.MAJOR_PROPERTY, version.getMajor())
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.model.PkgVersion.MINOR_PROPERTY, version.getMinor()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.model.PkgVersion.MICRO_PROPERTY, version.getMicro()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.model.PkgVersion.PRE_RELEASE_PROPERTY, version.getPreRelease()))
                .andExp(ExpressionFactory.matchExp(
                        org.haikuos.haikudepotserver.model.PkgVersion.REVISION_PROPERTY, version.getRevision()));
    }

    /**
     * <p>This is the Cayenne environment handle.</p>
     */

    @Resource
    ServerRuntime serverRuntime;

    /**
     * <p>This method will import the pkg described.  The repository is also provided as a Cayenne object id in order
     * to provide reference to the repository from which this pkg was obtained.  Note that this method will execute
     * as one 'transaction' (in the Cayenne sense).</p>
     */

    public void run(
            ObjectId repositoryObjectId,
            Pkg pkg) {

        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(repositoryObjectId);

        ObjectContext objectContext = serverRuntime.getContext();
        Repository repository = Repository.get(objectContext, repositoryObjectId);

        // first, check to see if the package is there or not.

        Optional<org.haikuos.haikudepotserver.model.Pkg> persistedPkgOptional = org.haikuos.haikudepotserver.model.Pkg.getByName(objectContext, pkg.getName());
        org.haikuos.haikudepotserver.model.Pkg persistedPkg;
        org.haikuos.haikudepotserver.model.PkgVersion persistedPkgVersion = null;

        if(!persistedPkgOptional.isPresent()) {
            persistedPkg = objectContext.newObject(org.haikuos.haikudepotserver.model.Pkg.class);
            persistedPkg.setName(pkg.getName());
            persistedPkg.setActive(Boolean.TRUE);
            logger.info("the package {} did not exist; will create",pkg.getName());
        }
        else {
            persistedPkg = persistedPkgOptional.get();

            // if we know that the package exists then we should look for the version.

            SelectQuery selectQuery = new SelectQuery(
                    org.haikuos.haikudepotserver.model.PkgVersion.class,
                    ExpressionFactory.matchExp(
                            org.haikuos.haikudepotserver.model.PkgVersion.PKG_PROPERTY,
                            persistedPkg)
                    .andExp(toExpression(pkg.getVersion())));

            persistedPkgVersion = Iterables.getOnlyElement(
                    (List<org.haikuos.haikudepotserver.model.PkgVersion>) objectContext.performQuery(selectQuery),
                    null);
        }

        if(null==persistedPkgVersion) {

            persistedPkgVersion = objectContext.newObject(org.haikuos.haikudepotserver.model.PkgVersion.class);
            persistedPkgVersion.setActive(Boolean.TRUE);
            persistedPkgVersion.setMajor(pkg.getVersion().getMajor());
            persistedPkgVersion.setMinor(pkg.getVersion().getMinor());
            persistedPkgVersion.setMicro(pkg.getVersion().getMicro());
            persistedPkgVersion.setPreRelease(pkg.getVersion().getPreRelease());
            persistedPkgVersion.setRevision(pkg.getVersion().getRevision());
            persistedPkgVersion.setRepository(repository);
            persistedPkgVersion.setArchitecture(Architecture.getByCode(
                    objectContext,
                    pkg.getArchitecture().name().toLowerCase()).get());
            persistedPkgVersion.setPkg(persistedPkg);

            // now add the copyrights
            for(String copyright : pkg.getCopyrights()) {
                PkgVersionCopyright persistedPkgVersionCopyright = objectContext.newObject(PkgVersionCopyright.class);
                persistedPkgVersionCopyright.setBody(copyright);
                persistedPkgVersionCopyright.setPkgVersion(persistedPkgVersion);
            }

            // now add the licenses
            for(String license : pkg.getLicenses()) {
                PkgVersionLicense persistedPkgVersionLicense = objectContext.newObject(PkgVersionLicense.class);
                persistedPkgVersionLicense.setBody(license);
                persistedPkgVersionLicense.setPkgVersion(persistedPkgVersion);
            }

            if(null!=pkg.getHomePageUrl()) {
                PkgVersionUrl persistedPkgVersionUrl = objectContext.newObject(PkgVersionUrl.class);
                persistedPkgVersionUrl.setUrl(pkg.getHomePageUrl().getUrl());
                persistedPkgVersionUrl.setPkgUrlType(PkgUrlType.getByCode(
                        objectContext,
                        pkg.getHomePageUrl().getUrlType().name().toLowerCase()).get());
                persistedPkgVersionUrl.setPkgVersion(persistedPkgVersion);
            }

            if(!Strings.isNullOrEmpty(pkg.getSummary()) || !Strings.isNullOrEmpty(pkg.getDescription())) {
                PkgVersionLocalization persistedPkgVersionLocalization = objectContext.newObject(PkgVersionLocalization.class);
                persistedPkgVersionLocalization.setDescription(pkg.getDescription());
                persistedPkgVersionLocalization.setSummary(pkg.getSummary());
                persistedPkgVersionLocalization.setPkgVersion(persistedPkgVersion);
            }

            logger.info("the version {} of package {} did not exist; will create", pkg.getVersion().toString(), pkg.getName());
        }

        objectContext.commitChanges();

        logger.info("have processed package {}",pkg.toString());

    }

}
