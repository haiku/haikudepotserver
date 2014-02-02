/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesRequest;
import org.haikuos.haikudepotserver.api1.model.repository.SearchRepositoriesResult;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.repository.RepositoryService;
import org.haikuos.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class RepositoryApiImpl extends AbstractApiImpl implements RepositoryApi {

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    RepositoryService repositoryService;

    @Override
    public SearchRepositoriesResult searchRepositories(SearchRepositoriesRequest request) {
        Preconditions.checkNotNull(request);

        final ObjectContext context = serverRuntime.getContext();

        RepositorySearchSpecification specification = new RepositorySearchSpecification();
        String exp = request.expression;

        if(null!=exp) {
            exp = Strings.emptyToNull(exp.trim().toLowerCase());
        }

        specification.setExpression(exp);

        if(null!=request.expressionType) {
            specification.setExpressionType(
                    PkgSearchSpecification.ExpressionType.valueOf(request.expressionType.name()));
        }

        specification.setLimit(request.limit+1); // get +1 to see if there are any more.
        specification.setOffset(request.offset);

        SearchRepositoriesResult result = new SearchRepositoriesResult();
        List<Repository> searchedRepositories = repositoryService.search(context,specification);

        // if there are more than we asked for then there must be more available.

        result.hasMore = new Boolean(searchedRepositories.size() > request.limit);

        if(result.hasMore) {
            searchedRepositories = searchedRepositories.subList(0,request.limit);
        }

        result.items = Lists.newArrayList(Iterables.transform(
                searchedRepositories,
                new Function<Repository, SearchRepositoriesResult.Repository>() {
                    @Override
                    public SearchRepositoriesResult.Repository apply(Repository input) {
                        SearchRepositoriesResult.Repository resultRepository = new SearchRepositoriesResult.Repository();
                        resultRepository.active = input.getActive();
                        resultRepository.architectureCode = input.getArchitecture().getCode();
                        resultRepository.code = input.getCode();
                        resultRepository.createTimestamp = input.getCreateTimestamp().getTime();
                        resultRepository.modifyTimestamp = input.getModifyTimestamp().getTime();
                        return resultRepository;
                    }
                }
        ));

        return result;
    }

}
