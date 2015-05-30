/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver;

import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.security.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
    ServerRuntime serverRuntime;

    @Resource
    PkgOrchestrationService pkgOrchestrationService;

    @Resource
    PkgOrchestrationService pkgService;

    @Resource
    protected AuthenticationService authenticationService;

    private ObjectContext objectContext = null;

    public ObjectContext getObjectContext() {
        if(null==objectContext) {
            objectContext = serverRuntime.getContext();
        }

        return objectContext;
    }

    private PkgScreenshot addPkgScreenshot(ObjectContext objectContext, Pkg pkg) {
        try (InputStream inputStream = IntegrationTestSupportService.class.getResourceAsStream("/sample-320x240.png")) {
            return pkgService.storePkgScreenshotImage(inputStream, objectContext, pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading a sample screenshot into a test package",e);
        }
    }

    private void addPngPkgIcon(ObjectContext objectContext, Pkg pkg, int size) {

        try (InputStream inputStream = this.getClass().getResourceAsStream(String.format("/sample-%dx%d.png", size, size))) {
            pkgService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, com.google.common.net.MediaType.PNG.toString()).get(),
                    size,
                    objectContext,
                    pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
    }

    private void addHvifPkgIcon(ObjectContext objectContext, Pkg pkg) {

        try (InputStream inputStream = this.getClass().getResourceAsStream("/sample.hvif")) {

            pkgService.storePkgIconImage(
                    inputStream,
                    MediaType.getByCode(objectContext, MediaType.MEDIATYPE_HAIKUVECTORICONFILE).get(),
                    null,
                    objectContext,
                    pkg);
        }
        catch(Exception e) {
            throw new IllegalStateException("an issue has arisen loading an icon",e);
        }
    }

    private void addPkgIcons(ObjectContext objectContext, Pkg pkg) {
        addPngPkgIcon(objectContext, pkg, 16);
        addPngPkgIcon(objectContext, pkg, 32);
        addHvifPkgIcon(objectContext, pkg);
    }

    public void addDummyLocalization(ObjectContext context, PkgVersion pkgVersion) {
        pkgOrchestrationService.updatePkgVersionLocalization(
                context,
                pkgVersion,
                NaturalLanguage.getEnglish(context),
                "sample title",
                "sample summary",
                "sample description");
    }

    public StandardTestData createStandardTestData() {

        LOGGER.info("will create standard test data");

        String platformTmpDirPath = System.getProperty("java.io.tmpdir");

        if (Strings.isNullOrEmpty(platformTmpDirPath)) {
            throw new IllegalStateException("unable to get the java temporary directory");
        }

        ObjectContext context = getObjectContext();
        StandardTestData result = new StandardTestData();

        Prominence prominence = Prominence.getByOrdering(context, Prominence.ORDERING_LAST).get();

        Architecture x86 = Architecture.getByCode(context, "x86").get();
        Architecture x86_gcc2 = Architecture.getByCode(context, "x86_gcc2").get();
        Architecture any = Architecture.getByCode(context, "any").get();

        result.repository = context.newObject(Repository.class);
        result.repository.setCode("testrepo");
        result.repository.setName("Test Repository");
        result.repository.setInformationUrl("http://example1.haikuos.org/");

        result.repositorySource = context.newObject(RepositorySource.class);
        result.repositorySource.setCode("testreposrc");
        result.repositorySource.setRepository(result.repository);
        result.repositorySource.setUrl("file://" + platformTmpDirPath + "/repository");

        result.pkg1 = context.newObject(Pkg.class);
        result.pkg1.setActive(true);
        result.pkg1.setName("pkg1");
        pkgOrchestrationService.ensurePkgProminence(context, result.pkg1, result.repository, prominence.getOrdering());

        ensureUserRatingAggregate(context, result.pkg1, result.repository, 3.5f, 4);

        {
            PkgPkgCategory pkgPkgCategory = context.newObject(PkgPkgCategory.class);
            result.pkg1.addToManyTarget(Pkg.PKG_PKG_CATEGORIES_PROPERTY, pkgPkgCategory, true);
            pkgPkgCategory.setPkgCategory(PkgCategory.getByCode(context, "graphics").get());
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get());
            pkgLocalization.setTitle("Package 1");
            pkgLocalization.setPkg(result.pkg1);
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_GERMAN).get());
            pkgLocalization.setTitle("Packet 1");
            pkgLocalization.setPkg(result.pkg1);
        }

        {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH).get());
            pkgLocalization.setTitle("Ping 1");
            pkgLocalization.setPkg(result.pkg1);
        }

        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgScreenshot(context,result.pkg1);
        addPkgIcons(context, result.pkg1);

        result.pkg1Version1x86 = context.newObject(PkgVersion.class);
        result.pkg1Version1x86.setActive(Boolean.FALSE);
        result.pkg1Version1x86.setArchitecture(x86);
        result.pkg1Version1x86.setMajor("1");
        result.pkg1Version1x86.setMicro("2");
        result.pkg1Version1x86.setRevision(3);
        result.pkg1Version1x86.setIsLatest(false);
        result.pkg1Version1x86.setPkg(result.pkg1);
        result.pkg1Version1x86.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg1Version1x86);

        result.pkg1Version2x86 = context.newObject(PkgVersion.class);
        result.pkg1Version2x86.setActive(Boolean.TRUE);
        result.pkg1Version2x86.setArchitecture(x86);
        result.pkg1Version2x86.setMajor("1");
        result.pkg1Version2x86.setMicro("2");
        result.pkg1Version2x86.setRevision(4);
        result.pkg1Version2x86.setIsLatest(true);
        result.pkg1Version2x86.setPkg(result.pkg1);
        result.pkg1Version2x86.setRepositorySource(result.repositorySource);

        pkgOrchestrationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86,
                NaturalLanguage.getEnglish(context),
                null,
                "pkg1Version2SummaryEnglish_persimon",
                "pkg1Version2DescriptionEnglish_rockmelon");

        pkgOrchestrationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86,
                NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH).get(),
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
        result.pkg1Version2x86_gcc2.setRepositorySource(result.repositorySource);

        // this is the same as the x86 version so that comparisons with English will happen.

        pkgOrchestrationService.updatePkgVersionLocalization(
                context,
                result.pkg1Version2x86_gcc2,
                NaturalLanguage.getByCode(context, NaturalLanguage.CODE_SPANISH).get(),
                null,
                "pkg1Version2SummarySpanish_apple",
                "pkg1Version2DescriptionSpanish_guava");

        result.pkg2 = context.newObject(Pkg.class);
        result.pkg2.setActive(true);
        result.pkg2.setName("pkg2");
        pkgOrchestrationService.ensurePkgProminence(context, result.pkg2, result.repository, prominence);

        result.pkg2Version1 = context.newObject(PkgVersion.class);
        result.pkg2Version1.setActive(Boolean.TRUE);
        result.pkg2Version1.setArchitecture(x86);
        result.pkg2Version1.setMajor("1");
        result.pkg2Version1.setMinor("1");
        result.pkg2Version1.setMicro("2");
        result.pkg2Version1.setRevision(3);
        result.pkg2Version1.setIsLatest(true);
        result.pkg2Version1.setPkg(result.pkg2);
        result.pkg2Version1.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg2Version1);

        result.pkg3 = context.newObject(Pkg.class);
        result.pkg3.setActive(true);
        result.pkg3.setName("pkg3");
        pkgOrchestrationService.ensurePkgProminence(context, result.pkg3, result.repository, prominence);

        result.pkg3Version1 = context.newObject(PkgVersion.class);
        result.pkg3Version1.setActive(Boolean.TRUE);
        result.pkg3Version1.setArchitecture(x86);
        result.pkg3Version1.setMajor("1");
        result.pkg3Version1.setMinor("1");
        result.pkg3Version1.setMicro("2");
        result.pkg3Version1.setRevision(3);
        result.pkg3Version1.setIsLatest(true);
        result.pkg3Version1.setPkg(result.pkg3);
        result.pkg3Version1.setRepositorySource(result.repositorySource);
        addDummyLocalization(context, result.pkg3Version1);

        result.pkgAny = context.newObject(Pkg.class);
        result.pkgAny.setActive(true);
        result.pkgAny.setName("pkgany");
        pkgOrchestrationService.ensurePkgProminence(context, result.pkgAny, result.repository, prominence);

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

    public void ensureUserRatingAggregate(ObjectContext context, Pkg pkg, Repository repository, Float rating, Integer sampleSize) {
        Optional<PkgUserRatingAggregate> aggregateOptional = pkg.getPkgUserRatingAggregate(repository);
        PkgUserRatingAggregate aggregate;

        if(!aggregateOptional.isPresent()) {
            aggregate = context.newObject(PkgUserRatingAggregate.class);
            pkg.addToManyTarget(Pkg.PKG_USER_RATING_AGGREGATES_PROPERTY, aggregate, true);
            aggregate.setRepository(repository);
        }
        else {
            aggregate = aggregateOptional.get();
        }

        aggregate.setDerivedRating(rating);
        aggregate.setDerivedRatingSampleSize(sampleSize);
    }

    public User createBasicUser(ObjectContext context, String nickname, String password) {
        User user = context.newObject(User.class);
        user.setNickname(nickname);
        user.setPasswordSalt(); // random
        user.setPasswordHash(authenticationService.hashPassword(user, password));
        user.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get());
        context.commitChanges();
        return user;
    }

    /**
     * <p>This will create a known user and a known set of user ratings that can be tested against.
     * This method expected that the standard test data has already been introduced into the
     * environment prior.</p>
     */

    public void createUserRatings() {

        ObjectContext context = serverRuntime.getContext();
        Pkg pkg = Pkg.getByName(context, "pkg3").get();
        Architecture x86 = Architecture.getByCode(context, "x86").get();
        PkgVersion pkgVersion = pkgOrchestrationService.getLatestPkgVersionForPkg(
                context,
                pkg,
                Repository.getByCode(context, "testrepo").get(),
                Collections.singletonList(x86)).get();

        NaturalLanguage english = NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get();

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
                userRating.setUserRatingStability(UserRatingStability.getByCode(context, UserRatingStability.CODE_UNSTABLEBUTUSABLE).get());
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
        public PkgVersion pkg1Version1x86;
        public PkgVersion pkg1Version2x86;
        public PkgVersion pkg1Version2x86_gcc2;

        public Pkg pkg2;
        public PkgVersion pkg2Version1;

        public Pkg pkg3;
        public PkgVersion pkg3Version1;

        public Pkg pkgAny;
        public PkgVersion pkgAnyVersion1;

    }

}
