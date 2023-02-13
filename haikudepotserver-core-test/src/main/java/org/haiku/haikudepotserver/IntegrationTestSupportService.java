/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

/**
 * <p>This class is designed to help out with creating some common test data that can be re-used between tests.</p>
 */

@Service
public class IntegrationTestSupportService {

    protected static Logger LOGGER = LoggerFactory.getLogger(IntegrationTestSupportService.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgService pkgService;

    @Resource
    private PkgLocalizationService pkgLocalizationService;

    @Resource
    private PkgIconService pkgIconService;

    @Resource
    private PkgScreenshotService pkgScreenshotService;

    @Resource
    private UserAuthenticationService userAuthenticationService;

    private ObjectContext objectContext = null;

    private ObjectContext getObjectContext() {
        if(null==objectContext) {
            objectContext = serverRuntime.newContext();
        }

        return objectContext;
    }

    private PkgScreenshot addPkgScreenshot(ObjectContext objectContext, Pkg pkg, String sourceLeafname) {
        try (InputStream inputStream = IntegrationTestSupportService.class.getResourceAsStream(sourceLeafname)) {
            return pkgScreenshotService.storePkgScreenshotImage(inputStream, objectContext, pkg.getPkgSupplement(), null);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading a sample screenshot into a test package",e);
        }
    }

    private void addPngPkgIcon(ObjectContext objectContext, Pkg pkg, int size) {

        try (InputStream inputStream = this.getClass().getResourceAsStream(String.format("/sample-%dx%d.png", size, size))) {
            pkgIconService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, com.google.common.net.MediaType.PNG.toString()),
                    size,
                    objectContext,
                    pkg.getPkgSupplement());
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
    }

    private void addHvifPkgIcon(ObjectContext objectContext, Pkg pkg) {

        try (InputStream inputStream = this.getClass().getResourceAsStream("/sample.hvif")) {

            pkgIconService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, MediaType.MEDIATYPE_HAIKUVECTORICONFILE),
                    null,
                    objectContext,
                    pkg.getPkgSupplement());
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
    }

    private void addPkgBitmapIcons(ObjectContext objectContext, Pkg pkg) {
        addPngPkgIcon(objectContext, pkg, 16);
        addPngPkgIcon(objectContext, pkg, 32);
        addPngPkgIcon(objectContext, pkg, 64);
    }

    private void addDummyLocalization(ObjectContext context, PkgVersion pkgVersion) {
        pkgLocalizationService.updatePkgVersionLocalization(
                context,
                pkgVersion,
                NaturalLanguage.getEnglish(context),
                "sample title " + pkgVersion.getPkg().getName(),
                "sample summary " + pkgVersion.getPkg().getName(),
                "sample description " + pkgVersion.getPkg().getName());
    }

    public Pkg createPkg(ObjectContext context, String name) {
        Pkg pkg = context.newObject(Pkg.class);
        pkg.setActive(true);
        pkg.setName(name);

        PkgSupplement pkgSupplement1 = context.newObject(PkgSupplement.class);
        pkgSupplement1.setBasePkgName(name);
        pkgSupplement1.addToPkgs(pkg);
        pkg.setPkgSupplement(pkgSupplement1);

        return pkg;
    }

    public StandardTestData createStandardTestData() {

        LOGGER.info("will create standard test data");

        String platformTmpDirPath = System.getProperty("java.io.tmpdir");

        if (Strings.isNullOrEmpty(platformTmpDirPath)) {
            throw new IllegalStateException("unable to get the java temporary directory");
        }

        ObjectContext context = getObjectContext();
        context.rollbackChanges();

        StandardTestData result = new StandardTestData();

        Prominence prominence = Prominence.tryGetByOrdering(context, Prominence.ORDERING_LAST).get();

        Architecture x86_64 = Architecture.getByCode(context, "x86_64");
        Architecture x86_gcc2 = Architecture.getByCode(context, "x86_gcc2");
        Architecture any = Architecture.getByCode(context, "any");

        result.repository = context.newObject(Repository.class);
        result.repository.setCode("testrepo");
        result.repository.setName("Test Repository");
        result.repository.setInformationUrl("http://example1.haiku.org/");

        result.repositorySource = context.newObject(RepositorySource.class);
        result.repositorySource.setCode("testreposrc_xyz");
        result.repositorySource.setRepository(result.repository);
        result.repositorySource.setIdentifier("http://www.example.com/test/identifier/url");
        result.repositorySource.setArchitecture(x86_64);

        RepositorySource repositorySourceX86Gcc2 = context.newObject(RepositorySource.class);
        repositorySourceX86Gcc2.setCode("testreposrc_xyz_x86_gcc2");
        repositorySourceX86Gcc2.setRepository(result.repository);
        repositorySourceX86Gcc2.setIdentifier("http://www.example.com/test/identifier/url/x86_gcc2");
        repositorySourceX86Gcc2.setArchitecture(x86_gcc2);

        RepositorySourceExtraIdentifier repositorySourceExtraIdentifier = context.newObject(RepositorySourceExtraIdentifier.class);
        repositorySourceExtraIdentifier.setIdentifier("example:haiku:identifier");
        repositorySourceExtraIdentifier.setRepositorySource(result.repositorySource);

        RepositorySourceMirror primaryMirror = context.newObject(RepositorySourceMirror.class);
        primaryMirror.setCountry(Country.getByCode(context, "NZ"));
        primaryMirror.setIsPrimary(true);
        primaryMirror.setBaseUrl("file://" + new File(platformTmpDirPath, "repository").getAbsolutePath());
        primaryMirror.setRepositorySource(result.repositorySource);
        primaryMirror.setCode("testreposrc_xyz_m_pri");

        RepositorySourceMirror nonPrimaryMirror = context.newObject(RepositorySourceMirror.class);
        nonPrimaryMirror.setCountry(Country.getByCode(context, "ZA"));
        nonPrimaryMirror.setIsPrimary(false);
        nonPrimaryMirror.setBaseUrl("file://not-found/on-disk");
        nonPrimaryMirror.setRepositorySource(result.repositorySource);
        nonPrimaryMirror.setCode("testreposrc_xyz_m_notpri");

        result.pkg1 = createPkg(context, "pkg1");

        pkgService.ensurePkgProminence(context, result.pkg1, result.repository, prominence.getOrdering());
        pkgService.updatePkgChangelog(context, result.pkg1.getPkgSupplement(), "Stadt\r\nKarlsruhe\r\n");

        ensureUserRatingAggregate(context, result.pkg1, result.repository, 3.5f, 4);

        {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            result.pkg1.getPkgSupplement().addToManyTarget(PkgSupplement.PKG_PKG_CATEGORIES.getName(), pkgPkgCategory, true);
            pkgPkgCategory.setPkgCategory(PkgCategory.tryGetByCode(context, "graphics").get());
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH));
            pkgLocalization.setTitle("Package 1");
            pkgLocalization.setPkgSupplement(result.pkg1.getPkgSupplement());
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_GERMAN));
            pkgLocalization.setTitle("Packet 1");
            pkgLocalization.setPkgSupplement(result.pkg1.getPkgSupplement());
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH));
            pkgLocalization.setTitle("Ping 1");
            pkgLocalization.setPkgSupplement(result.pkg1.getPkgSupplement());
        }

        addPkgScreenshot(context,result.pkg1, "/sample-320x240-a.png");
        addPkgScreenshot(context,result.pkg1, "/sample-240x320-b.png");
        addPkgScreenshot(context,result.pkg1, "/sample-320x240-c.png");
        addPkgBitmapIcons(context, result.pkg1);

        result.pkg1Version1x86_64 = context.newObject(PkgVersion.class);
        result.pkg1Version1x86_64.setActive(Boolean.FALSE);
        result.pkg1Version1x86_64.setArchitecture(x86_64);
        result.pkg1Version1x86_64.setMajor("1");
        result.pkg1Version1x86_64.setMicro("2");
        result.pkg1Version1x86_64.setRevision(3);
        result.pkg1Version1x86_64.setIsLatest(false);
        result.pkg1Version1x86_64.setPkg(result.pkg1);
        result.pkg1Version1x86_64.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg1Version1x86_64);

        result.pkg1Version2x86_64 = context.newObject(PkgVersion.class);
        result.pkg1Version2x86_64.setActive(Boolean.TRUE);
        result.pkg1Version2x86_64.setArchitecture(x86_64);
        result.pkg1Version2x86_64.setMajor("1");
        result.pkg1Version2x86_64.setMicro("2");
        result.pkg1Version2x86_64.setRevision(4);
        result.pkg1Version2x86_64.setIsLatest(true);
        result.pkg1Version2x86_64.setPkg(result.pkg1);
        result.pkg1Version2x86_64.setRepositorySource(result.repositorySource);

        pkgLocalizationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86_64,
                NaturalLanguage.getEnglish(context),
                null,
                "pkg1Version2SummaryEnglish_persimon",
                "pkg1Version2DescriptionEnglish_rockmelon");

        pkgLocalizationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86_64,
                NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH),
                null,
                "pkg1Version2SummarySpanish_feijoa",
                "pkg1Version2DescriptionSpanish_mango");

        result.pkg1Version2x86_gcc2 = context.newObject(PkgVersion.class);
        result.pkg1Version2x86_gcc2.setActive(Boolean.TRUE);
        result.pkg1Version2x86_gcc2.setArchitecture(x86_gcc2);
        result.pkg1Version2x86_gcc2.setMajor("1");
        result.pkg1Version2x86_gcc2.setMicro("2");
        result.pkg1Version2x86_gcc2.setRevision(4);
        result.pkg1Version2x86_gcc2.setIsLatest(true);
        result.pkg1Version2x86_gcc2.setPkg(result.pkg1);
        result.pkg1Version2x86_gcc2.setRepositorySource(repositorySourceX86Gcc2);

        // this is the same as the x86 version so that comparisons with English will happen.

        pkgLocalizationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86_gcc2,
                NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH),
                null,
                "pkg1Version2SummarySpanish_apple",
                "pkg1Version2DescriptionSpanish_guava");

        result.pkg2 = createPkg(context, "pkg2");

        pkgService.ensurePkgProminence(context, result.pkg2, result.repository, prominence);

        result.pkg2Version1 = context.newObject(PkgVersion.class);
        result.pkg2Version1.setActive(Boolean.TRUE);
        result.pkg2Version1.setArchitecture(x86_64);
        result.pkg2Version1.setMajor("1");
        result.pkg2Version1.setMinor("1");
        result.pkg2Version1.setMicro("2");
        result.pkg2Version1.setRevision(3);
        result.pkg2Version1.setIsLatest(true);
        result.pkg2Version1.setPkg(result.pkg2);
        result.pkg2Version1.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg2Version1);

        result.pkg3 = createPkg(context, "pkg3");

        pkgService.ensurePkgProminence(context, result.pkg3, result.repository, prominence);

        result.pkg3Version1 = context.newObject(PkgVersion.class);
        result.pkg3Version1.setActive(Boolean.TRUE);
        result.pkg3Version1.setArchitecture(x86_64);
        result.pkg3Version1.setMajor("1");
        result.pkg3Version1.setMinor("1");
        result.pkg3Version1.setMicro("2");
        result.pkg3Version1.setRevision(3);
        result.pkg3Version1.setIsLatest(true);
        result.pkg3Version1.setPkg(result.pkg3);
        result.pkg3Version1.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg3Version1);

        result.pkgAny = createPkg(context, "pkgany");

        pkgService.ensurePkgProminence(context, result.pkgAny, result.repository, prominence);

        result.pkgAnyVersion1 = context.newObject(PkgVersion.class);
        result.pkgAnyVersion1.setActive(Boolean.TRUE);
        result.pkgAnyVersion1.setArchitecture(any);
        result.pkgAnyVersion1.setMajor("123");
        result.pkgAnyVersion1.setMicro("123");
        result.pkgAnyVersion1.setRevision(3);
        result.pkgAnyVersion1.setIsLatest(true);
        result.pkgAnyVersion1.setPkg(result.pkgAny);
        result.pkgAnyVersion1.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkgAnyVersion1);

        context.commitChanges();

        LOGGER.info("did create standard test data");

        return result;
    }

    private void ensureUserRatingAggregate(ObjectContext context, Pkg pkg, Repository repository, Float rating, Integer sampleSize) {
        Optional<PkgUserRatingAggregate> aggregateOptional = pkg.getPkgUserRatingAggregate(repository);
        PkgUserRatingAggregate aggregate;

        if(aggregateOptional.isEmpty()) {
            aggregate = context.newObject(PkgUserRatingAggregate.class);
            pkg.addToManyTarget(Pkg.PKG_USER_RATING_AGGREGATES.getName(), aggregate, true);
            aggregate.setRepository(repository);
        }
        else {
            aggregate = aggregateOptional.get();
        }

        aggregate.setDerivedRating(rating);
        aggregate.setDerivedRatingSampleSize(sampleSize);
    }

    public User createBasicUser(ObjectContext context, String nickname, String passwordClear) {
        User user = context.newObject(User.class);
        user.setNickname(nickname);
        userAuthenticationService.setPassword(user, passwordClear);
        user.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH));
        context.commitChanges();
        return user;
    }

    public void agreeToUserUsageConditions(ObjectContext context, User user) {
        UserUsageConditionsAgreement agreement = context.newObject(UserUsageConditionsAgreement.class);
        agreement.setUser(user);
        agreement.setTimestampAgreed();
        agreement.setUserUsageConditions(UserUsageConditions.getByCode(context, "UUC2021V01"));
        context.commitChanges();
    }

    /**
     * <p>This will create a known user and a known set of user ratings that can be tested against.
     * This method expected that the standard test data has already been introduced into the
     * environment prior.</p>
     */

    public void createUserRatings() {

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = Pkg.getByName(context, "pkg3");
        Architecture x86_64 = Architecture.getByCode(context, "x86_64");
        PkgVersion pkgVersion = pkgService.getLatestPkgVersionForPkg(
                context,
                pkg,
                RepositorySource.getByCode(context, "testreposrc_xyz"),
                Collections.singletonList(x86_64)).get();

        NaturalLanguage english = NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH);

        {
            User user = createBasicUser(context, "urtest1", "password");

            {
                UserRating userRating = context.newObject(UserRating.class);
                userRating.setRating((short) 5);
                userRating.setUser(user);
                userRating.setNaturalLanguage(english);
                userRating.setPkgVersion(pkgVersion);
                userRating.setComment("Southern hemisphere winter");
                userRating.setCode("ABCDEF"); // known code that can be used for reference later
            }
        }

        {
            User user = createBasicUser(context, "urtest2", "password");

            {
                UserRating userRating = context.newObject(UserRating.class);
                userRating.setRating((short) 3);
                userRating.setUser(user);
                userRating.setNaturalLanguage(english);
                userRating.setPkgVersion(pkgVersion);
                userRating.setComment("Winter banana apples");
                userRating.setCode("GHIJKL"); // known code that can be used for reference later
                userRating.setUserRatingStability(UserRatingStability.tryGetByCode(context, UserRatingStability.CODE_UNSTABLEBUTUSABLE).get());
            }
        }

        {
            User user = createBasicUser(context, "urtest3", "password");

            {
                UserRating userRating = context.newObject(UserRating.class);
                userRating.setRating((short) 1);
                userRating.setUser(user);
                userRating.setActive(false);
                userRating.setNaturalLanguage(english);
                userRating.setPkgVersion(pkgVersion);
                userRating.setComment("Kingston black apples");
                userRating.setCode("MNOPQR"); // known code that can be used for reference later
            }
        }

        context.commitChanges();
    }

    /**
     * <p>This class is a container that carries some basic test-case data.</p>
     */

    public static class StandardTestData {

        public Repository repository;
        public RepositorySource repositorySource;

        public Pkg pkg1;
        public PkgVersion pkg1Version1x86_64;
        public PkgVersion pkg1Version2x86_64;
        public PkgVersion pkg1Version2x86_gcc2;

        public Pkg pkg2;
        public PkgVersion pkg2Version1;

        public Pkg pkg3;
        public PkgVersion pkg3Version1;

        public Pkg pkgAny;
        public PkgVersion pkgAnyVersion1;

    }

}
