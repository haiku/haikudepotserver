/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogRequest;
import org.haiku.haikudepotserver.api1.model.pkg.GetPkgChangelogResult;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterRequest;
import org.haiku.haikudepotserver.api1.model.pkg.IncrementViewCounterResult;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;

@ContextConfiguration(classes = TestConfig.class)
public class PkgApiIT extends AbstractIntegrationTest {

    @Resource
    private PkgApi pkgApi;

    @Test
    public void testGetPkgChangelog() {
        integrationTestSupportService.createStandardTestData();

        GetPkgChangelogRequest request = new GetPkgChangelogRequest();
        request.pkgName = "pkg1";

        // ------------------------------------
        GetPkgChangelogResult result = pkgApi.getPkgChangelog(request);
        // ------------------------------------

        Assertions.assertThat(result.content).isEqualTo("Stadt\nKarlsruhe");
    }

    @Test
    public void testIncrementViewCounter() {
        integrationTestSupportService.createStandardTestData();

        IncrementViewCounterRequest request = new IncrementViewCounterRequest();
        request.major = "1";
        request.micro = "2";
        request.revision = 3;
        request.name = "pkg1";
        request.architectureCode = "x86_64";
        request.repositoryCode = "testrepo";

        // ------------------------------------
        IncrementViewCounterResult result = pkgApi.incrementViewCounter(request);
        // ------------------------------------

        Assertions.assertThat(result).isNotNull();

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg1 = Pkg.getByName(context, "pkg1");
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrc_xyz");
            Architecture architecture = Architecture.getByCode(context, "x86_64");
            PkgVersion pkgVersion = PkgVersion.tryGetForPkg(context, pkg1, repositorySource, architecture, new VersionCoordinates("1",null,"2",null,3)).get();
            Assertions.assertThat(pkgVersion.getViewCounter()).isEqualTo(1L);
        }

    }

}
