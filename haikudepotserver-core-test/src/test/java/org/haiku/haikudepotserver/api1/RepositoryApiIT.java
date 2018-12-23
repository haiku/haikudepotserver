/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.collect.ImmutableList;
import junit.framework.Assert;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haiku.haikudepotserver.api1.model.repository.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class RepositoryApiIT extends AbstractIntegrationTest {

    @Resource
    private RepositoryApi repositoryApi;

    @Test
    public void testGetRepositories() {
        integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        GetRepositoriesResult result = repositoryApi.getRepositories(new GetRepositoriesRequest());
        // ------------------------------------

        Assertions.assertThat(result.repositories.size()).isEqualTo(1);
        Assertions.assertThat(result.repositories.get(0).code).isEqualTo("testrepo");

    }

    @Test
    public void testUpdateRepository() throws ObjectNotFoundException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.active = false;
        request.code = data.repository.getCode();
        request.filter = Collections.singletonList(UpdateRepositoryRequest.Filter.ACTIVE);

        // ------------------------------------
        repositoryApi.updateRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Repository repository = ObjectSelect
                .query(Repository.class)
                .where(Repository.CODE.eq(data.repository.getCode()))
                .selectOne(context);

        Assertions.assertThat(repository.getActive()).isFalse();
    }

    @Test
    public void testUpdateRepository_password() throws ObjectNotFoundException {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        Assertions.assertThat(data.repository.getPasswordHash()).isNull();
        setAuthenticatedUserToRoot();

        UpdateRepositoryRequest request = new UpdateRepositoryRequest();
        request.code = "testrepo";
        request.active = false;
        request.passwordClear = "Quatsch";
        request.filter = Collections.singletonList(UpdateRepositoryRequest.Filter.PASSWORD);

        // ------------------------------------
        repositoryApi.updateRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Repository repository = ObjectSelect
                .query(Repository.class)
                .where(Repository.CODE.eq(data.repository.getCode()))
                .selectOne(context);

        Assertions.assertThat(repository.getPasswordHash()).matches("^[A-Za-z0-9]+$");
    }

    private void assertFoundRepository(SearchRepositoriesResult result) {
        Assertions.assertThat(result.total).isEqualTo(1);
        Assertions.assertThat(result.items.size()).isEqualTo(1);
        Assertions.assertThat(result.items.get(0).code).isEqualTo("testrepo");
        Assertions.assertThat(result.items.get(0).name).isEqualTo("Test Repository");
    }

    @Test
    public void searchRepositoriesTest() {
        integrationTestSupportService.createStandardTestData();

        SearchRepositoriesRequest request = new SearchRepositoriesRequest();
        request.expression = "test";
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchRepositoriesResult result = repositoryApi.searchRepositories(request);
        // ------------------------------------

        assertFoundRepository(result);
    }

    public void setupSourceBasedUrlTest() {
        ObjectContext context = serverRuntime.newContext();

        integrationTestSupportService.createStandardTestData();

        RepositorySource repositorySource = context.newObject(RepositorySource.class);
        repositorySource.setCode("zigzag_x86_64");
        repositorySource.setUrl("http://example.com/zigzag");
        repositorySource.setRepository(Repository.tryGetByCode(context, "testrepo").get());
        context.commitChanges();
    }

    @Test
    public void searchRepositoriesTest_sourceBaseUrlHit() {
        setupSourceBasedUrlTest();

        SearchRepositoriesRequest request = new SearchRepositoriesRequest();
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.repositorySourceSearchUrls = Collections.singletonList("https://example.com/zigzag");
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchRepositoriesResult result = repositoryApi.searchRepositories(request);
        // ------------------------------------

        assertFoundRepository(result);
    }

    @Test
    public void searchRepositoriesTest_sourceBaseUrlMiss() {
        setupSourceBasedUrlTest();

        SearchRepositoriesRequest request = new SearchRepositoriesRequest();
        request.expressionType = SearchPkgsRequest.ExpressionType.CONTAINS;
        request.repositorySourceSearchUrls = Collections.singletonList("http://www.nowhere.org/notfound");
        request.limit = 2;
        request.offset = 0;

        // ------------------------------------
        SearchRepositoriesResult result = repositoryApi.searchRepositories(request);
        // ------------------------------------

        Assertions.assertThat(result.items.size()).isEqualTo(0);
        Assertions.assertThat(result.total).isEqualTo(0);
    }

    @Test
    public void getRepositoryTest() throws Exception {
        integrationTestSupportService.createStandardTestData();

        GetRepositoryRequest request = new GetRepositoryRequest();
        request.code = "testrepo";

        // ------------------------------------
        GetRepositoryResult result = repositoryApi.getRepository(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.code).isEqualTo("testrepo");
        Assertions.assertThat(result.informationUrl).isEqualTo("http://example1.haiku.org/");
        Assertions.assertThat(result.repositorySources.size()).isEqualTo(1);

        GetRepositoryResult.RepositorySource repositorySource = result.repositorySources.get(0);
        Assertions.assertThat(repositorySource.code).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(repositorySource.active).isTrue();
        Assertions.assertThat(repositorySource.url).startsWith("file:///");
    }

    @Test
    public void testCreateRepository_ok() throws Exception {
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = "differentrepo";
        request.name = "Different Repo";
        request.informationUrl = "http://zink.haiku.org";

        // ------------------------------------
        repositoryApi.createRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryAfterOptional = Repository.tryGetByCode(context,"differentrepo");
        Assertions.assertThat(repositoryAfterOptional.get().getActive()).isTrue();
        Assertions.assertThat(repositoryAfterOptional.get().getCode()).isEqualTo("differentrepo");
        Assertions.assertThat(repositoryAfterOptional.get().getName()).isEqualTo("Different Repo");
        Assertions.assertThat(repositoryAfterOptional.get().getInformationUrl()).isEqualTo("http://zink.haiku.org");
    }

    @Test
    public void testCreateRepository_codeNotUnique() throws Exception {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = data.repository.getCode();
        request.informationUrl = "http://zink.haiku.org";

        try {
            // ------------------------------------
            repositoryApi.createRepository(request);
            // ------------------------------------

            Assert.fail("the repository should not have been able to be created against an already existing repository code");
        }
        catch(ValidationException ve) {
            Assertions.assertThat(ve.getValidationFailures().size()).isEqualTo(1);
            Assertions.assertThat(ve.getValidationFailures().get(0).getMessage()).isEqualTo("unique");
            Assertions.assertThat(ve.getValidationFailures().get(0).getProperty()).isEqualTo(Repository.CODE.getName());
        }
    }

    @Test
    public void testGetRepositorySource() throws Exception {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetRepositorySourceRequest request = new GetRepositorySourceRequest();
        request.code = data.pkg1Version1x86_64.getRepositorySource().getCode();

        // ------------------------------------
        GetRepositorySourceResult result = repositoryApi.getRepositorySource(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.code).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(result.repositoryCode).isEqualTo("testrepo");
        Assertions.assertThat(result.url).isEqualTo("http://www.example.com/test/identifier/url");
        Assertions.assertThat(result.repositorySourceMirrors.size()).isEqualTo(2);

        GetRepositorySourceResult.RepositorySourceMirror mirror0 = result.repositorySourceMirrors.get(0);
        GetRepositorySourceResult.RepositorySourceMirror mirror1 = result.repositorySourceMirrors.get(1);

        Assertions.assertThat(mirror0.code).isEqualTo("testreposrc_xyz_m_pri");
        Assertions.assertThat(mirror0.baseUrl).isEqualTo("file:///tmp/repository");
        Assertions.assertThat(mirror1.code).isEqualTo("testreposrc_xyz_m_notpri");
        Assertions.assertThat(mirror1.baseUrl).isEqualTo("file://not-found/on-disk");

    }

    @Test
    public void testUpdateRepositorySource() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceRequest request = new UpdateRepositorySourceRequest();
        request.code = "testreposrc_xyz";
        request.active = false;
        request.filter = Collections.singletonList(
                UpdateRepositorySourceRequest.Filter.ACTIVE);

        // ------------------------------------
        repositoryApi.updateRepositorySource(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySource repositorySourceAfter = RepositorySource.tryGetByCode(context, "testreposrc_xyz").get();
            // this url was set before and is retained after the update.
            Assertions.assertThat(repositorySourceAfter.getUrl()).isEqualTo("http://www.example.com/test/identifier/url");
            Assertions.assertThat(repositorySourceAfter.getActive()).isFalse();
        }

    }

    @Test
    public void testCreateRepositorySource() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositorySourceRequest request = new CreateRepositorySourceRequest();
        request.code = "testreposrcxx_xyz";
        request.repositoryCode = "testrepo";

        // ------------------------------------
        repositoryApi.createRepositorySource(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<RepositorySource> repositorySourceOptional = RepositorySource.tryGetByCode(context, "testreposrcxx_xyz");
            Assertions.assertThat(repositorySourceOptional.get().getActive()).isTrue();
            Assertions.assertThat(repositorySourceOptional.get().getUrl()).isNull();
            Assertions.assertThat(repositorySourceOptional.get().getRepository().getCode()).isEqualTo("testrepo");
            Assertions.assertThat(repositorySourceOptional.get().getCode()).isEqualTo("testreposrcxx_xyz");
        }

    }

    @Test
    public void testCreateRepositorySourceMirror() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositorySourceMirrorRequest request = new CreateRepositorySourceMirrorRequest();
        request.baseUrl = "http://testtest.haiku-os.org";
        request.countryCode = "DE";
        request.description = "Landkarte";
        request.repositorySourceCode = "testreposrc_xyz";

        // ------------------------------------
        String code = repositoryApi.createRepositorySourceMirror(request).code;
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror.tryGetByCode(context, code).get();
            Assertions.assertThat(mirror.getActive()).isTrue();
            Assertions.assertThat(mirror.getBaseUrl()).isEqualTo("http://testtest.haiku-os.org");
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Landkarte");
        }

    }

    @Test
    public void testUpdateRepositorySourceMirror() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceMirrorRequest request = new UpdateRepositorySourceMirrorRequest();
        request.code = "testreposrc_xyz_m_notpri";
        request.isPrimary = true;
        request.baseUrl = "http://www.example.com/changed";
        request.description = "Cheramoia";
        request.countryCode = "DE";
        request.filter = ImmutableList.of(
                UpdateRepositorySourceMirrorRequest.Filter.DESCRIPTION,
                UpdateRepositorySourceMirrorRequest.Filter.IS_PRIMARY,
                UpdateRepositorySourceMirrorRequest.Filter.BASE_URL,
                UpdateRepositorySourceMirrorRequest.Filter.COUNTRY);

        // ------------------------------------
        repositoryApi.updateRepositorySourceMirror(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_notpri").get();
            Assertions.assertThat(mirror.getActive()).isTrue();
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Cheramoia");
            Assertions.assertThat(mirror.getBaseUrl()).isEqualTo("http://www.example.com/changed");
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Cheramoia");
            Assertions.assertThat(mirror.getIsPrimary()).isEqualTo(true);
        }

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_pri").get();
            Assertions.assertThat(mirror.getIsPrimary()).isEqualTo(false);
        }
    }

    @Test
    public void testGetRepositorySourceMirror() throws Exception {
        integrationTestSupportService.createStandardTestData();

        GetRepositorySourceMirrorRequest request = new GetRepositorySourceMirrorRequest();
        request.code = "testreposrc_xyz_m_notpri";

        // ------------------------------------
        GetRepositorySourceMirrorResult result = repositoryApi.getRepositorySourceMirror(request);
        // ------------------------------------

        Assertions.assertThat(result.code).isEqualTo("testreposrc_xyz_m_notpri");
        Assertions.assertThat(result.baseUrl).isEqualTo("file://not-found/on-disk");
        Assertions.assertThat(result.countryCode).isEqualTo("ZA");
        // ....
    }

}