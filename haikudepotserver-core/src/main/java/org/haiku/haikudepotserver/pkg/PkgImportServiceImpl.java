/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgUrlType;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.PkgVersionCopyright;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLicense;
import org.haiku.haikudepotserver.dataobjects.PkgVersionUrl;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.pkg.model.BadPkgIconException;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.PkgImportService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.support.ExposureType;
import org.haiku.haikudepotserver.support.HpkgHelper;
import org.haiku.haikudepotserver.support.URLHelperService;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinatesComparator;
import org.haiku.pkg.AttributeContext;
import org.haiku.pkg.HpkgFileExtractor;
import org.haiku.pkg.model.Attribute;
import org.haiku.pkg.model.AttributeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PkgImportServiceImpl implements PkgImportService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgImportServiceImpl.class);

    private final PkgServiceImpl pkgServiceImpl;
    private final PkgIconService pkgIconService;
    private final PkgLocalizationService pkgLocalizationService;
    private final URLHelperService urlHelperService;

    public PkgImportServiceImpl(
            PkgServiceImpl pkgServiceImpl,
            PkgIconService pkgIconService,
            PkgLocalizationService pkgLocalizationService,
            URLHelperService urlHelperService) {
        this.pkgServiceImpl = Preconditions.checkNotNull(pkgServiceImpl);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
        this.urlHelperService = Preconditions.checkNotNull(urlHelperService);
    }

    @Override
    public void importFrom(
            ObjectContext objectContext,
            ObjectId repositorySourceObjectId,
            org.haiku.pkg.model.Pkg pkg,
            boolean populateFromPayload) {

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != repositorySourceObjectId, "the repository source is must be provided");

        RepositorySource repositorySource = RepositorySource.get(
                objectContext,
                repositorySourceObjectId);

        if (!repositorySource.getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository source that is not active; " + repositorySource);
        }

        if (!repositorySource.getRepository().getActive()) {
            throw new IllegalStateException("it is not possible to import from a repository that is not active; " + repositorySource.getRepository());
        }

        // first, check to see if the package is there or not.

        Optional<Pkg> persistedPkgOptional = Pkg.tryGetByName(objectContext, pkg.getName());
        Pkg persistedPkg;
        Optional<PkgVersion> persistedLatestExistingPkgVersion = Optional.empty();
        Architecture architecture = Architecture.tryGetByCode(objectContext, pkg.getArchitecture().name().toLowerCase())
                .orElseThrow(IllegalStateException::new);
        PkgVersion persistedPkgVersion = null;

        if (persistedPkgOptional.isEmpty()) {
            persistedPkg = createPkg(objectContext, pkg.getName());
            pkgServiceImpl.ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());
            LOGGER.info("the package [{}] did not exist; will create", pkg.getName());
        } else {

            persistedPkg = persistedPkgOptional.get();
            pkgServiceImpl.ensurePkgProminence(objectContext, persistedPkg, repositorySource.getRepository());

            // if we know that the package exists then we should look for the version.

            persistedPkgVersion = PkgVersion.tryGetForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource,
                    architecture,
                    new VersionCoordinates(pkg.getVersion())).orElse(null);

            persistedLatestExistingPkgVersion = pkgServiceImpl.getLatestPkgVersionForPkg(
                    objectContext,
                    persistedPkg,
                    repositorySource,
                    Collections.singletonList(architecture));
        }

        if (null == persistedPkgVersion) {

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
                    "the version [{}] of package [{}] did not exist; will create",
                    pkg.getVersion().toString(),
                    pkg.getName());
        } else {
            LOGGER.debug(
                    "the version [{}] of package [{}] did exist; will re-configure necessary data",
                    pkg.getVersion().toString(),
                    pkg.getName());

        }

        persistedPkgVersion.setActive(Boolean.TRUE);

        importCopyrights(objectContext, pkg, persistedPkgVersion);
        importLicenses(objectContext, pkg, persistedPkgVersion);
        importUrls(objectContext, pkg, persistedPkgVersion);

        if (!Strings.isNullOrEmpty(pkg.getSummary()) || !Strings.isNullOrEmpty(pkg.getDescription())) {
            pkgLocalizationService.updatePkgVersionLocalization(
                    objectContext,
                    persistedPkgVersion,
                    NaturalLanguage.getEnglish(objectContext),
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
        // failing the import at this stage since this is "just" meta data.  The length of the payload is being used as
        // a signal that the payload was downloaded and processed at some point.

        if(populateFromPayload && shouldPopulateFromPayload(persistedPkgVersion)) {
            populateFromPayload(objectContext, persistedPkgVersion);
        }

        LOGGER.debug("have processed package {}", pkg);
    }

    private Pkg createPkg(ObjectContext objectContext, String name) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "the name is required");

        Pkg pkg = objectContext.newObject(Pkg.class);
        pkg.setName(name);
        pkg.setActive(Boolean.TRUE);

        String basePkgName = pkgServiceImpl
                .tryGetMainPkgNameForSubordinatePkg(objectContext, name)
                .orElse(name);

        PkgSupplement pkgSupplement = PkgSupplement
                .tryGetByBasePkgName(objectContext, basePkgName)
                .orElseGet(() -> {
                    PkgSupplement result = objectContext.newObject(PkgSupplement.class);
                    result.setBasePkgName(basePkgName);
                    return result;
                });

        pkg.setPkgSupplement(pkgSupplement);
        pkgSupplement.addToPkgs(pkg);

        return pkg;
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

    private boolean shouldPopulateFromPayload(PkgVersion persistedPkgVersion) {
        return null == persistedPkgVersion.getPayloadLength()
                && Stream.of(
                PkgService.SUFFIX_PKG_DEBUGINFO,
                PkgService.SUFFIX_PKG_DEVELOPMENT,
                PkgService.SUFFIX_PKG_SOURCE).noneMatch(suffix -> persistedPkgVersion.getPkg().getName().endsWith(suffix));
    }

    /**
     * <p>This will read in the payload into a temporary file.  From there it will parse it
     * and take up any data from it such as the icon and the length of the download in
     * bytes.</p>
     */

    private void populateFromPayload(ObjectContext objectContext, PkgVersion persistedPkgVersion) {
        persistedPkgVersion.tryGetHpkgURL(ExposureType.INTERNAL_FACING)
                .ifPresentOrElse(
                        u -> populateFromPayload(objectContext, persistedPkgVersion, u),
                        () -> LOGGER.info(
                                "no package payload data recorded because there is no "
                                        + "hpkg url for pkg [{}] version [{}]",
                                persistedPkgVersion.getPkg(), persistedPkgVersion));
    }

    private void populateFromPayload(
            ObjectContext objectContext,
            PkgVersion persistedPkgVersion, URL url) {
        File temporaryFile = null;

        try {
            String prefix = persistedPkgVersion.getPkg().getName() + "_" + RandomStringUtils.randomAlphabetic(3) + "_";
            // ^ need to ensure minimum length of the prefix
            temporaryFile = File.createTempFile(prefix, ".hpkg");

            try {
                urlHelperService.transferPayloadToFile(url, temporaryFile);
            }
            catch (IOException ioe) {
                // if we can't download then don't stop the entire import process - just log and carry on.
                LOGGER.warn("unable to download from the url [{}] --> [{}]; will ignore", url, temporaryFile);
                return;
            }

            // the length of the payload is interesting and trivial to capture from
            // the data downloaded.

            if (null == persistedPkgVersion.getPayloadLength()
                || persistedPkgVersion.getPayloadLength() != temporaryFile.length()) {
                persistedPkgVersion.setPayloadLength(temporaryFile.length());
                LOGGER.info("recording new length for [{}] version [{}] of {}bytes",
                        persistedPkgVersion.getPkg(), persistedPkgVersion, temporaryFile.length());
            }

            // more complex is the capture of the data in the parsed payload data.

            HpkgFileExtractor hpkgFileExtractor;

            try {
                hpkgFileExtractor = new HpkgFileExtractor(temporaryFile);
            }
            catch (Throwable th) {
                // if it is not possible to parse the HPKG then log and carry on.
                LOGGER.warn("unable to parse the payload from [{}]", url, th);
                return;
            }

            populateIconFromPayload(objectContext, persistedPkgVersion, hpkgFileExtractor);
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        finally {
            if (null != temporaryFile && temporaryFile.exists()) {
                if (temporaryFile.delete()) {
                    LOGGER.debug("did delete the temporary file");
                }
                else {
                    LOGGER.error("unable to delete the temporary file [{}]", temporaryFile);
                }
            }
        }
    }

    private void populateIconFromPayload(
            ObjectContext objectContext, PkgVersion persistedPkgVersion,
            HpkgFileExtractor hpkgFileExtractor) {
        AttributeContext context = hpkgFileExtractor.getTocContext();
        List<Attribute> iconAttrs = HpkgHelper.findIconAttributesFromExecutableDirEntries(
                context, hpkgFileExtractor.getToc());
        switch (iconAttrs.size()) {
            case 0 -> LOGGER.info("package [{}] version [{}] has no icons",
                    persistedPkgVersion.getPkg(), persistedPkgVersion);
            case 1 -> populateIconFromPayload(
                    objectContext, persistedPkgVersion, context,
                    Iterables.getFirst(iconAttrs, null));
            default -> LOGGER.info("package [{}] version [{}] has {} icons --> ambiguous so will not load any",
                    persistedPkgVersion.getPkg(), persistedPkgVersion, iconAttrs.size());
        }
    }

    private void populateIconFromPayload(
            ObjectContext objectContext, PkgVersion persistedPkgVersion,
            AttributeContext context, Attribute attribute) {
        Attribute dataAttr = attribute.tryGetChildAttribute(AttributeId.DATA).orElse(null);

        if (null == dataAttr) {
            LOGGER.warn("the icon [{}] found for package [{}] version [{}] does not have a data attribute",
                    AttributeId.FILE_ATTRIBUTE, persistedPkgVersion.getPkg(), persistedPkgVersion);
        }

        ByteSource byteSource = (ByteSource) dataAttr.getValue(context);

        try {
            LOGGER.info("did find {} bytes of icon data for package [{}] version [{}]",
                    byteSource.size(), persistedPkgVersion.getPkg(), persistedPkgVersion);
        }
        catch (IOException ignore) {
            LOGGER.warn("cannot get the size of the icon payload for package [{}] version [{}]",
                    persistedPkgVersion.getPkg(), persistedPkgVersion);
        }

        try (InputStream inputStream = byteSource.openStream()) {
            pkgIconService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, MediaType.MEDIATYPE_HAIKUVECTORICONFILE),
                    null,
                    objectContext,
                    persistedPkgVersion.getPkg().getPkgSupplement());
        }
        catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        catch (BadPkgIconException e) {
            LOGGER.info("a failure has arisen loading a package icon data", e);
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

                if (0 == c) {
                    if (isRealArchitecture) {
                        LOGGER.debug(
                                "imported a package version [{}] of [{}] which is the same as the existing [{}]",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords);
                    }
                } else {
                    if (isRealArchitecture) {

                        // [apl 3.dec.2016]
                        // If the package from the repository is older than the one that is presently marked as latest
                        // then a regression has occurred.  In this case make the imported one be the latest and mark
                        // the later ones as "inactive".

                        List<PkgVersion> pkgVersionsToDeactivate = PkgVersion.findForPkg(
                                objectContext,
                                persistedPkgVersion.getPkg(),
                                persistedPkgVersion.getRepositorySource(),
                                false)
                                .stream()
                                .filter((pv) -> pv.getArchitecture().equals(persistedPkgVersion.getArchitecture()))
                                .filter((pv) -> versionCoordinatesComparator.compare(
                                        persistedPkgVersionCoords,
                                        pv.toVersionCoordinates()) < 0)
                                .toList();

                        LOGGER.warn(
                                "imported a package version {} of {} which is older or the same as the existing {}" +
                                        " -- will deactivate {} pkg versions after the imported one and make the" +
                                        " imported one as latest",
                                persistedPkgVersionCoords,
                                persistedPkgVersion.getPkg().getName(),
                                persistedLatestExistingPkgVersionCoords,
                                pkgVersionsToDeactivate.size());

                        for (PkgVersion pkgVersionToDeactivate : pkgVersionsToDeactivate) {
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
