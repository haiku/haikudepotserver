/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api2.model.CreateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoriesResult;
import org.haiku.haikudepotserver.api2.model.GetRepositoryRepositorySource;
import org.haiku.haikudepotserver.api2.model.GetRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositoryResult;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceMirrorResult;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceRepositorySourceMirror;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetRepositorySourceResult;
import org.haiku.haikudepotserver.api2.model.RemoveRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesResult;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositoryRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorFilter;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceMirrorRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateRepositorySourceRequestEnvelope;
import org.haiku.haikudepotserver.api2.support.ValidationException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ContextConfiguration(classes = TestConfig.class)
public class RepositoryApiServiceIT extends AbstractIntegrationTest {

    @Resource
    private RepositoryApiService repositoryApiService;

    @Test
    public void testGetRepositories() {
        integrationTestSupportService.createStandardTestData();

        GetRepositoriesRequestEnvelope request = new GetRepositoriesRequestEnvelope();

        // ------------------------------------
        GetRepositoriesResult result = repositoryApiService.getRepositories(request);
        // ------------------------------------

        Assertions.assertThat(result.getRepositories().size()).isEqualTo(1);
        Assertions.assertThat(result.getRepositories().get(0).getCode()).isEqualTo("testrepo");
    }

    @Test
    public void testUpdateRepository() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositoryRequestEnvelope request = new UpdateRepositoryRequestEnvelope()
                .active(false)
                .code(data.repository.getCode())
                .filter(List.of(UpdateRepositoryFilter.ACTIVE));

        // ------------------------------------
        repositoryApiService.updateRepository(request);
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

        UpdateRepositoryRequestEnvelope request = new UpdateRepositoryRequestEnvelope()
                .active(false)
                .code("testrepo")
                .passwordClear("Quatsch")
                .filter(List.of(UpdateRepositoryFilter.PASSWORD));

        // ------------------------------------
        repositoryApiService.updateRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        Repository repository = ObjectSelect
                .query(Repository.class)
                .where(Repository.CODE.eq(data.repository.getCode()))
                .selectOne(context);

        Assertions.assertThat(repository.getPasswordHash()).matches("^[A-Za-z0-9]+$");
    }

    private void assertFoundRepository(SearchRepositoriesResult result) {
        Assertions.assertThat(result.getTotal()).isEqualTo(1);
        Assertions.assertThat(result.getItems().size()).isEqualTo(1);
        Assertions.assertThat(result.getItems().get(0).getCode()).isEqualTo("testrepo");
        Assertions.assertThat(result.getItems().get(0).getName()).isEqualTo("Test Repository");
    }

    @Test
    public void searchRepositoriesTest() {
        integrationTestSupportService.createStandardTestData();

        SearchRepositoriesRequestEnvelope request = new SearchRepositoriesRequestEnvelope()
                .expression("test")
                .expressionType(SearchRepositoriesRequestEnvelope.ExpressionTypeEnum.CONTAINS)
                .limit(2)
                .offset(0);

        // ------------------------------------
        SearchRepositoriesResult result = repositoryApiService.searchRepositories(request);
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

        GetRepositoryRequestEnvelope request = new GetRepositoryRequestEnvelope()
                .code("testrepo");

        // ------------------------------------
        GetRepositoryResult result = repositoryApiService.getRepository(request);
        // ------------------------------------

        Assertions.assertThat(result.getActive()).isTrue();
        Assertions.assertThat(result.getCode()).isEqualTo("testrepo");
        Assertions.assertThat(result.getInformationUrl()).isEqualTo("http://example1.haiku.org/");
        Assertions.assertThat(result.getRepositorySources().size()).isEqualTo(2);
            // ^ one for x86_64 and one for x86_gcc2

        List<GetRepositoryRepositorySource> repositorySourcesSorted = result.getRepositorySources().stream()
                .sorted(Comparator.comparing(GetRepositoryRepositorySource::getCode))
                .collect(Collectors.toList());

        GetRepositoryRepositorySource repositorySource0 = repositorySourcesSorted.get(0);
        Assertions.assertThat(repositorySource0.getCode()).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(repositorySource0.getActive()).isTrue();
        Assertions.assertThat(repositorySource0.getUrl()).startsWith("file:///");
        Assertions.assertThat(repositorySource0.getArchitectureCode()).isEqualTo("x86_64");

        GetRepositoryRepositorySource repositorySource1 = repositorySourcesSorted.get(1);
        Assertions.assertThat(repositorySource1.getCode()).isEqualTo("testreposrc_xyz_x86_gcc2");
        Assertions.assertThat(repositorySource1.getActive()).isTrue();
//        Assertions.assertThat(repositorySource1.url).startsWith("file:///");
        Assertions.assertThat(repositorySource1.getArchitectureCode()).isEqualTo("x86_gcc2");
    }

    @Test
    public void testCreateRepository_ok() {
        setAuthenticatedUserToRoot();

        CreateRepositoryRequestEnvelope request = new CreateRepositoryRequestEnvelope()
                .code("differentrepo")
                .name("Different Repo")
                .informationUrl("http://zink.haiku.org");

        // ------------------------------------
        repositoryApiService.createRepository(request);
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

        CreateRepositoryRequestEnvelope request = new CreateRepositoryRequestEnvelope()
                .code(data.repository.getCode())
                .informationUrl("http://zink.haiku.org");

        // ------------------------------------
        ValidationException ve = org.junit.jupiter.api.Assertions.assertThrows(
                ValidationException.class,
                () -> repositoryApiService.createRepository(request));
        // ------------------------------------

        Assertions.assertThat(ve.getValidationFailures().size()).isEqualTo(1);
        Assertions.assertThat(ve.getValidationFailures().get(0).getMessage()).isEqualTo("unique");
        Assertions.assertThat(ve.getValidationFailures().get(0).getProperty()).isEqualTo(Repository.CODE.getName());
    }

    @Test
    public void testGetRepositorySource() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetRepositorySourceRequestEnvelope request = new GetRepositorySourceRequestEnvelope()
                .code(data.pkg1Version1x86_64.getRepositorySource().getCode());

        // ------------------------------------
        GetRepositorySourceResult result = repositoryApiService.getRepositorySource(request);
        // ------------------------------------

        Assertions.assertThat(result.getActive()).isTrue();
        Assertions.assertThat(result.getCode()).isEqualTo("testreposrc_xyz");
        Assertions.assertThat(result.getRepositoryCode()).isEqualTo("testrepo");
        Assertions.assertThat(result.getIdentifier()).isEqualTo("http://www.example.com/test/identifier/url");
        Assertions.assertThat(result.getRepositorySourceMirrors().size()).isEqualTo(2);
        Assertions.assertThat(result.getExtraIdentifiers()).containsExactly("example:haiku:identifier");
        Assertions.assertThat(result.getArchitectureCode()).isEqualTo("x86_64");

        GetRepositorySourceRepositorySourceMirror mirror0 = result.getRepositorySourceMirrors().get(0);
        GetRepositorySourceRepositorySourceMirror mirror1 = result.getRepositorySourceMirrors().get(1);

        Assertions.assertThat(mirror0.getCode()).isEqualTo("testreposrc_xyz_m_pri");
        Assertions.assertThat(mirror0.getBaseUrl()).isEqualTo("file:///tmp/repository");
        Assertions.assertThat(mirror1.getCode()).isEqualTo("testreposrc_xyz_m_notpri");
        Assertions.assertThat(mirror1.getBaseUrl()).isEqualTo("file://not-found/on-disk");
    }

    @Test
    public void testUpdateRepositorySource() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceRequestEnvelope request = new UpdateRepositorySourceRequestEnvelope()
                .code("testreposrc_xyz")
                .active(false)
                .extraIdentifiers(List.of("birds:of:a:feather"))
                .filter(List.of(
                        UpdateRepositorySourceFilter.ACTIVE,
                        UpdateRepositorySourceFilter.EXTRA_IDENTIFIERS
                ));

        // ------------------------------------
        repositoryApiService.updateRepositorySource(request);
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

        CreateRepositorySourceRequestEnvelope request = new CreateRepositorySourceRequestEnvelope()
                .code("testreposrcxx_xyz")
                .repositoryCode("testrepo");

        // ------------------------------------
        repositoryApiService.createRepositorySource(request);
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

        CreateRepositorySourceMirrorRequestEnvelope request = new CreateRepositorySourceMirrorRequestEnvelope()
                .baseUrl("http://testtest.haiku-os.org")
                .countryCode("DE")
                .description("Landkarte")
                .repositorySourceCode("testreposrc_xyz");

        // ------------------------------------
        String code = repositoryApiService.createRepositorySourceMirror(request).getCode();
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror.getByCode(context, code);
            Assertions.assertThat(mirror.getActive()).isTrue();
            Assertions.assertThat(mirror.getBaseUrl()).isEqualTo("http://testtest.haiku-os.org");
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Landkarte");
        }

    }

    @Test
    public void testUpdateRepositorySourceMirror() {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceMirrorRequestEnvelope request = new UpdateRepositorySourceMirrorRequestEnvelope()
                .code("testreposrc_xyz_m_notpri")
                .isPrimary(true)
                .baseUrl("http://www.example.com/changed")
                .description("Cheramoia")
                .countryCode("DE")
                .filter(List.of(
                                UpdateRepositorySourceMirrorFilter.DESCRIPTION,
                                UpdateRepositorySourceMirrorFilter.IS_PRIMARY,
                                UpdateRepositorySourceMirrorFilter.BASE_URL,
                                UpdateRepositorySourceMirrorFilter.COUNTRY
                        ));

        // ------------------------------------
        repositoryApiService.updateRepositorySourceMirror(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror
                    .getByCode(context, "testreposrc_xyz_m_notpri");
            Assertions.assertThat(mirror.getActive()).isTrue();
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Cheramoia");
            Assertions.assertThat(mirror.getBaseUrl()).isEqualTo("http://www.example.com/changed");
            Assertions.assertThat(mirror.getDescription()).isEqualTo("Cheramoia");
            Assertions.assertThat(mirror.getIsPrimary()).isEqualTo(true);
        }

        {
            ObjectContext context = serverRuntime.newContext();
            RepositorySourceMirror mirror = RepositorySourceMirror
                    .getByCode(context, "testreposrc_xyz_m_pri");
            Assertions.assertThat(mirror.getIsPrimary()).isEqualTo(false);
        }
    }

    @Test
    public void testGetRepositorySourceMirror() {
        integrationTestSupportService.createStandardTestData();

        GetRepositorySourceMirrorRequestEnvelope request = new GetRepositorySourceMirrorRequestEnvelope()
                .code("testreposrc_xyz_m_notpri");

        // ------------------------------------
        GetRepositorySourceMirrorResult result = repositoryApiService.getRepositorySourceMirror(request);
        // ------------------------------------

        Assertions.assertThat(result.getCode()).isEqualTo("testreposrc_xyz_m_notpri");
        Assertions.assertThat(result.getBaseUrl()).isEqualTo("file://not-found/on-disk");
        Assertions.assertThat(result.getCountryCode()).isEqualTo("ZA");
        // ....
    }

    @Test
    public void testRemoveRepositorySourceMirror() {
        integrationTestSupportService.createStandardTestData();

        RemoveRepositorySourceMirrorRequestEnvelope request = new RemoveRepositorySourceMirrorRequestEnvelope()
                .code("testreposrc_xyz_m_notpri");

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_notpri").isPresent()).isTrue();
        }

        // ------------------------------------
        repositoryApiService.removeRepositorySourceMirror(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Assertions.assertThat(RepositorySourceMirror
                    .tryGetByCode(context, "testreposrc_xyz_m_notpri").isPresent()).isFalse();
        }
    }

}
