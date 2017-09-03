/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectIdQuery;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.PkgImportService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.support.URLHelper;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PkgImportServiceImpl implements PkgImportService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgImportServiceImpl.class);

    @Resource
    private PkgServiceImpl pkgServiceImpl;

    @Resource
    private PkgIconService pkgIconService;

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    @Override
    public void importFrom(
            ObjectContext objectContext,
            ObjectId repositorySourceObjectId,
            org.haiku.pkg.model.Pkg pkg,
            boolean shouldPopulatePayloadLength) {

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != repositorySourceObjectId, "the repository source is must be provided");

        RepositorySource repositorySource = RepositorySource.get(
                objectContext,
                repositorySourceObjectId,
                ObjectIdQuery.CACHE);

        if(!repositorySource.getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository source that is not active; " + repositorySource);
        }

        if(!repositorySource.getRepository().getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository that is not active; " + repositorySource.getRepository());
        }

        // first, check to see if the package is there or not.

        Optional<Pkg> persistedPkgOptional = Pkg.tryGetByName(objectContext, pkg.getName());
        Pkg persistedPkg;
        Optional<PkgVersion> persistedLatestExistingPkgVersion = Optional.empty();
        Architecture architecture = Architecture.getByCode(objectContext, pkg.getArchitecture().name().toLowerCase())
                .orElseThrow(IllegalStateException::new);
        PkgVersion persistedPkgVersion = null;

        if(!persistedPkgOptional.isPresent()) {

            persistedPkg = objectContext.newObject(Pkg.class);
            persistedPkg.setName(pkg.getName());
            persistedPkg.setActive(Boolean.TRUE);
            pkgServiceImpl.ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());
            LOGGER.info("the package {} did not exist; will create", pkg.getName());

            possiblyReplicateDataFromDevelMainPkg(objectContext, persistedPkg);

        } else {

            persistedPkg = persistedPkgOptional.get();
            pkgServiceImpl.ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());

            // if we know that the package exists then we should look for the version.

            persistedPkgVersion = PkgVersion.getForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource.getRepository(),
                    architecture,
                    new VersionCoordinates(pkg.getVersion())).orElse(null);

            persistedLatestExistingPkgVersion = pkgServiceImpl.getLatestPkgVersionForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource.getRepository(),
                    Collections.singletonList(architecture));
        }

        if (null==persistedPkgVersion) {

            persistedPkgVersion = objectContext.newObject(PkgVersion.class);
            persistedPkgVersion.setMajor(pkg.getVersion().getMajor());
            persistedPkgVersion.setMinor(pkg.getVersion().getMinor());
            persistedPkgVersion.setMicro(pkg.getVersion().getMicro());
            persistedPkgVersion.setPreRelease(pkg.getVersion().getPreRelease());
            persistedPkgVersion.setRevision(pkg.getVersion().getRevision());
            persistedPkgVersion.setRepositorySource(repositorySource);
            persistedPkgVersion.setArchitecture(architecture);
            persistedPkgVersion.setPkg(persistedPkg);

            LOGGER.info(
                    "the version {} of package {} did not exist; will create",
                    pkg.getVersion().toString(),
                    pkg.getName());
        } else {
            LOGGER.debug(
                    "the version {} of package {} did exist; will re-configure necessary data",
                    pkg.getVersion().toString(),
                    pkg.getName());

        }

        persistedPkgVersion.setActive(Boolean.TRUE);

        importCopyrights(objectContext, pkg, persistedPkgVersion);
        importLicenses(objectContext, pkg, persistedPkgVersion);
        importUrls(objectContext, pkg, persistedPkgVersion);

        if(!Strings.isNullOrEmpty(pkg.getSummary()) || !Strings.isNullOrEmpty(pkg.getDescription())) {
            pkgLocalizationService.updatePkgVersionLocalization(
                    objectContext,
                    persistedPkgVersion,
                    NaturalLanguage.getByCode(objectContext, NaturalLanguage.CODE_ENGLISH)
                            .orElseThrow(IllegalStateException::new),
                    null, // not supported quite yet
                    pkg.getSummary(),
                    pkg.getDescription());
        }

        // now possibly switch the latest flag over to the new one from the old one.
        possiblyReconfigurePersistedPkgVersionToBeLatest(
                objectContext,
                persistedLatestExistingPkgVersion.orElse(null),
                persistedPkgVersion);

        // [apl]
        // If this fails, we will let it go and it can be tried again a bit later on.  The system can try to back-fill
        // those at some later date if any of the latest versions for packages are missing.  This is better than
        // failing the import at this stage since this is "just" meta data.

        if(shouldPopulatePayloadLength && null==persistedPkgVersion.getPayloadLength()) {
            populatePayloadLength(persistedPkgVersion);
        }

        LOGGER.debug("have processed package {}", pkg.toString());

    }

    private void importUrls(ObjectContext objectContext, org.haiku.pkg.model.Pkg pkg, PkgVersion persistedPkgVersion) {
        PkgUrlType pkgUrlType = PkgUrlType.getByCode(
                objectContext,
                org.haiku.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase()).orElseThrow(IllegalStateException::new);

        Optional<PkgVersionUrl> homeUrlOptional = persistedPkgVersion.getPkgVersionUrlForType(pkgUrlType);

        if (null != pkg.getHomePageUrl()) {
            if(homeUrlOptional.isPresent()) {
                homeUrlOptional.get().setUrl(pkg.getHomePageUrl().getUrl());
                homeUrlOptional.get().setName(pkg.getHomePageUrl().getName());
            } else {
                PkgVersionUrl persistedPkgVersionUrl = objectContext.newObject(PkgVersionUrl.class);
                persistedPkgVersionUrl.setUrl(pkg.getHomePageUrl().getUrl());
                persistedPkgVersionUrl.setName(pkg.getHomePageUrl().getName());
                persistedPkgVersionUrl.setPkgUrlType(pkgUrlType);
                persistedPkgVersionUrl.setPkgVersion(persistedPkgVersion);
            }
        }
    }

    private void importLicenses(ObjectContext objectContext, org.haiku.pkg.model.Pkg pkg, PkgVersion persistedPkgVersion) {
        List<String> existingLicenses = persistedPkgVersion.getLicenses();

        // now add the licenses that are not already there.

        for (String license : pkg.getLicenses()) {
            if(!existingLicenses.contains(license)) {
                PkgVersionLicense persistedPkgVersionLicense = objectContext.newObject(PkgVersionLicense.class);
                persistedPkgVersionLicense.setBody(license);
                persistedPkgVersionLicense.setPkgVersion(persistedPkgVersion);
            }
        }

        // remove those licenses that are no longer present

        for(PkgVersionLicense pkgVersionLicense : ImmutableList.copyOf(persistedPkgVersion.getPkgVersionLicenses())) {
            if(!pkg.getLicenses().contains(pkgVersionLicense.getBody())) {
                persistedPkgVersion.removeFromPkgVersionLicenses(pkgVersionLicense);
                objectContext.deleteObjects(pkgVersionLicense);
            }
        }
    }

    private void importCopyrights(ObjectContext objectContext, org.haiku.pkg.model.Pkg pkg, PkgVersion persistedPkgVersion) {
        List<String> existingCopyrights = persistedPkgVersion.getCopyrights();

        // now add the copyrights that are not already there.

        for (String copyright : pkg.getCopyrights()) {
            if(!existingCopyrights.contains(copyright)) {
                PkgVersionCopyright persistedPkgVersionCopyright = objectContext.newObject(PkgVersionCopyright.class);
                persistedPkgVersionCopyright.setBody(copyright);
                persistedPkgVersionCopyright.setPkgVersion(persistedPkgVersion);
            }
        }

        // remove those copyrights that are no longer present

        for(PkgVersionCopyright pkgVersionCopyright : ImmutableList.copyOf(persistedPkgVersion.getPkgVersionCopyrights())) {
            if(!pkg.getCopyrights().contains(pkgVersionCopyright.getBody())) {
                persistedPkgVersion.removeFromPkgVersionCopyrights(pkgVersionCopyright);
                objectContext.deleteObjects(pkgVersionCopyright);
            }
        }
    }

    /**
     * <p>If the imported package is a developmental package then there may be a main package that this
     * development package relates to.  In this case, some data such as localizations should be copied
     * from the main package to the development package.</p>
     *
     * <p>For example; &quot;cairo&quot; has a developmental package of &quot;cairo_devel&quot;.  If the
     * &quot;cairo_devel&quot; is imported, it will look for the &quot;cairo&quot; package and will
     * take data from it.</p>
     */

    private void possiblyReplicateDataFromDevelMainPkg(ObjectContext objectContext, Pkg persistedPkg) {
        pkgServiceImpl.tryGetDevelMainPkg(objectContext, persistedPkg.getName()).ifPresent((mainPkg) -> {

            try {
                pkgIconService.replicatePkgIcons(objectContext, mainPkg, persistedPkg);
            } catch (IOException | BadPkgIconException e) {
                LOGGER.error(
                        "was unable to update the icon from pkg " + mainPkg.getName() + " to " + persistedPkg.getName(),
                        e);
            }

            pkgLocalizationService.replicatePkgLocalizations(
                    objectContext,
                    mainPkg,
                    persistedPkg,
                    PkgLocalizationService.SUFFIX_SUMMARY_DEVELOPMENT);
        });
    }

    private void populatePayloadLength(PkgVersion persistedPkgVersion) {
        long length = -1;
        URL pkgVersionHpkgURL = persistedPkgVersion.getHpkgURL();

        try {
            length = URLHelper.payloadLength(pkgVersionHpkgURL);
        } catch(IOException ioe) {
            LOGGER.error("unable to get the payload length for; " + persistedPkgVersion, ioe);
        }

        if(length > 0) {
            persistedPkgVersion.setPayloadLength(length);
        }
    }

    private void possiblyReconfigurePersistedPkgVersionToBeLatest(
            ObjectContext objectContext,
            PkgVersion persistedLatestExistingPkgVersion,
            PkgVersion persistedPkgVersion) {

        if(null != persistedLatestExistingPkgVersion) {
            VersionCoordinatesComparator versionCoordinatesComparator = new VersionCoordinatesComparator();
            VersionCoordinates persistedPkgVersionCoords = persistedPkgVersion.toVersionCoordinates();
            VersionCoordinates persistedLatestExistingPkgVersionCoords = persistedLatestExistingPkgVersion.toVersionCoordinates();

            int c = versionCoordinatesComparator.compare(
                    persistedPkgVersionCoords,
                    persistedLatestExistingPkgVersionCoords);

            if(c > 0) {
                persistedPkgVersion.setIsLatest(true);
                persistedLatestExistingPkgVersion.setIsLatest(false);
            } else {
                boolean isRealArchitecture = !persistedPkgVersion.getArchitecture().getCode().equals(Architecture.CODE_SOURCE);

                if(0==c) {
                    if(isRealArchitecture) {
                        LOGGER.debug(
                                "imported a package version {} of {} which is the same as the existing {}",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords);
                    }
                } else {
                    if(isRealArchitecture) {

                        // [apl 3.dec.2016]
                        // If the package from the repository is older than the one that is presently marked as latest
                        // then a regression has occurred.  In this case make the imported one be the latest and mark
                        // the later ones as "inactive".

                        List<PkgVersion> pkgVersionsToDeactivate = PkgVersion.getForPkg(
                                objectContext,
                                persistedPkgVersion.getPkg(),
                                persistedPkgVersion.getRepositorySource().getRepository(),
                                false)
                                .stream()
                                .filter((pv) -> pv.getArchitecture().equals(persistedPkgVersion.getArchitecture()))
                                .filter((pv) -> versionCoordinatesComparator.compare(
                                        persistedPkgVersionCoords,
                                        pv.toVersionCoordinates()) < 0)
                                .collect(Collectors.toList());

                        LOGGER.warn(
                                "imported a package version {} of {} which is older or the same as the existing {}" +
                                        " -- will deactivate {} pkg versions after the imported one and make the" +
                                        " imported one as latest",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords,
                                pkgVersionsToDeactivate.size());

                        for(PkgVersion pkgVersionToDeactivate : pkgVersionsToDeactivate) {
                            pkgVersionToDeactivate.setActive(false);
                            pkgVersionToDeactivate.setIsLatest(false);
                            LOGGER.info("deactivated {}", pkgVersionToDeactivate);
                        }

                        persistedPkgVersion.setIsLatest(true);
                    }
                }
            }
        } else {
            persistedPkgVersion.setIsLatest(true);
        }

    }


}
