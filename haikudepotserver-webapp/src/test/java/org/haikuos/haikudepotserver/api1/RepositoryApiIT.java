/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
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
        Assertions.assertThat(result.items.get(0).code).isEqualTo("testrepository");
    }

    @Test
    public void getRepositoryTest() throws Exception {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();

        GetRepositoryRequest request = new GetRepositoryRequest();
        request.code = "testrepository";

        // ------------------------------------
        GetRepositoryResult result = repositoryApi.getRepository(request);
        // ------------------------------------

        Assertions.assertThat(result.active).isTrue();
        Assertions.assertThat(result.architectureCode).isEqualTo("x86");
        Assertions.assertThat(result.url).isEqualTo("file:///");
    }

    @Test
    public void testCreateRepository_ok() throws Exception {
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.architectureCode = "x86";
        request.code = "integrationtest";
        request.url = "http://www.somewhere.co.nz";

        // ------------------------------------
        repositoryApi.createRepository(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.getContext();
        Optional<Repository> repositoryAfterOptional = Repository.getByCode(context,"integrationtest");
        Assertions.assertThat(repositoryAfterOptional.get().getActive()).isTrue();
        Assertions.assertThat(repositoryAfterOptional.get().getArchitecture().getCode()).isEqualTo("x86");
        Assertions.assertThat(repositoryAfterOptional.get().getCode()).isEqualTo("integrationtest");
        Assertions.assertThat(repositoryAfterOptional.get().getUrl()).isEqualTo("http://www.somewhere.co.nz");
    }

    @Test
    public void testCreateRepository_codeNotUnique() throws Exception {
        IntegrationTestSupportService.StandardTestData data = integrationTestSupportService.createStandardTestData();
        setAuthenticatedUserToRoot();

        CreateRepositoryRequest request = new CreateRepositoryRequest();
        request.architectureCode = "x86";
        request.code = data.repository.getCode();
        request.url = "http://www.somewhere.co.nz";

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
