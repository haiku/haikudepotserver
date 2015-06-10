/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import junit.framework.Assert;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haikuos.haikudepotserver.api1.model.repository.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.api1.support.ValidationException;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.dataobjects.RepositorySource;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
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
        Optional<Repository> repositoryOptional = Repository.getByCode(context, data.repository.getCode());
        Assertions.assertThat(repositoryOptional.isPresent()).isTrue();
        Assertions.assertThat(repositoryOptional.get().getActive()).isFalse();

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

        Assertions.assertThat(result.total).isEqualTo(1);
        Assertions.assertThat(result.items.size()).isEqualTo(1);
        Assertions.assertThat(result.items.get(0).code).isEqualTo("testrepo");
        Assertions.assertThat(result.items.get(0).name).isEqualTo("Test Repository");
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
        Assertions.assertThat(result.informationUrl).isEqualTo("http://example1.haikuos.org/");
        Assertions.assertThat(result.repositorySources.size()).isEqualTo(1);

        GetRepositoryResult.RepositorySource repositorySource = result.repositorySources.get(0);
        Assertions.assertThat(repositorySource.code).isEqualTo("testreposrc");
        Assertions.assertThat(repositorySource.active).isTrue();
        Assertions.assertThat(repositorySource.url).startsWith("file:///");
    }

    @Test
    public void testCreateRepository_ok() throws Exception {
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = "differentrepo";
        request.name = "Different Repo";
        request.informationUrl = "http://zink.haikuos.org";

        // ------------------------------------
        repositoryApi.createRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.getContext();
        Optional<Repository> repositoryAfterOptional = Repository.getByCode(context,"differentrepo");
        Assertions.assertThat(repositoryAfterOptional.get().getActive()).isTrue();
        Assertions.assertThat(repositoryAfterOptional.get().getCode()).isEqualTo("differentrepo");
        Assertions.assertThat(repositoryAfterOptional.get().getName()).isEqualTo("Different Repo");
        Assertions.assertThat(repositoryAfterOptional.get().getInformationUrl()).isEqualTo("http://zink.haikuos.org");
    }

    @Test
    public void testCreateRepository_codeNotUnique() throws Exception {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.code = data.repository.getCode();
        request.informationUrl = "http://zink.haikuos.org";

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
        request.code = data.pkg1Version1x86.getRepositorySource().getCode();

        // ------------------------------------
        GetRepositorySourceResult result = repositoryApi.getRepositorySource(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.code).isEqualTo("testreposrc");
        Assertions.assertThat(result.repositoryCode).isEqualTo("testrepo");
        Assertions.assertThat(result.url).startsWith("file://");

    }

    @Test
    public void testUpdateRepositorySource() throws Exception {
        integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        UpdateRepositorySourceRequest request = new UpdateRepositorySourceRequest();
        request.code = "testreposrc";
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
            RepositorySource repositorySourceAfter = RepositorySource.getByCode(context, "testreposrc").get();
            Assertions.assertThat(repositorySourceAfter.getUrl()).isEqualTo("http://test-example2.haiku-os.org");
            Assertions.assertThat(repositorySourceAfter.getActive()).isFalse();
        }

    }

}
