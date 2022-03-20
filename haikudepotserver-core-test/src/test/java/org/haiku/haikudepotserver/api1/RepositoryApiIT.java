/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haiku.haikudepotserver.api1.model.repository.CreateRepositoryRequest;
import org.haiku.haikudepotserver.api1.model.repository.CreateRepositorySourceMirrorRequest;
import org.haiku.haikudepotserver.api1.model.repository.CreateRepositorySourceRequest;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositoriesRequest;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositoriesResult;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositoryRequest;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositoryResult;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositorySourceMirrorRequest;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositorySourceMirrorResult;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositorySourceRequest;
import org.haiku.haikudepotserver.api1.model.repository.GetRepositorySourceResult;
import org.haiku.haikudepotserver.api1.model.repository.RemoveRepositorySourceMirrorRequest;
import org.haiku.haikudepotserver.api1.model.repository.SearchRepositoriesRequest;
import org.haiku.haikudepotserver.api1.model.repository.SearchRepositoriesResult;
import org.haiku.haikudepotserver.api1.model.repository.UpdateRepositoryRequest;
import org.haiku.haikudepotserver.api1.model.repository.UpdateRepositorySourceMirrorRequest;
import org.haiku.haikudepotserver.api1.model.repository.UpdateRepositorySourceRequest;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    public void testUpdateRepository() {
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
    public void testUpdateRepository_password() {
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
        repositorySource.setIdentifier("http://example.com/zigzag");
        repositorySource.setRepository(Repository.getByCode(context, "testrepo"));
        context.commitChanges();
    }

    @Test
    public void getRepositoryTest() {
        integrationTestSupportService.createStandardTestData();

        GetRepositoryRequest request = new GetRepositoryRequest();
        request.code = "testrepo";

        // ------------------------------------
        GetRepositoryResult result = repositoryApi.getRepository(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.code).isEqualTo("testrepo");
        Assertions.assertThat(result.informationUrl).isEqualTo("http://example1.haiku.org/");
        Assertions.assertThat(result.repositorySources.size()).isEqualTo(2);
            // ^ one for x86_64 and one for x86_gcc2

        List<GetRepositoryResult.RepositorySource> repositorySourcesSorted = result.repositorySources.stream()
                .sorted(Comparator.comparing(rs1 -> rs1.code))
                .collect(Collectors.toList());

        GetRepositoryResult.RepositorySource repositorySource0 = repositorySourcesSorted.get(0);
        Assertions.assertThat(repositorySource0.code).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(repositorySource0.active).isTrue();
        Assertions.assertThat(repositorySource0.url).startsWith("file:///");
        Assertions.assertThat(repositorySource0.architectureCode).isEqualTo("x86_64");

        GetRepositoryResult.RepositorySource repositorySource1 = repositorySourcesSorted.get(1);
        Assertions.assertThat(repositorySource1.code).isEqualTo("testreposrc_xyz_x86_gcc2");
        Assertions.assertThat(repositorySource1.active).isTrue();
//        Assertions.assertThat(repositorySource1.url).startsWith("file:///");
        Assertions.assertThat(repositorySource1.architectureCode).isEqualTo("x86_gcc2");
    }

    @Test
    public void testCreateRepository_ok() {
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = "differentrepo";
        request.name = "Different Repo";
        request.informationUrl = "http://zink.haiku.org";

        // ------------------------------------
        repositoryApi.createRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Repository repositoryAfter = Repository.getByCode(context,"differentrepo");
        Assertions.assertThat(repositoryAfter.getActive()).isTrue();
        Assertions.assertThat(repositoryAfter.getCode()).isEqualTo("differentrepo");
        Assertions.assertThat(repositoryAfter.getName()).isEqualTo("Different Repo");
        Assertions.assertThat(repositoryAfter.getInformationUrl()).isEqualTo("http://zink.haiku.org");
    }

    @Test
    public void testCreateRepository_codeNotUnique() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = data.repository.getCode();
        request.informationUrl = "http://zink.haiku.org";

        try {
            // ------------------------------------
            repositoryApi.createRepository(request);
            // ------------------------------------

            org.junit.jupiter.api.Assertions.fail("the repository should not have been able to be created against an already existing repository code");
        }
        catch(ValidationException ve) {
            Assertions.assertThat(ve.getValidationFailures().size()).isEqualTo(1);
            Assertions.assertThat(ve.getValidationFailures().get(0).getMessage()).isEqualTo("unique");
            Assertions.assertThat(ve.getValidationFailures().get(0).getProperty()).isEqualTo(Repository.CODE.getName());
        }
    }

    @Test
    public void testGetRepositorySource() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetRepositorySourceRequest request = new GetRepositorySourceRequest();
        request.code = data.pkg1Version1x86_64.getRepositorySource().getCode();

        // ------------------------------------
        GetRepositorySourceResult result = repositoryApi.getRepositorySource(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.code).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(result.repositoryCode).isEqualTo("testrepo");
        Assertions.assertThat(result.identifier).isEqualTo("http://www.example.com/test/identifier/url");
        Assertions.assertThat(result.repositorySourceMirrors.size()).isEqualTo(2);
        Assertions.assertThat(result.extraIdentifiers).containsExactly("example:haiku:identifier");
        Assertions.assertThat(result.architectureCode).isEqualTo("x86_64");

        GetRepositorySourceResult.RepositorySourceMirror mirror0 = result.repositorySourceMirrors.get(0);
        GetRepositorySourceResult.RepositorySourceMirror mirror1 = result.repositorySourceMirrors.get(1);

        Assertions.assertThat(mirror0.code).isEqualTo("testreposrc_xyz_m_pri");
        Assertions.assertThat(mirror0.baseUrl).isEqualTo("file:///tmp/repository");
        Assertions.assertThat(mirror1.code).isEqualTo("testreposrc_xyz_m_notpri");
        Assertions.assertThat(mirror1.baseUrl).isEqualTo("file://not-found/on-disk");

    }

    @Test
    public void testUpdateRepositorySource() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceRequest request = new UpdateRepositorySourceRequest();
        request.code = "testreposrc_xyz";
        request.active = false;
        request.extraIdentifiers = List.of("birds:of:a:feather");
        request.filter = List.of(
                UpdateRepositorySourceRequest.Filter.EXTRA_IDENTIFIERS,
                UpdateRepositorySourceRequest.Filter.ACTIVE);

        // ------------------------------------
        repositoryApi.updateRepositorySource(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySource repositorySourceAfter = RepositorySource.getByCode(context, "testreposrc_xyz");
            // this url was set before and is retained after the update.
            Assertions.assertThat(repositorySourceAfter.getIdentifier()).isEqualTo("http://www.example.com/test/identifier/url");
            Assertions.assertThat(repositorySourceAfter.getActive()).isFalse();
            Assertions.assertThat(repositorySourceAfter.getExtraIdentifiers()).contains("birds:of:a:feather");
        }

    }

    @Test
    public void testCreateRepositorySource() {
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
            RepositorySource repositorySource = RepositorySource.getByCode(context, "testreposrcxx_xyz");
            Assertions.assertThat(repositorySource.getActive()).isTrue();
            Assertions.assertThat(repositorySource.getIdentifier()).isNull();
            Assertions.assertThat(repositorySource.getRepository().getCode()).isEqualTo("testrepo");
            Assertions.assertThat(repositorySource.getCode()).isEqualTo("testreposrcxx_xyz");
        }

    }

    @Test
    public void testCreateRepositorySourceMirror() {
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
    public void testUpdateRepositorySourceMirror() {
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
    public void testGetRepositorySourceMirror() {
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

    @Test
    public void testRemoveRepositorySourceMirror() {
        integrationTestSupportService.createStandardTestData();

        RemoveRepositorySourceMirrorRequest request = new RemoveRepositorySourceMirrorRequest();
        request.code = "testreposrc_xyz_m_notpri";

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_notpri").isPresent()).isTrue();
        }

        // ------------------------------------
        repositoryApi.removeRepositorySourceMirror(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_notpri").isPresent()).isFalse();
        }
    }

}
