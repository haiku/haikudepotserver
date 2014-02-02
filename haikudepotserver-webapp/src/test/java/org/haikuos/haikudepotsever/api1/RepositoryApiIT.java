/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.RepositoryApi;
import org.haikuos.haikudepotserver.api1.model.pkg.SearchPkgsRequest;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesRequest;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesResult;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.haikuos.haikudepotsever.api1.support.IntegrationTestSupportService;
import org.junit.Test;

import javax.annotation.Resource;

public class RepositoryApiIT extends AbstractIntegrationTest {

    @Resource
    IntegrationTestSupportService integrationTestSupportService;

    @Resource
    RepositoryApi repositoryApi;

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

        Assertions.assertThat(result.hasMore).isFalse();
        Assertions.assertThat(result.items.size()).isEqualTo(1);
        Assertions.assertThat(result.items.get(0).code).isEqualTo("testrepository");
    }

}
