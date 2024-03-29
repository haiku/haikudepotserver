/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.ObjectSelect;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgUrlType;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.PkgVersionCopyright;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLicense;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.dataobjects.PkgVersionUrl;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.haiku.haikudepotserver.job.Jobs;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>This test will load in a fake repository HPKR file and will then check to see that it imported correctly.</p>
 */

@ContextConfiguration(classes = TestConfig.class)
public class RepositoryHpkrIngressServiceIT extends AbstractIntegrationTest {

    private final static long DELAY_PROCESSSUBMITTEDTESTJOB = 60 * 1000; // 60s

    @Resource
    private JobService jobService;

    @Resource
    private PkgService pkgService;

    private void verifyPackage(
            ObjectContext context,
            String name) {
        Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, name);
        Assertions.assertThat(pkgOptional.isPresent()).isTrue();
        Assertions.assertThat(pkgOptional.get().getActive()).isTrue();
    }

    @Test
    public void testImportThenCheck() throws Exception {

        File temporaryDir;
        File temporaryRepoFile = null;
        File temporaryRepoInfoFile = null;

        try {
            temporaryDir = Files.createTempDir();
            temporaryRepoFile = new File(temporaryDir, "repo");
            temporaryRepoInfoFile = new File(temporaryDir, "repo.info");

            // get the test hpkr data and copy it into a temporary file that can be used as a source
            // for a repository.

            Files.write(getResourceData("sample-repo.info"), temporaryRepoInfoFile);
            Files.write(getResourceData("sample-repo.hpkr"), temporaryRepoFile);

            // first setup a fake repository to import that points at the local test HPKR file.

            {
                ObjectContext context = serverRuntime.newContext();

                Repository repository = context.newObject(Repository.class);
                repository.setCode("test");
                repository.setName("Test Repository");

                RepositorySource repositorySource = context.newObject(RepositorySource.class);
                repositorySource.setCode("testsrc_xyz");
                repositorySource.setIdentifier("file://" + temporaryDir.getAbsolutePath());
                repository.addToManyTarget(Repository.REPOSITORY_SOURCES.getName(), repositorySource, true);

                RepositorySourceMirror repositorySourceMirror = context.newObject(RepositorySourceMirror.class);
                repositorySourceMirror.setBaseUrl("file://" + temporaryDir.getAbsolutePath());
                repositorySourceMirror.setIsPrimary(true);
                repositorySourceMirror.setCode("testsrc_xyz_mirror");
                repositorySourceMirror.setCountry(Country.getByCode(context, Country.CODE_NZ));
                repositorySource.addToManyTarget(RepositorySource.REPOSITORY_SOURCE_MIRRORS.getName(),
                        repositorySourceMirror, true);

                context.commitChanges();
            }

            // setup another repository that is not related to the import test to check some stuff...

            {
                ObjectContext context = serverRuntime.newContext();

                Repository repository = context.newObject(Repository.class);
                repository.setCode("test2");
                repository.setName("Test 2");

                RepositorySource repositorySource = context.newObject(RepositorySource.class);
                repositorySource.setCode("testsrc2_xyz");
                repositorySource.setLastImportTimestamp(new java.sql.Timestamp(12345L)); // just after epoc second.
                repository.addToManyTarget(Repository.REPOSITORY_SOURCES.getName(), repositorySource, true);

                RepositorySourceMirror repositorySourceMirror = context.newObject(RepositorySourceMirror.class);
                repositorySourceMirror.setBaseUrl("file://does-not-exist/path");
                repositorySourceMirror.setIsPrimary(true);
                repositorySourceMirror.setCode("testsrc2_xyz_mirror");
                repositorySourceMirror.setCountry(Country.getByCode(context, Country.CODE_NZ));
                repositorySource.addToManyTarget(RepositorySource.REPOSITORY_SOURCE_MIRRORS.getName(),
                        repositorySourceMirror, true);

                context.commitChanges();
            }

            // add a package version from this repository that is known not to be in that example and then
            // latterly check that the package version is no longer active.

            {
                ObjectContext context = serverRuntime.newContext();
                Pkg pkg = integrationTestSupportService.createPkg(context, "taranaki");
                pkgService.ensurePkgProminence(context, pkg, Repository.tryGetByCode(context, "test").get());
                pkgService.ensurePkgProminence(context, pkg, Repository.tryGetByCode(context, "test2").get());

                // this one should get deactivated
                {
                    PkgVersion pkgVersion = context.newObject(PkgVersion.class);
                    pkgVersion.setPkg(pkg);
                    pkgVersion.setMajor("1");
                    pkgVersion.setMinor("2");
                    pkgVersion.setArchitecture(Architecture.tryGetByCode(context, "x86_64").get());
                    pkgVersion.setIsLatest(true);
                    pkgVersion.setRepositorySource(RepositorySource.tryGetByCode(context, "testsrc_xyz").get());
                }

                // this one should remain
                {
                    PkgVersion pkgVersion = context.newObject(PkgVersion.class);
                    pkgVersion.setPkg(pkg);
                    pkgVersion.setMajor("1");
                    pkgVersion.setMinor("3");
                    pkgVersion.setArchitecture(Architecture.tryGetByCode(context, "x86_64").get());
                    pkgVersion.setIsLatest(true);
                    pkgVersion.setRepositorySource(RepositorySource.tryGetByCode(context, "testsrc2_xyz").get());
                }

                context.commitChanges();
            }

            // add an inactive package version from this repository that is known to be in the repository.  This
            // package should be activated and re-used.

            ObjectId originalFfmpegPkgOid;

            {
                ObjectContext context = serverRuntime.newContext();

                Pkg pkg = integrationTestSupportService.createPkg(context, "ffmpeg");
                pkgService.ensurePkgProminence(context, pkg, Repository.tryGetByCode(context, "test").get());
                pkgService.ensurePkgProminence(context, pkg, Repository.tryGetByCode(context, "test2").get());

                PkgVersion pkgVersion = context.newObject(PkgVersion.class);
                pkgVersion.setPkg(pkg);
                pkgVersion.setMajor("3");
                pkgVersion.setMinor("3");
                pkgVersion.setMicro("2");
                pkgVersion.setRevision(1);
                pkgVersion.setArchitecture(Architecture.tryGetByCode(context, "x86_64").get());
                pkgVersion.setIsLatest(true);
                pkgVersion.setActive(false); // to be sure!
                pkgVersion.setRepositorySource(RepositorySource.tryGetByCode(context, "testsrc_xyz").get());

                PkgVersionUrl pkgVersionUrl = context.newObject(PkgVersionUrl.class);
                pkgVersionUrl.setPkgUrlType(PkgUrlType.getByCode(context, org.haiku.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase()).get());
                pkgVersionUrl.setUrl("http://noop");
                pkgVersion.addToManyTarget(PkgVersion.PKG_VERSION_URLS.getName(), pkgVersionUrl, true);

                PkgVersionCopyright pkgVersionCopyright = context.newObject(PkgVersionCopyright.class);
                pkgVersionCopyright.setBody("Norfolk pine");
                pkgVersion.addToManyTarget(PkgVersion.PKG_VERSION_COPYRIGHTS.getName(), pkgVersionCopyright, true);

                PkgVersionLicense pkgVersionLicense = context.newObject(PkgVersionLicense.class);
                pkgVersionLicense.setBody("Punga");
                pkgVersion.addToManyTarget(PkgVersion.PKG_VERSION_LICENSES.getName(), pkgVersionLicense, true);

                context.commitChanges();

                originalFfmpegPkgOid = pkgVersion.getObjectId();
            }

            // do the import.

            String guid = jobService.submit(
                    new RepositoryHpkrIngressJobSpecification("test"),
                    JobSnapshot.COALESCE_STATUSES_NONE);

            // wait for it to finish.

            {
                long startMs = System.currentTimeMillis();

                while(
                        Jobs.isQueuedOrStarted(jobService.tryGetJob(guid).get())
                                && (System.currentTimeMillis() - startMs) < DELAY_PROCESSSUBMITTEDTESTJOB) {
                    Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                }

                if(Jobs.isQueuedOrStarted(jobService.tryGetJob(guid).get())) {
                    throw new IllegalStateException("test processing of the sample repo has taken > "
                            + DELAY_PROCESSSUBMITTEDTESTJOB + "ms");
                }
            }

            // check that the sample url is loaded into the repository source.

            {
                ObjectContext context = serverRuntime.newContext();
                RepositorySource repositorySource = RepositorySource.tryGetByCode(context, "testsrc_xyz").get();
                Assertions.assertThat(repositorySource.getIdentifier()).isEqualTo("f0c086e5-e096-429c-b38d-57beabd764e9");
                // ^^ as defined in the repo info file.
                Assertions.assertThat(repositorySource.getArchitecture().getCode()).isEqualTo("x86_gcc2");
                // ^^ as defined in the repo info file.
            }

            // now pull out some known packages and make sure they are imported correctly.
            // TODO - this is a fairly simplistic test; do some more checks.

            {
                ObjectContext context = serverRuntime.newContext();

                verifyPackage(context,"apr");
                verifyPackage(context,"schroedinger");

                // this one is not in the import and so should be inactive afterwards.

                List<PkgVersion> pkgVersions = ObjectSelect
                        .query(PkgVersion.class)
                        .where(PkgVersion.PKG.dot(Pkg.NAME).eq("taranaki"))
                        .select(context);

                Assertions.assertThat(pkgVersions.size()).isEqualTo(2);

                for(PkgVersion pkgVersion : pkgVersions) {
                    boolean isTestRepository = pkgVersion.getRepositorySource().getRepository().getCode().equals("test");
                    Assertions.assertThat(pkgVersion.getActive()).isEqualTo(!isTestRepository);
                }

                // check that the ffmpeg package was re-used and populated; as an example.

                {
                    PkgVersion pkgVersion = PkgVersion.get(context, originalFfmpegPkgOid);
                    Assertions.assertThat(pkgVersion.getActive()).isTrue();
                    Assertions.assertThat(pkgVersion.getIsLatest()).isTrue();
                    Assertions.assertThat(PkgVersion.findForPkg(
                            context,
                            pkgVersion.getPkg(),
                            RepositorySource.tryGetByCode(context, "testsrc_xyz").get(),
                            true).size())
                            .isEqualTo(1); // include inactive

                    PkgVersionLocalization localization = pkgVersion.getPkgVersionLocalization(NaturalLanguage.getByCode(context, NaturalLanguageCoordinates.LANGUAGE_CODE_ENGLISH)).get();
                    Assertions.assertThat(localization.getDescription().get()).startsWith("FFmpeg is a complete, cro");
                    Assertions.assertThat(localization.getSummary().get()).startsWith("Audio and video rec");

                    // the former rubbish copyright is removed
                    List<String> copyrights = pkgVersion.getCopyrights();
                    Assertions.assertThat(copyrights.size()).isEqualTo(2);
                    Assertions.assertThat(ImmutableSet.copyOf(copyrights)).containsOnly("2000-2003 Fabrice Bellard", "2003-2017 the FFmpeg developers");

                    // the former rubbish license is removed
                    List<String> licenses = pkgVersion.getLicenses();
                    Assertions.assertThat(licenses.size()).isEqualTo(2);
                    Assertions.assertThat(ImmutableSet.copyOf(licenses)).containsOnly("GNU LGPL v2.1", "GNU GPL v2");

                    Optional<PkgVersionUrl> pkgVersionUrlOptional = pkgVersion.getPkgVersionUrlForType(PkgUrlType.getByCode(
                            context,
                            org.haiku.pkg.model.PkgUrlType.HOMEPAGE.name().toLowerCase()).get());

                    Assertions.assertThat(pkgVersionUrlOptional.isPresent()).isTrue();
                    Assertions.assertThat(pkgVersionUrlOptional.get().getUrl()).isEqualTo("https://ffmpeg.org/");
                }

            }
        } finally {
            if (null != temporaryRepoFile) {
                if (!temporaryRepoFile.delete()) {
                    LOGGER.warn("unable to delete the temporary 'repo' file");
                }
            }

            if (null != temporaryRepoInfoFile) {
                if (!temporaryRepoInfoFile.delete()) {
                    LOGGER.warn("unable to delete the temporary 'repo.info' file");
                }
            }
        }
    }

}
