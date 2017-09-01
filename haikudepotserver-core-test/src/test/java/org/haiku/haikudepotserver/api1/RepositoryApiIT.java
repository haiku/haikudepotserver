/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.collect.ImmutableList;
import junit.framework.Assert;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haiku.haikudepotserver.api1.model.repository.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.api1.support.ValidationException;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
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

        ObjectContext context = serverRuntime.getContext();
        Repository repository = (Repository) context.performQuery(new SelectQuery(
                Repository.class,
                ExpressionFactory.matchExp(Repository.CODE_PROPERTY, data.repository.getCode())
        )).stream().collect(SingleCollector.single());

        Assertions.assertThat(repository.getActive()).isFalse();

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
        ObjectContext context = serverRuntime.getContext();

        integrationTestSupportService.createStandardTestData();

        RepositorySource repositorySource = context.newObject(RepositorySource.class);
        repositorySource.setCode("zigzag_x86_64");
        repositorySource.setUrl("http://example.com/zigzag");
        repositorySource.setRepository(Repository.getByCode(context, "testrepo").get());
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

        ObjectContext context = serverRuntime.getContext();
        Optional<Repository> repositoryAfterOptional = Repository.getByCode(context,"differentrepo");
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
            Assertions.assertThat(ve.getValidationFailures().get(0).getProperty()).isEqualTo(Repository.CODE_PROPERTY);
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
        Assertions.assertThat(result.url).startsWith("file://");

    }

    @Test
    public void testUpdateRepositorySource() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceRequest request = new UpdateRepositorySourceRequest();
        request.code = "testreposrc_xyz";
        request.active = false;
        request.url = "http://test-example2.haiku-os.org";
        request.filter = ImmutableList.of(
                UpdateRepositorySourceRequest.Filter.ACTIVE,
                UpdateRepositorySourceRequest.Filter.URL);

        // ------------------------------------
        repositoryApi.updateRepositorySource(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            RepositorySource repositorySourceAfter = RepositorySource.getByCode(context, "testreposrc_xyz").get();
            Assertions.assertThat(repositorySourceAfter.getUrl()).isEqualTo("http://test-example2.haiku-os.org");
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
        request.url = "http://testtest.haiku-os.org";

        // ------------------------------------
        repositoryApi.createRepositorySource(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<RepositorySource> repositorySourceOptional = RepositorySource.getByCode(context, "testreposrcxx_xyz");
            Assertions.assertThat(repositorySourceOptional.get().getActive()).isTrue();
            Assertions.assertThat(repositorySourceOptional.get().getUrl()).isEqualTo("http://testtest.haiku-os.org");
            Assertions.assertThat(repositorySourceOptional.get().getRepository().getCode()).isEqualTo("testrepo");
            Assertions.assertThat(repositorySourceOptional.get().getCode()).isEqualTo("testreposrcxx_xyz");
        }

    }

}