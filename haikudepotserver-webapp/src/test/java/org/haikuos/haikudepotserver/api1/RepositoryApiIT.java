/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

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
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class RepositoryApiIT extends AbstractIntegrationTest {

    @Resource
    RepositoryApi repositoryApi;

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
    public void searchPkgsTest() {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

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
        Assertions.assertThat(result.informationalUrl).isEqualTo("http://example1.haikuos.org/");
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
        request.informationalUrl = "http://zink.haikuos.org";

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
        request.informationalUrl = "http://zink.haikuos.org";

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

}
